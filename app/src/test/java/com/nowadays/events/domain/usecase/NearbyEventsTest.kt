package com.nowadays.events.domain.usecase

import com.nowadays.events.support.DeterministicEventFixtures.event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NearbyEventsTest {
    @Test fun `distance is calculated locally in kilometres`() {
        assertEquals(1.11, NearbyEvents.distanceKm(43.89, -0.50, 43.90, -0.50), 0.03)
    }

    @Test fun `results are ordered and filtered by radius`() {
        val close = event("close", latitude = 43.891)
        val medium = event("medium", latitude = 43.92)
        val far = event("far", latitude = 44.20)
        assertEquals(listOf("close", "medium"), NearbyEvents.find(
            listOf(far, medium, close), 43.89, -0.50, 5.0,
        ).map { it.event.id })
    }

    @Test fun `equal distances have deterministic event id order`() {
        assertEquals(listOf("a", "b"), NearbyEvents.find(
            listOf(event("b"), event("a")), 43.89, -0.50, 1.0,
        ).map { it.event.id })
    }

    @Test fun `invalid coordinates unavailable position and empty list return no result`() {
        val invalid = event("invalid").copy(latitude = Double.NaN)
        assertTrue(NearbyEvents.find(listOf(invalid), 43.89, -0.50, 50.0).isEmpty())
        assertTrue(NearbyEvents.find(listOf(event("a")), Double.NaN, -0.50, 50.0).isEmpty())
        assertTrue(NearbyEvents.find(emptyList(), 43.89, -0.50, 50.0).isEmpty())
    }

    @Test fun `ongoing event is shown before a closer future event`() {
        val now = Instant.parse("2026-08-01T12:00:00Z")
        val ongoing = event("ongoing", latitude = 43.92).copy(
            startsAt = now.minusSeconds(600), endsAt = now.plusSeconds(600),
        )
        val close = event("close", latitude = 43.891).copy(
            startsAt = now.plusSeconds(600), endsAt = now.plusSeconds(1200),
        )
        assertEquals(listOf("ongoing", "close"), NearbyEvents.find(
            listOf(close, ongoing), 43.89, -0.50, 10.0, now,
        ).map { it.event.id })
    }

    @Test fun `radius boundary is inclusive`() {
        val event = event("edge")
        val distance = NearbyEvents.distanceKm(43.89, -0.50, event.latitude, event.longitude)
        assertEquals(1, NearbyEvents.find(listOf(event), 43.89, -0.50, distance).size)
    }
}
