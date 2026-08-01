import json
import sqlite3
import tempfile
import unittest
from pathlib import Path

from nowadays_agent import SCHEMA, detail_links, distance_km, event_from_curated, export_feed, extract_events, persist


class NowadaysAgentTests(unittest.TestCase):
    def test_extracts_structured_event_and_cancellation(self):
        payload = {
            "@context": "https://schema.org",
            "@type": "Event",
            "name": "Concert annulé",
            "startDate": "2026-08-01T20:00:00+02:00",
            "endDate": "2026-08-01T22:00:00+02:00",
            "eventStatus": "https://schema.org/EventCancelled",
            "url": "https://example.org/concert",
            "location": {
                "@type": "Place",
                "name": "Le Théâtre",
                "address": {"addressLocality": "Mont-de-Marsan"},
                "geo": {"latitude": 43.89, "longitude": -0.50},
            },
        }
        html = f'<script type="application/ld+json">{json.dumps(payload)}</script>'
        event = extract_events(html, "Test", "https://example.org")[0]
        self.assertEqual("cancelled", event.status)
        self.assertEqual("Le Théâtre", event.venue)

    def test_distance_filter(self):
        self.assertLess(distance_km(43.8904, -0.5007, 43.758, -0.572), 50)
        self.assertGreater(distance_km(43.8904, -0.5007, 48.8566, 2.3522), 50)

    def test_builds_validated_curated_event(self):
        event = event_from_curated({
            "@type": "Event",
            "name": "Une matinée pêche",
            "startDate": "2026-08-05T09:30:00+02:00",
            "endDate": "2026-08-05T12:00:00+02:00",
            "url": "https://example.org/peche",
            "source_name": "Source validée",
            "location": {
                "name": "Grenade-sur-l’Adour",
                "geo": {"latitude": 43.773106, "longitude": -0.431053},
            },
        })
        self.assertIsNotNone(event)
        self.assertEqual("2026-08-05T07:30:00+00:00", event.start_at)
        self.assertEqual("Source validée", event.source_name)

    def test_detail_links_are_same_domain_and_bounded(self):
        body = """
        <a href="/agenda/concert-1">Concert</a>
        <a href="https://outside.example/agenda/concert-2">Externe</a>
        <a href="/contact">Contact</a>
        <a href="/agenda/concert-3?tracking=1">Autre</a>
        """
        self.assertEqual(
            ["https://example.org/agenda/concert-1"],
            detail_links(body, "https://example.org/agenda/", 1),
        )

    def test_persist_merges_same_fingerprint_and_keeps_sources(self):
        html = """
        <script type="application/ld+json">
        {"@type":"Event","name":"Fête locale","startDate":"2026-08-01T20:00:00+02:00",
        "location":{"name":"Arènes","geo":{"latitude":43.89,"longitude":-0.50}}}
        </script>
        """
        first = extract_events(html, "A", "https://a.example/event")[0]
        second = extract_events(html, "B", "https://b.example/event")[0]
        with tempfile.TemporaryDirectory() as directory:
            database = sqlite3.connect(":memory:")
            database.executescript(SCHEMA)
            persist(database, [first, second], "2026-07-29T10:00:00+00:00")
            self.assertEqual(1, database.execute("SELECT COUNT(*) FROM events").fetchone()[0])
            self.assertEqual(2, database.execute("SELECT COUNT(*) FROM event_sources").fetchone()[0])
            output = Path(directory) / "events.json"
            self.assertEqual(1, export_feed(database, output))
            self.assertEqual(2, len(json.loads(output.read_text())["events"][0]["source_urls"]))


if __name__ == "__main__":
    unittest.main()
