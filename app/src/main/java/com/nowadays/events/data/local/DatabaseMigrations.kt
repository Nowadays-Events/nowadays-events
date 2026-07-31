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
