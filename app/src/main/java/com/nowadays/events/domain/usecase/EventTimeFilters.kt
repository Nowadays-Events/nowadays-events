package com.nowadays.events.domain.usecase

import com.nowadays.events.domain.model.Event
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
        return events.filter { event ->
            event.endsAt >= window.start && (window.endExclusive == null || event.startsAt < window.endExclusive)
        }.sortedBy(Event::startsAt)
    }

    fun apply(
        events: List<Event>,
        startDate: LocalDate,
        endDateInclusive: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<Event> {
        val start = startDate.atStartOfDay(zoneId).toInstant()
        val endExclusive = endDateInclusive.plusDays(1).atStartOfDay(zoneId).toInstant()
        return events.filter { it.endsAt >= start && it.startsAt < endExclusive }.sortedBy(Event::startsAt)
    }
}
