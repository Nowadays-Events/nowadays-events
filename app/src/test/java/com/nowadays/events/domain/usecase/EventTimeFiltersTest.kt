package com.nowadays.events.domain.usecase

import com.nowadays.events.domain.model.DataOrigin
import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.domain.model.EventPrice
import com.nowadays.events.domain.model.EventScheduleType
import com.nowadays.events.domain.model.EventTimePrecision
import com.nowadays.events.domain.model.TimeFilter
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class EventTimeFiltersTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val clock = Clock.fixed(Instant.parse("2026-07-18T10:00:00Z"), zone)
    private val filters = EventTimeFilters(clock)

    @Test fun todayIncludesOverlappingEvent() {
        val event = event("2026-07-17T23:00:00Z", "2026-07-18T12:00:00Z")
            .copy(scheduleType = EventScheduleType.CONTINUOUS)
        assertEquals(1, filters.apply(listOf(event), TimeFilter.TODAY, zone).size)
    }

    @Test fun nextSevenDaysIncludesNextMondayButExcludesFollowingSaturday() {
        val monday = event("2026-07-19T22:00:00Z", "2026-07-20T00:00:00Z")
        val nextSaturday = event("2026-07-24T22:00:00Z", "2026-07-25T01:00:00Z")
        assertEquals(listOf(monday.id), filters.apply(listOf(monday, nextSaturday), TimeFilter.NEXT_7_DAYS, zone).map { it.id })
    }

    @Test fun tomorrowIncludesOnlyNextDay() {
        val tomorrow = event("2026-07-19T08:00:00Z", "2026-07-19T10:00:00Z")
        val later = event("2026-07-20T08:00:00Z", "2026-07-20T10:00:00Z")
        assertEquals(listOf(tomorrow.id), filters.apply(listOf(tomorrow, later), TimeFilter.TOMORROW, zone).map { it.id })
    }

    @Test fun weekendStartsFridayAtSixPmLocalTime() {
        val fridayBeforeWeekend = EventTimeFilters(
            Clock.fixed(Instant.parse("2026-07-17T15:00:00Z"), zone),
        )
        val before = event("2026-07-17T14:00:00Z", "2026-07-17T15:59:59Z")
        val during = event("2026-07-17T16:00:00Z", "2026-07-17T18:00:00Z")
        assertEquals(
            listOf(during.id),
            fridayBeforeWeekend.apply(listOf(before, during), TimeFilter.THIS_WEEKEND, zone).map { it.id },
        )
    }

    @Test fun weekendIncludesSaturdayEvent() {
        val saturday = event("2026-07-18T12:00:00Z", "2026-07-18T14:00:00Z")
        assertEquals(1, filters.apply(listOf(saturday), TimeFilter.THIS_WEEKEND, zone).size)
    }

    @Test fun allFutureExcludesExpiredEvents() {
        val expired = event("2026-07-18T07:00:00Z", "2026-07-18T09:59:59Z")
        assertEquals(0, filters.apply(listOf(expired), TimeFilter.ALL_FUTURE, zone).size)
    }

    @Test fun recurringEventIsAbsentBetweenOccurrencesAndVisibleOnItsNextDay() {
        val nextWednesday = event("2026-07-01T08:00:00Z", "2026-09-30T09:00:00Z")
            .copy(
                occurrenceCount = 12, scheduleType = EventScheduleType.RECURRING,
                occurrenceStarts = listOf(Instant.parse("2026-07-22T08:00:00Z")),
            )
        assertEquals(0, filters.apply(listOf(nextWednesday), TimeFilter.TODAY, zone).size)
        val wednesdayClock = Clock.fixed(Instant.parse("2026-07-22T06:00:00Z"), zone)
        assertEquals(1, EventTimeFilters(wednesdayClock).apply(listOf(nextWednesday), TimeFilter.TODAY, zone).size)
    }

    @Test fun recurringEventUsesNextOccurrenceForTomorrowSevenDaysWeekendAndCustomRange() {
        val saturday = event("2026-06-01T08:00:00Z", "2026-10-01T09:00:00Z")
            .copy(
                occurrenceCount = 18, scheduleType = EventScheduleType.RECURRING,
                occurrenceStarts = listOf(Instant.parse("2026-07-19T08:00:00Z")),
            )
        assertEquals(1, filters.apply(listOf(saturday), TimeFilter.TOMORROW, zone).size)
        assertEquals(1, filters.apply(listOf(saturday), TimeFilter.NEXT_7_DAYS, zone).size)
        assertEquals(1, filters.apply(listOf(saturday), TimeFilter.THIS_WEEKEND, zone).size)
        assertEquals(1, filters.apply(listOf(saturday), LocalDate.of(2026, 7, 19), LocalDate.of(2026, 7, 19), zone).size)
        assertEquals(0, filters.apply(listOf(saturday), LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 21), zone).size)
    }

    @Test fun recurringEventAdvancesFromFirstOccurrenceAndRequiresAFutureDate() {
        val recurring = event("2026-06-01T08:00:00Z", "2026-10-01T09:00:00Z")
            .copy(occurrenceCount = 8, scheduleType = EventScheduleType.RECURRING)
        assertEquals(0, filters.apply(listOf(recurring), TimeFilter.ALL_FUTURE, zone).size)
        val first = recurring.copy(nextOccurrenceAt = Instant.parse("2026-07-18T12:00:00Z"))
        val following = first.copy(nextOccurrenceAt = Instant.parse("2026-07-25T12:00:00Z"))
        assertEquals(first.id, filters.apply(listOf(first), TimeFilter.TODAY, zone).single().id)
        assertEquals(0, filters.apply(listOf(following), TimeFilter.TODAY, zone).size)
    }

    @Test fun localTimezoneKeepsOccurrenceOnTheCorrectCalendarDay() {
        val justAfterMidnightParis = event("2026-06-01T08:00:00Z", "2026-10-01T09:00:00Z")
            .copy(
                occurrenceCount = 3, scheduleType = EventScheduleType.RECURRING,
                occurrenceStarts = listOf(Instant.parse("2026-07-18T22:30:00Z")),
            )
        assertEquals(0, filters.apply(listOf(justAfterMidnightParis), TimeFilter.TODAY, zone).size)
        assertEquals(1, filters.apply(listOf(justAfterMidnightParis), TimeFilter.TOMORROW, zone).size)
    }

    @Test fun recurringWindowSelectsTheRealOccurrenceInsideThatWindow() {
        val recurring = event("2026-06-01T08:00:00Z", "2026-10-01T09:00:00Z").copy(
            scheduleType = EventScheduleType.RECURRING,
            occurrenceCount = 3,
            nextOccurrenceAt = Instant.parse("2026-07-18T12:00:00Z"),
            occurrenceStarts = listOf(
                Instant.parse("2026-07-18T12:00:00Z"),
                Instant.parse("2026-07-19T12:00:00Z"),
            ),
        )
        val tomorrow = filters.apply(listOf(recurring), TimeFilter.TOMORROW, zone).single()
        assertEquals(Instant.parse("2026-07-19T12:00:00Z"), tomorrow.nextOccurrenceAt)
    }

    @Test fun sundayAtOneTenKeepsOnlyActuallyOngoingEvents() {
        val sundayClock = Clock.fixed(Instant.parse("2026-09-26T23:10:00Z"), zone)
        val sundayFilters = EventTimeFilters(sundayClock)
        val overnight = event("2026-09-26T20:00:00Z", "2026-09-27T01:00:00Z")
            .copy(id = "overnight", timePrecision = EventTimePrecision.EXACT)
        val continuous = event("2026-09-25T08:00:00Z", "2026-09-27T18:00:00Z")
            .copy(id = "continuous", scheduleType = EventScheduleType.CONTINUOUS)
        // Ancien flux : fin technique à 23:59 UTC, soit 01:59 le dimanche à Paris.
        val dateOnlySaturday = event("2026-09-26T00:00:00Z", "2026-09-26T23:59:59Z")
            .copy(id = "date-only", timePrecision = EventTimePrecision.DATE_ONLY)
        val ended = event("2026-09-26T18:00:00Z", "2026-09-26T22:30:00Z")
            .copy(id = "ended", timePrecision = EventTimePrecision.EXACT)

        assertEquals(
            setOf("overnight", "continuous"),
            sundayFilters.apply(
                listOf(overnight, continuous, dateOnlySaturday, ended), TimeFilter.TODAY, zone,
            ).map { it.id }.toSet(),
        )
    }

    @Test fun tourinsoftOccurrencesAreFilteredByTheirRealNextDates() {
        val referenceClock = Clock.fixed(Instant.parse("2026-09-26T23:10:00Z"), zone)
        val referenceFilters = EventTimeFilters(referenceClock)
        val workshop = event("2026-09-09T22:00:00Z", "2026-10-29T22:59:59Z").copy(
            id = "workshop", scheduleType = EventScheduleType.RECURRING,
            nextOccurrenceAt = Instant.parse("2026-10-01T16:00:00Z"),
            occurrenceStarts = listOf(
                Instant.parse("2026-10-01T16:00:00Z"), Instant.parse("2026-10-08T16:00:00Z"),
            ),
        )
        val bookClub = event("2026-09-24T22:00:00Z", "2026-12-10T22:59:59Z").copy(
            id = "book-club", scheduleType = EventScheduleType.RECURRING,
            nextOccurrenceAt = Instant.parse("2026-10-29T13:15:00Z"),
            occurrenceStarts = listOf(
                Instant.parse("2026-10-29T13:15:00Z"), Instant.parse("2026-12-10T13:00:00Z"),
            ),
        )

        assertEquals(emptyList<Event>(), referenceFilters.apply(listOf(workshop, bookClub), TimeFilter.TODAY, zone))
        assertEquals(
            listOf("workshop"),
            referenceFilters.apply(listOf(workshop, bookClub), TimeFilter.NEXT_7_DAYS, zone).map { it.id },
        )
        assertEquals(
            listOf("workshop", "book-club"),
            referenceFilters.apply(listOf(workshop, bookClub), TimeFilter.ALL_FUTURE, zone).map { it.id },
        )
    }

    private fun event(start: String, end: String) = Event(
        id = start, title = "Test", shortDescription = "Test", fullDescription = null,
        category = EventCategory.CULTURE, startsAt = Instant.parse(start), endsAt = Instant.parse(end),
        venueName = "Lieu", address = "Paris", latitude = 48.85, longitude = 2.35,
        sourceUrl = "https://example.invalid", imageUrl = null, organizer = null,
        price = EventPrice.Free, updatedAt = clock.instant(), origin = DataOrigin.DEMO,
    )
}
