package com.nowadays.events.presentation.map

import org.junit.Assert.assertEquals
import org.junit.Test

class ReferencePlaceCodecTest {
    @Test fun `recent place survives persistence`() {
        val places = listOf(ReferencePlace("Biscarrosse", 44.394, -1.168), ReferencePlace("Point choisi", 43.8, -0.7, "map"))
        assertEquals(places, ReferencePlaceCodec.decode(ReferencePlaceCodec.encode(places)))
    }

    @Test fun `only three recent places are retained`() {
        val places = (1..5).map { ReferencePlace("Ville $it", 43.0 + it, -1.0) }
        assertEquals(3, ReferencePlaceCodec.decode(ReferencePlaceCodec.encode(places)).size)
    }
}
