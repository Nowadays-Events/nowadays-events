package com.nowadays.events

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nowadays.events.data.local.EventDatabase
import com.nowadays.events.data.local.MIGRATION_7_8
import com.nowadays.events.data.local.MIGRATION_8_9
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TemporalDatabaseMigrationInstrumentedTest {
    private val databaseName = "temporal-migration-v7.db"
    private lateinit var context: Context

    @Before fun prepare() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After fun cleanup() {
        context.deleteDatabase(databaseName)
    }

    @Test fun representativeVersion7DatabaseMigratesToExplicitTemporalModel() {
        val file = context.getDatabasePath(databaseName)
        file.parentFile?.mkdirs()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL(V7_EVENTS)
            database.execSQL(V7_ATTENDANCE)
            database.execSQL("CREATE INDEX IF NOT EXISTS index_events_starts_at ON events(starts_at)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_events_ends_at ON events(ends_at)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_events_latitude_longitude ON events(latitude, longitude)")
            database.execSQL("PRAGMA user_version = 7")
            insertV7(database, "single", 1_000L, 2_000L, 1, null)
            insertV7(database, "continuous", 1_000L, 86_402_000L, 1, null)
            insertV7(database, "recurring", 1_000L, 200_000_000L, 8, 50_000L)
        }

        val room = Room.databaseBuilder(context, EventDatabase::class.java, databaseName)
            .addMigrations(MIGRATION_7_8, MIGRATION_8_9)
            .allowMainThreadQueries()
            .build()
        val database = room.openHelper.writableDatabase

        assertEquals(9, database.version)
        database.query(
            "SELECT id,schedule_type,occurrence_starts FROM events ORDER BY id",
        ).use { cursor ->
            val rows = buildList {
                while (cursor.moveToNext()) add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
            }
            assertEquals(
                listOf(
                    Triple("continuous", "CONTINUOUS", ""),
                    Triple("recurring", "RECURRING", "50000"),
                    Triple("single", "SINGLE", ""),
                ),
                rows,
            )
        }
        room.close()
    }

    private fun insertV7(
        database: android.database.sqlite.SQLiteDatabase,
        id: String,
        start: Long,
        end: Long,
        occurrenceCount: Int,
        nextOccurrence: Long?,
    ) {
        database.execSQL(
            """
            INSERT INTO events (
                id,title,short_description,full_description,category,starts_at,ends_at,venue_name,
                address,latitude,longitude,source_url,source_urls,image_url,organizer,is_free,
                price_cents,price_type,currency,updated_at,origin,going_count,maybe_count,is_fictional,
                status,occurrence_count,next_occurrence_at,time_precision,original_time_text
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """.trimIndent(),
            arrayOf(
                id, id, "description", null, "CULTURE", start, end, "Lieu", "Adresse",
                43.89, -0.50, "https://example.invalid/$id", "https://example.invalid/$id",
                null, null, 1, null, "FREE", "EUR", start, "AUTOMATIC", 0, 0, 0,
                "ACTIVE", occurrenceCount, nextOccurrence, "EXACT", null,
            ),
        )
    }

    private companion object {
        const val V7_EVENTS = """
            CREATE TABLE IF NOT EXISTS events (
                id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, short_description TEXT NOT NULL,
                full_description TEXT, category TEXT NOT NULL, starts_at INTEGER NOT NULL,
                ends_at INTEGER NOT NULL, venue_name TEXT NOT NULL, address TEXT NOT NULL,
                latitude REAL NOT NULL, longitude REAL NOT NULL, source_url TEXT NOT NULL,
                source_urls TEXT NOT NULL DEFAULT '', image_url TEXT, organizer TEXT,
                is_free INTEGER NOT NULL, price_cents INTEGER, price_type TEXT NOT NULL DEFAULT 'UNKNOWN',
                currency TEXT NOT NULL, updated_at INTEGER NOT NULL, origin TEXT NOT NULL,
                going_count INTEGER NOT NULL, maybe_count INTEGER NOT NULL, is_fictional INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'ACTIVE', occurrence_count INTEGER NOT NULL DEFAULT 1,
                next_occurrence_at INTEGER, time_precision TEXT NOT NULL DEFAULT 'EXACT', original_time_text TEXT
            )
        """
        const val V7_ATTENDANCE = """
            CREATE TABLE IF NOT EXISTS event_attendance (
                event_id TEXT NOT NULL PRIMARY KEY, response TEXT NOT NULL, updated_at INTEGER NOT NULL,
                FOREIGN KEY(event_id) REFERENCES events(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """
    }
}
