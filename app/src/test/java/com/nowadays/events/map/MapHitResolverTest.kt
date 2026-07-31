package com.nowadays.events.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapHitResolverTest {
    @Test fun `selects the geographically nearest visible point`() {
        val points = mapOf(
            "near" to GeoPoint(43.8901, -0.5001),
            "far" to GeoPoint(43.91, -0.52),
        )
        assertEquals("near", MapHitResolver.nearest(GeoPoint(43.89, -0.50), 15.0, points, 54f))
    }

    @Test fun `does not select a distant event outside the hit radius`() {
        val points = mapOf("far" to GeoPoint(44.0, -0.7))
        assertNull(MapHitResolver.nearest(GeoPoint(43.89, -0.50), 15.0, points, 54f))
    }

    @Test fun `does not select a nearby marker outside its visible touch target`() {
        val points = mapOf("other" to GeoPoint(43.8920, -0.5000))
        assertNull(MapHitResolver.nearest(GeoPoint(43.89, -0.50), 15.0, points, 54f))
    }
}
