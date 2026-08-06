package com.nowadays.events.map

import com.nowadays.events.domain.model.Event
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal data class MarkerPriority(val alpha: Int, val radius: Float)

internal object MapMarkerPolicy {
    private const val LONG_RUNNING_SECONDS = 36 * 60 * 60
    private const val RECURRING_SECONDS = 14 * 24 * 60 * 60

    fun isLongRunning(event: Event): Boolean =
        event.endsAt.epochSecond - event.startsAt.epochSecond >= LONG_RUNNING_SECONDS

    fun isRecurring(event: Event): Boolean =
        event.occurrenceCount > 1 || event.endsAt.epochSecond - event.startsAt.epochSecond >= RECURRING_SECONDS

    fun displayDate(
        event: Event,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): LocalDate {
        val start = event.startsAt.atZone(zoneId).toLocalDate()
        val end = event.endsAt.atZone(zoneId).toLocalDate()
        val next = event.nextOccurrenceAt?.atZone(zoneId)?.toLocalDate()
        return when {
            isRecurring(event) && next != null && next >= today -> next
            isRecurring(event) && today in start..end -> today
            else -> start
        }
    }

    fun priority(
        event: Event,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): MarkerPriority {
        val days = ChronoUnit.DAYS.between(
            today,
            event.startsAt.atZone(zoneId).toLocalDate(),
        ).coerceAtLeast(0)
        return when {
            days <= 1 -> MarkerPriority(alpha = 255, radius = 26f)
            days <= 3 -> MarkerPriority(alpha = 210, radius = 23f)
            days <= 6 -> MarkerPriority(alpha = 145, radius = 20f)
            else -> MarkerPriority(alpha = 70, radius = 17f)
        }
    }
}
