package com.nowadays.events.map

import com.nowadays.events.domain.model.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ProximityClustererTest {
    @Test fun `group ungroup and regroup keeps a stable cluster id`() {
        val events = listOf(event("b"), event("a"))
        val close = mapOf("a" to ScreenPoint(0f, 0f), "b" to ScreenPoint(10f, 0f))
        val apart = mapOf("a" to ScreenPoint(0f, 0f), "b" to ScreenPoint(100f, 0f))
        val first = ProximityClusterer.group(events, close, 40f).single()
        assertEquals(2, ProximityClusterer.group(events, apart, 40f).size)
        val regrouped = ProximityClusterer.group(events.reversed(), close, 40f).single()
        assertEquals(first.id, regrouped.id)
        assertEquals(listOf("a", "b"), regrouped.events.map(Event::id))
    }

    @Test fun `nearby but distinct concentrations remain separate`() {
        val events = listOf(event("a"), event("b"), event("c"))
        val points = mapOf(
            "a" to ScreenPoint(0f, 0f), "b" to ScreenPoint(15f, 0f), "c" to ScreenPoint(100f, 0f),
        )
        assertEquals(listOf(2, 1), ProximityClusterer.group(events, points, 30f).map { it.events.size }.sortedDescending())
    }

    private fun event(id: String) = Event(
        id, id, id, null, EventCategory.CULTURE, Instant.parse("2026-08-23T10:00:00Z"),
        Instant.parse("2026-08-23T12:00:00Z"), "Lieu", "Mont-de-Marsan", 43.89, -0.50,
        "https://example.invalid/$id", null, null, EventPrice.Free, Instant.EPOCH, DataOrigin.DEMO,
    )
}
