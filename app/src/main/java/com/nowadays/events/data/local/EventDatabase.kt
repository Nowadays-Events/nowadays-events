package com.nowadays.events.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [EventEntity::class, AttendanceEntity::class], version = 5, exportSchema = false)
abstract class EventDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
}
