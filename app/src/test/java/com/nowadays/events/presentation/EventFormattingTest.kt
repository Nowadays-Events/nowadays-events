package com.nowadays.events.presentation

import com.nowadays.events.domain.model.EventTimePrecision
import com.nowadays.events.domain.model.EventScheduleType
import com.nowadays.events.support.DeterministicEventFixtures.event
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class EventFormattingTest {
    private val paris = ZoneId.of("Europe/Paris")

    @Test fun `date only never presents midnight as an exact time`() {
        val value = eventScheduleLabel(event("date").copy(
            startsAt = Instant.parse("2026-09-12T22:00:00Z"), timePrecision = EventTimePrecision.DATE_ONLY,
        ), paris)
        assertTrue(value.contains("Horaire à confirmer"))
        assertFalse(value.contains("00h"))
    }

    @Test fun `real midnight remains visible when explicitly exact`() {
        val value = eventScheduleLabel(event("midnight").copy(
            startsAt = Instant.parse("2026-09-12T22:00:00Z"), timePrecision = EventTimePrecision.EXACT,
        ), paris)
        assertTrue(value.contains("00 h"))
    }

    @Test fun `approximate label preserves useful original wording`() {
        val value = eventScheduleLabel(event("evening").copy(
            timePrecision = EventTimePrecision.APPROXIMATE, originalTimeText = "En soirée",
        ), paris)
        assertTrue(value.contains("En soirée"))
    }

    @Test fun `recurrence displays its next real occurrence rather than historical start`() {
        val value = eventScheduleLabel(event("recurring").copy(
            startsAt = Instant.parse("2026-01-01T10:00:00Z"),
            scheduleType = EventScheduleType.RECURRING,
            nextOccurrenceAt = Instant.parse("2026-09-06T10:00:00Z"),
        ), paris)
        assertTrue(value.startsWith("Prochaine date :"))
        assertTrue(value.contains("6 sept."))
        assertFalse(value.contains("1 janv."))
    }

    @Test fun `continuous event clearly says when it is in progress`() {
        val value = eventScheduleLabel(event("continuous").copy(
            startsAt = Instant.parse("2026-09-01T10:00:00Z"),
            endsAt = Instant.parse("2026-09-03T18:00:00Z"),
            scheduleType = EventScheduleType.CONTINUOUS,
        ), paris, Instant.parse("2026-09-02T10:00:00Z"))
        assertTrue(value.startsWith("En cours · jusqu’au"))
    }

    @Test fun `recurrence without future occurrence never falls back to first date`() {
        val value = eventScheduleLabel(event("finished").copy(
            startsAt = Instant.parse("2026-01-01T10:00:00Z"),
            scheduleType = EventScheduleType.RECURRING,
            nextOccurrenceAt = null,
        ), paris)
        assertEquals("Aucune prochaine occurrence", value)
    }
}
