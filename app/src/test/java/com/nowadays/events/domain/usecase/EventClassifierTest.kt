package com.nowadays.events.domain.usecase

import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.domain.model.EventPrice
import com.nowadays.events.support.DeterministicEventFixtures.event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventClassifierTest {
    @Test fun `concert is classified by explicit title`() {
        val result = EventClassifier.classify(event("concert").copy(title = "Concert en plein air"))
        assertEquals(EventKind.CONCERT, result.kind)
        assertEquals(EventMood.FESTIVE, result.mood)
        assertTrue(result.summary().contains("Concert"))
    }

    @Test fun `family workshop remains an explainable inference`() {
        val result = EventClassifier.classify(event("workshop").copy(
            title = "Atelier créatif pour enfants", category = EventCategory.FAMILY, price = EventPrice.Free,
        ))
        assertEquals(EventKind.WORKSHOP, result.kind)
        assertEquals(EventAudience.CHILDREN, result.audience)
        assertTrue(result.confidence != Confidence.LOW)
    }

    @Test fun `ambiguous event does not invent an audience or mood`() {
        val result = EventClassifier.classify(event("unknown").copy(title = "Rencontre", shortDescription = ""))
        assertEquals(EventKind.OTHER, result.kind)
        assertEquals(EventAudience.UNKNOWN, result.audience)
    }

    @Test fun `Biscarrosse weekend fixture titles remain deterministic`() {
        val expectations = mapOf(
            "Stage de danse cubaine" to EventKind.WORKSHOP,
            "Faîtes de l’AMAP" to EventKind.MARKET,
            "Run 2K Challenge" to EventKind.SPORT,
            "Voyage en comédie" to EventKind.SHOW,
            "Biscarrosse dit Merci" to EventKind.OTHER,
        )
        expectations.forEach { (title, expected) ->
            assertEquals(title, expected, EventClassifier.classify(event(title).copy(title = title)).kind)
        }
    }
}
