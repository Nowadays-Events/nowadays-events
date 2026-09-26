package com.nowadays.events.presentation.map

import com.nowadays.events.data.location.LocationSuggestion
import com.nowadays.events.data.location.LocationSearcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferencePlacePolicyTest {
    @Test fun `city result becomes a readable reference place`() {
        val place = ReferencePlacePolicy.fromSuggestion(LocationSuggestion("Biscarrosse, 40600", 44.394, -1.168))
        assertEquals(ReferencePlace("Biscarrosse", 44.394, -1.168), place)
        assertEquals("Autour de Biscarrosse", ReferencePlacePolicy.title(place))
    }

    @Test fun `gps place uses around me label`() {
        val place = ReferencePlacePolicy.fromGps(43.89, -0.50)
        assertEquals("Autour de moi", ReferencePlacePolicy.title(place))
    }

    @Test fun `gps failures distinguish missing fix and refused permission`() {
        assertTrue(ReferencePlacePolicy.gpsFailureMessage(true).startsWith("Position indisponible"))
        assertTrue(ReferencePlacePolicy.gpsFailureMessage(false).startsWith("Localisation refusée"))
    }

    @Test fun `city search can be replaced by a deterministic offline fake`() = runBlocking {
        val fake: LocationSearcher = object : LocationSearcher {
            override suspend fun search(query: String, near: String?): List<LocationSuggestion> =
                if (query == "Biscarrosse") listOf(LocationSuggestion("Biscarrosse", 44.394, -1.168)) else emptyList()
        }

        assertEquals("Biscarrosse", fake.search("Biscarrosse").single().label)
    }
}
