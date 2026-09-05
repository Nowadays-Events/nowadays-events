package com.nowadays.events.map

import com.nowadays.events.domain.model.EventStatus
import com.nowadays.events.support.DeterministicEventFixtures.event
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterMarkerPolicyTest {
    @Test fun `active cluster has no cancellation indicator`() {
        val style = ClusterMarkerPolicy.style(listOf(event("a"), event("b")))
        assertFalse(style.hasCancelledEvent)
        assertFalse(style.allEventsCancelled)
    }

    @Test fun `one cancellation adds a secondary indicator`() {
        val style = ClusterMarkerPolicy.style(listOf(
            event("a"), event("b").copy(status = EventStatus.CANCELLED),
        ))
        assertTrue(style.hasCancelledEvent)
        assertFalse(style.allEventsCancelled)
    }

    @Test fun `fully cancelled cluster keeps indicator without changing cluster meaning`() {
        val style = ClusterMarkerPolicy.style(listOf(
            event("a").copy(status = EventStatus.CANCELLED),
            event("b").copy(status = EventStatus.CANCELLED),
        ))
        assertTrue(style.hasCancelledEvent)
        assertTrue(style.allEventsCancelled)
    }

    @Test fun `cancellation state does not change stable cluster id`() {
        val positions = mapOf("a" to ScreenPoint(0f, 0f), "b" to ScreenPoint(10f, 0f))
        val active = listOf(event("a"), event("b"))
        val mixed = listOf(event("a"), event("b").copy(status = EventStatus.CANCELLED))
        assertTrue(ClusterMarkerPolicy.style(mixed).hasCancelledEvent)
        assertTrue(
            ProximityClusterer.group(active, positions, 40f).single().id ==
                ProximityClusterer.group(mixed, positions, 40f).single().id,
        )
    }
}
