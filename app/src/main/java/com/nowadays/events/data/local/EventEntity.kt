package com.nowadays.events.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "events",
    indices = [Index("starts_at"), Index("ends_at"), Index(value = ["latitude", "longitude"])],
)
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "short_description") val shortDescription: String,
    @ColumnInfo(name = "full_description") val fullDescription: String?,
    val category: String,
    @ColumnInfo(name = "starts_at") val startsAtEpochMillis: Long,
    @ColumnInfo(name = "ends_at") val endsAtEpochMillis: Long,
    @ColumnInfo(name = "venue_name") val venueName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "source_url") val sourceUrl: String,
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    val organizer: String?,
    @ColumnInfo(name = "is_free") val isFree: Boolean,
    @ColumnInfo(name = "price_cents") val priceCents: Int?,
    @ColumnInfo(name = "price_type", defaultValue = "UNKNOWN") val priceType: String = "UNKNOWN",
    val currency: String,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMillis: Long,
    val origin: String,
    @ColumnInfo(name = "going_count") val goingCount: Int,
    @ColumnInfo(name = "maybe_count") val maybeCount: Int,
    @ColumnInfo(name = "is_fictional") val isFictional: Boolean,
    @ColumnInfo(defaultValue = "ACTIVE") val status: String = "ACTIVE",
    @ColumnInfo(name = "occurrence_count", defaultValue = "1") val occurrenceCount: Int = 1,
    @ColumnInfo(name = "next_occurrence_at") val nextOccurrenceAtEpochMillis: Long? = null,
)
