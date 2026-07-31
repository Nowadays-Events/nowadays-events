package com.nowadays.events.domain.usecase

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateEventInputTest {
    private val validator = ValidateEventInput()

    @Test fun validInputHasNoErrors() {
        val errors = validator(validInput())
        assertTrue(errors.isEmpty)
    }

    @Test fun invalidInputReportsAllImportantFields() {
        val errors = validator(
            validInput().copy(
                title = "x",
                shortDescription = "court",
                endsAt = Instant.parse("2026-07-22T17:00:00Z"),
                latitude = 95.0,
                sourceUrl = "javascript:alert(1)",
                priceCents = -1,
            ),
        )
        assertFalse(errors.isEmpty)
        assertTrue(errors.title != null && errors.shortDescription != null && errors.dates != null)
        assertTrue(errors.coordinates != null && errors.sourceUrl != null && errors.price != null)
    }

    private fun validInput() = EventInput(
        title = "Concert fictif",
        shortDescription = "Une description suffisamment longue",
        startsAt = Instant.parse("2026-07-22T18:00:00Z"),
        endsAt = Instant.parse("2026-07-22T20:00:00Z"),
        venueName = "Salle fictive",
        address = "1 rue de Paris",
        latitude = 48.85,
        longitude = 2.35,
        sourceUrl = "https://example.invalid/event",
        priceCents = 1200,
    )
}
