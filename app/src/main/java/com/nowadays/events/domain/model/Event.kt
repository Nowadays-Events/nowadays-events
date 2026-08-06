package com.nowadays.events.domain.model

import java.time.Instant

data class Event(
    val id: String,
    val title: String,
    val shortDescription: String,
    val fullDescription: String?,
    val category: EventCategory,
    val startsAt: Instant,
    val endsAt: Instant,
    val venueName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val sourceUrl: String,
    val imageUrl: String?,
    val organizer: String?,
    val price: EventPrice,
    val updatedAt: Instant,
    val origin: DataOrigin,
    val goingCount: Int = 0,
    val maybeCount: Int = 0,
    val isFictional: Boolean = false,
    val status: EventStatus = EventStatus.ACTIVE,
)

enum class EventCategory { CULTURE, MUSIC, SPORT, FOOD, FAMILY, COMMUNITY, TECHNOLOGY }
enum class DataOrigin { DEMO, MANUAL, AUTOMATIC }
enum class AttendanceResponse { NONE, GOING, MAYBE }
enum class EventStatus { ACTIVE, POSTPONED, CANCELLED }

sealed interface EventPrice {
    data object Free : EventPrice
    data class Paid(val amountCents: Int?, val currency: String = "EUR") : EventPrice
}
