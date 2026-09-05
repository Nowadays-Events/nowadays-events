package com.nowadays.events.presentation.detail

import com.nowadays.events.support.DeterministicEventFixtures.event
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class EventDetailFormattingTest {
    @Test fun `address presentation separates a concatenated country`() {
        assertEquals(
            "214 rue de la Provence 40280 Saint-Pierre-du-Mont, France",
            normalizedAddress("214 rue de la Provence 40280 Saint-Pierre-du-MontFrance"),
        )
    }

    @Test fun `address presentation preserves an already separated address`() {
        assertEquals("40280 Saint-Pierre-du-Mont, France", normalizedAddress("40280 Saint-Pierre-du-Mont, France"))
    }

    @Test fun `recurrence summary uses next occurrence instead of continuous range`() {
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withZone(ZoneId.of("Europe/Paris"))
        val recurring = event("weekly", occurrenceCount = 4).copy(
            nextOccurrenceAt = Instant.parse("2026-09-08T18:00:00Z"),
        )
        val label = eventDateLabel(recurring, formatter)
        assertEquals(true, label.startsWith("Prochaine date :"))
        assertEquals(true, label.contains("Puis 3 autres dates"))
    }

    @Test fun `recurrence without future date is explicit`() {
        assertEquals(
            "Aucune prochaine date confirmée",
            eventDateLabel(
                event("weekly", occurrenceCount = 4).copy(nextOccurrenceAt = null),
                DateTimeFormatter.ISO_INSTANT,
            ),
        )
    }
}
