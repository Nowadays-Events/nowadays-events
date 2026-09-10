package com.nowadays.events.presentation

import com.nowadays.events.domain.model.EventTimePrecision
import com.nowadays.events.support.DeterministicEventFixtures.event
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
}
