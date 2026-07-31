package com.nowadays.events.data.importer

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class EventMetadataParserTest {
    @Test fun `extracts every structured event`() {
        val html = """<script type="application/ld+json">[
          {"@type":"Event","name":"Concert A","startDate":"2026-07-22T18:00:00+02:00","endDate":"2026-07-22T20:00:00+02:00"},
          {"@type":"Event","name":"Concert B","startDate":"2026-07-23T19:00:00+02:00","endDate":"2026-07-23T21:00:00+02:00"}
        ]</script>"""
        val events = EventMetadataParser.parseAll(html).orEmpty()
        assertEquals(listOf("Concert A", "Concert B"), events.map { it.title })
        assertEquals("2026-07-22T18:00", events.first().startsAt)
    }

    @Test fun `infers programme range and daily occurrences from article`() {
        val html = """<html><head><title>Madeleine 2026 à Mont-de-Marsan du 22 au 26 juillet</title>
          <meta property="og:title" content="Fêtes de la Madeleine 2026" /></head><body>
          <p>Le programme se déroule du 22 au 26 juillet.</p>
          <h2>Mercredi 22 juillet</h2><p>Ouverture des fêtes de 18h à 23h.</p>
          <h2>Jeudi 23 juillet</h2><p>Animations de 10h à 22h.</p></body></html>"""
        val events = EventMetadataParser.parseAll(html).orEmpty()
        assertTrue(events.size >= 3)
        assertEquals("2026-07-22T00:00", events.first().startsAt)
        assertTrue(events.all { it.venue.contains("Mont-de-Marsan") })
    }
}
