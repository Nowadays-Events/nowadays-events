package com.nowadays.events.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventNavigationTest {
    @Test fun mapFocusHighlightsWithoutOpeningDetailByDefault() {
        val request = MapFocusRequest("event-42", 43.89, -0.50)
        assertEquals("event-42", request.eventId)
        assertFalse(request.openDetail)
    }

    @Test fun openingDetailRemainsAnExplicitChoice() {
        assertTrue(MapFocusRequest("id", 1.0, 2.0, openDetail = true).openDetail)
    }

    @Test fun focusRequestKeepsExactCoordinates() {
        val request = MapFocusRequest("event-42", 43.8904, -0.5007)
        assertEquals(43.8904, request.latitude, 0.0)
        assertEquals(-0.5007, request.longitude, 0.0)
    }
}
