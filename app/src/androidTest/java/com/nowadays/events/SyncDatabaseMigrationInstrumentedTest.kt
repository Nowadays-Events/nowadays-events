package com.nowadays.events

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nowadays.events.data.local.EventDatabase
import com.nowadays.events.data.local.MIGRATION_8_9
import com.nowadays.events.data.local.SyncStateEntity
import org.junit.*
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class SyncDatabaseMigrationInstrumentedTest {
    private val name = "sync-migration-v8.db"
    private lateinit var context: Context
    @Before fun prepare() { context = ApplicationProvider.getApplicationContext(); context.deleteDatabase(name) }
    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun representativeVersion8DatabaseMigratesWithoutLosingEventsOrAttendance() {
        val file = context.getDatabasePath(name).also { it.parentFile?.mkdirs() }
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(V8_EVENTS); db.execSQL(V8_ATTENDANCE)
            db.execSQL("CREATE INDEX index_events_starts_at ON events(starts_at)")
            db.execSQL("CREATE INDEX index_events_ends_at ON events(ends_at)")
            db.execSQL("CREATE INDEX index_events_latitude_longitude ON events(latitude, longitude)")
            repeat(169) { insertEvent(db, if (it == 168) "MANUAL" else "AUTOMATIC", "event-$it") }
            db.execSQL("INSERT INTO event_attendance(event_id,response,updated_at) VALUES('event-0','GOING',1000)")
            db.execSQL("PRAGMA user_version = 8")
        }
        val room = Room.databaseBuilder(context, EventDatabase::class.java, name).addMigrations(MIGRATION_8_9).allowMainThreadQueries().build()
        val db = room.openHelper.writableDatabase
        assertEquals(9, db.version)
        db.query("SELECT COUNT(*), SUM(CASE WHEN origin='MANUAL' THEN 1 ELSE 0 END), SUM(missed_snapshots), SUM(is_remote_visible) FROM events").use {
            it.moveToFirst(); assertEquals(169, it.getInt(0)); assertEquals(1, it.getInt(1)); assertEquals(0, it.getInt(2)); assertEquals(169, it.getInt(3))
        }
        db.query("SELECT response FROM event_attendance WHERE event_id='event-0'").use { assertTrue(it.moveToFirst()); assertEquals("GOING", it.getString(0)) }
        db.query("SELECT COUNT(*) FROM sync_state").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }

        val dao = room.eventDao()
        val all = runBlocking { dao.getAllIncludingHidden() }
        val absent = all.first { it.id == "event-0" }
        val remoteWithoutAbsent = all.filter { it.origin == "AUTOMATIC" && it.id != absent.id }
        val success = SyncStateEntity(status = "SUCCESS", lastAttemptAt = 2_000, lastSuccessAt = 2_000, receivedCount = remoteWithoutAbsent.size, lastSuccessfulCount = remoteWithoutAbsent.size, technicalReason = null)
        runBlocking {
            dao.reconcileRemoteSnapshot(remoteWithoutAbsent, success, 2)
            dao.reconcileRemoteSnapshot(remoteWithoutAbsent, success.copy(lastAttemptAt = 3_000, lastSuccessAt = 3_000), 2)
        }
        db.query("SELECT missed_snapshots,is_remote_visible FROM events WHERE id='event-0'").use { it.moveToFirst(); assertEquals(2, it.getInt(0)); assertEquals(0, it.getInt(1)) }
        db.query("SELECT COUNT(*) FROM events WHERE origin='MANUAL'").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.query("SELECT response FROM event_attendance WHERE event_id='event-0'").use { assertTrue(it.moveToFirst()); assertEquals("GOING", it.getString(0)) }
        runBlocking { dao.reconcileRemoteSnapshot(remoteWithoutAbsent + absent.copy(title = "Restauré"), success.copy(lastAttemptAt = 4_000, lastSuccessAt = 4_000), 2) }
        db.query("SELECT title,missed_snapshots,is_remote_visible FROM events WHERE id='event-0'").use { it.moveToFirst(); assertEquals("Restauré", it.getString(0)); assertEquals(0, it.getInt(1)); assertEquals(1, it.getInt(2)) }
        db.query("SELECT response FROM event_attendance WHERE event_id='event-0'").use { assertTrue(it.moveToFirst()); assertEquals("GOING", it.getString(0)) }
        room.close()
    }

    private fun insertEvent(db: android.database.sqlite.SQLiteDatabase, origin: String, id: String) = db.execSQL(
        """INSERT INTO events(id,title,short_description,full_description,category,starts_at,ends_at,venue_name,address,latitude,longitude,source_url,source_urls,image_url,organizer,is_free,price_cents,price_type,currency,updated_at,origin,going_count,maybe_count,is_fictional,status,occurrence_count,next_occurrence_at,schedule_type,occurrence_starts,time_precision,original_time_text) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
        arrayOf(id,id,"Description",null,"CULTURE",1000,2000,"Lieu","Adresse",43.89,-0.50,"https://example.invalid/$id","https://example.invalid/$id",null,null,1,null,"FREE","EUR",1000,origin,0,0,0,"ACTIVE",1,null,"SINGLE","","EXACT",null),
    )

    private companion object {
        const val V8_EVENTS = """CREATE TABLE events(id TEXT NOT NULL PRIMARY KEY,title TEXT NOT NULL,short_description TEXT NOT NULL,full_description TEXT,category TEXT NOT NULL,starts_at INTEGER NOT NULL,ends_at INTEGER NOT NULL,venue_name TEXT NOT NULL,address TEXT NOT NULL,latitude REAL NOT NULL,longitude REAL NOT NULL,source_url TEXT NOT NULL,source_urls TEXT NOT NULL DEFAULT '',image_url TEXT,organizer TEXT,is_free INTEGER NOT NULL,price_cents INTEGER,price_type TEXT NOT NULL DEFAULT 'UNKNOWN',currency TEXT NOT NULL,updated_at INTEGER NOT NULL,origin TEXT NOT NULL,going_count INTEGER NOT NULL,maybe_count INTEGER NOT NULL,is_fictional INTEGER NOT NULL,status TEXT NOT NULL DEFAULT 'ACTIVE',occurrence_count INTEGER NOT NULL DEFAULT 1,next_occurrence_at INTEGER,schedule_type TEXT NOT NULL DEFAULT 'SINGLE',occurrence_starts TEXT NOT NULL DEFAULT '',time_precision TEXT NOT NULL DEFAULT 'EXACT',original_time_text TEXT)"""
        const val V8_ATTENDANCE = """CREATE TABLE event_attendance(event_id TEXT NOT NULL PRIMARY KEY,response TEXT NOT NULL,updated_at INTEGER NOT NULL,FOREIGN KEY(event_id) REFERENCES events(id) ON UPDATE NO ACTION ON DELETE CASCADE)"""
    }
}
