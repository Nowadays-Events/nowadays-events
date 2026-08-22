package com.nowadays.events.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapViewportPaddingTest {
    @Test fun `viewport reserves the period controls and bottom actions`() {
        val padding = mapViewportPadding(3f)
        assertEquals(420, padding.top)
        assertEquals(216, padding.bottom)
        assertTrue(padding.top > padding.bottom)
        assertEquals(0, padding.left)
        assertEquals(0, padding.right)
    }
}
