package com.nowadays.events.map

import org.junit.Assert.assertEquals
import org.junit.Test

class CompassMarginTest {
    @Test
    fun marginsScaleWithScreenDensity() {
        assertEquals(112, compassTopMarginPx(1f))
        assertEquals(336, compassTopMarginPx(3f))
        assertEquals(48, compassEndMarginPx(3f))
    }
}
