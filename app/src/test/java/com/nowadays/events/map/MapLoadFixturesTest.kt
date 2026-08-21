package com.nowadays.events.map

import com.nowadays.events.domain.model.EventStatus
import com.nowadays.events.support.DeterministicEventFixtures
import org.junit.Assert.*
import org.junit.Test

class MapLoadFixturesTest {
    @Test fun `load fixtures cover requested sizes and states`() {
        for (size in listOf(150, 500, 2_000)) {
            val events = DeterministicEventFixtures.events(size)
            assertEquals(size, events.size)
            assertTrue(events.groupBy { it.latitude to it.longitude }.any { it.value.size > 1 })
            assertTrue(events.any { it.status == EventStatus.CANCELLED })
            assertTrue(events.any { it.occurrenceCount > 1 })
        }
        assertEquals(3, DeterministicEventFixtures.family().size)
    }

    @Test fun `two thousand stable event ids map without loss`() {
        val events = DeterministicEventFixtures.events(2_000)
        assertEquals(2_000, events.map { it.id }.toSet().size)
        assertEquals(2_000, EventGeoJsonMapper.map(events).features().orEmpty().size)
    }
}
