package com.nowadays.events.presentation.map

import com.nowadays.events.domain.model.*
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactEventListPolicyTest {
    private val now = Instant.parse("2026-09-26T10:00:00Z")
    private val zone = ZoneId.of("Europe/Paris")

    @Test fun `counter is deterministic for today and radius`() {
        assertEquals("5 événements aujourd’hui autour de vous", CompactEventListPolicy.introduction(5, 30, TimeFilter.TODAY))
        assertEquals("8 idées à moins de 15 km", CompactEventListPolicy.introduction(8, 15, TimeFilter.NEXT_7_DAYS))
    }

    @Test fun `today separates continuous event from later occurrence without duplicates`() {
        val running = event("running", now.minusSeconds(3600), now.plusSeconds(3600), EventScheduleType.CONTINUOUS)
        val later = event("later", now.plusSeconds(7200), now.plusSeconds(10800))
        val sections = CompactEventListPolicy.sections(listOf(NearbyListItem(running, 2.0), NearbyListItem(later, 3.0)), TimeFilter.TODAY, now, zone)

        assertEquals(listOf("En cours", "Plus tard aujourd’hui"), sections.map { it.title })
        assertEquals(2, sections.flatMap { it.events }.map { it.event.id }.distinct().size)
    }

    @Test fun `recurrence uses next occurrence when grouping`() {
        val next = now.plusSeconds(86_400)
        val recurring = event("recurring", now.minusSeconds(86_400), now.plusSeconds(172_800), EventScheduleType.RECURRING).copy(nextOccurrenceAt = next, occurrenceCount = 3)
        val section = CompactEventListPolicy.sections(listOf(NearbyListItem(recurring, 1.0)), TimeFilter.ALL_FUTURE, now, zone).single()
        assertEquals("Demain", section.title)
    }

    @Test fun `every category has a compact marker`() {
        assertTrue(EventCategory.entries.all { CompactEventListPolicy.icon(it).isNotBlank() })
    }

    private fun event(id: String, start: Instant, end: Instant, schedule: EventScheduleType = EventScheduleType.SINGLE) = Event(
        id, "Titre suffisamment long pour le test", "Description", null, EventCategory.CULTURE,
        start, end, "Lieu", "Adresse", 43.89, -0.50, "https://example.invalid/$id", null, null,
        EventPrice.Unknown, now, DataOrigin.DEMO, scheduleType = schedule,
    )
}
