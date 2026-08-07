package com.nowadays.events.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS event_attendance (
                event_id TEXT NOT NULL PRIMARY KEY,
                response TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY(event_id) REFERENCES events(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE events ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE events ADD COLUMN price_type TEXT NOT NULL DEFAULT 'UNKNOWN'")
        db.execSQL("UPDATE events SET price_type = CASE WHEN is_free = 1 THEN 'FREE' ELSE 'PAID' END")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE events ADD COLUMN occurrence_count INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE events ADD COLUMN next_occurrence_at INTEGER")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE events ADD COLUMN source_urls TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE events SET source_urls = source_url WHERE source_urls = ''")
    }
}
