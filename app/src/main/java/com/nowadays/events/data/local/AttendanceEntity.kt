package com.nowadays.events.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "event_attendance",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AttendanceEntity(
    @PrimaryKey @ColumnInfo(name = "event_id") val eventId: String,
    val response: String,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMillis: Long,
)

