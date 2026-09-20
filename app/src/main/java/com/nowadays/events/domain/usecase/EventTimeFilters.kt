package com.nowadays.events.domain.usecase

import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.EventScheduleType
import com.nowadays.events.domain.model.TimeFilter
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class TimeWindow(val start: Instant, val endExclusive: Instant?)

class EventTimeFilters @Inject constructor(private val clock: Clock) {
    fun window(filter: TimeFilter, zoneId: ZoneId = ZoneId.systemDefault()): TimeWindow {
        val now = clock.instant()
        val today = now.atZone(zoneId).toLocalDate()
        return when (filter) {
            TimeFilter.TODAY -> TimeWindow(
                today.atStartOfDay(zoneId).toInstant(),
                today.plusDays(1).atStartOfDay(zoneId).toInstant(),
            )
            TimeFilter.TOMORROW -> TimeWindow(
                today.plusDays(1).atStartOfDay(zoneId).toInstant(),
                today.plusDays(2).atStartOfDay(zoneId).toInstant(),
            )
            TimeFilter.NEXT_7_DAYS -> TimeWindow(
                today.atStartOfDay(zoneId).toInstant(),
                today.plusDays(7).atStartOfDay(zoneId).toInstant(),
            )
            TimeFilter.THIS_WEEKEND -> {
                val friday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(4)
                TimeWindow(friday.atTime(LocalTime.of(18, 0)).atZone(zoneId).toInstant(), friday.plusDays(3).atStartOfDay(zoneId).toInstant())
            }
            TimeFilter.CUSTOM, TimeFilter.ALL_FUTURE -> TimeWindow(now, null)
        }
    }

    fun apply(events: List<Event>, filter: TimeFilter, zoneId: ZoneId = ZoneId.systemDefault()): List<Event> {
        val window = window(filter, zoneId)
        return events.mapNotNull { it.forWindow(window, clock.instant()) }.sortedBy(::relevantStart)
    }

    fun apply(
        events: List<Event>,
        startDate: LocalDate,
        endDateInclusive: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<Event> {
        val start = startDate.atStartOfDay(zoneId).toInstant()
        val endExclusive = endDateInclusive.plusDays(1).atStartOfDay(zoneId).toInstant()
        val window = TimeWindow(start, endExclusive)
        return events.mapNotNull { it.forWindow(window, clock.instant()) }.sortedBy(::relevantStart)
    }

    private fun relevantStart(event: Event): Instant =
        if (event.scheduleType == EventScheduleType.RECURRING) event.nextOccurrenceAt ?: Instant.MAX else event.startsAt

    private fun Event.forWindow(window: TimeWindow, now: Instant): Event? {
        if (endsAt < now && scheduleType != EventScheduleType.RECURRING) return null
        if (scheduleType == EventScheduleType.RECURRING) {
            val occurrence = (occurrenceStarts + listOfNotNull(nextOccurrenceAt))
                .distinct().sorted()
                .firstOrNull { candidate ->
                    candidate >= now && candidate >= window.start &&
                        (window.endExclusive == null || candidate < window.endExclusive)
                } ?: return null
            return copy(nextOccurrenceAt = occurrence)
        }
        return takeIf {
            endsAt >= window.start && (window.endExclusive == null || startsAt < window.endExclusive)
        }
    }
}
