import json
import sqlite3
import tempfile
import unittest
from pathlib import Path

from nowadays_agent import SCHEMA, detail_links, distance_km, event_from_curated, export_candidates, export_feed, extract_armagnac_event, extract_events, hydrate_previous_feed, mark_unverified, persist


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

    def test_postponed_is_not_treated_as_cancelled(self):
        payload = {
            "@type": "Event", "name": "Concert reporté",
            "startDate": "2026-09-01T20:00:00+02:00",
            "eventStatus": "https://schema.org/EventPostponed",
            "url": "https://example.org/postponed",
            "location": {"name": "Salle", "geo": {"latitude": 43.89, "longitude": -0.50}},
        }
        html = f'<script type="application/ld+json">{json.dumps(payload)}</script>'
        self.assertEqual("postponed", extract_events(html, "Test", payload["url"])[0].status)

    def test_extracts_category_and_price_without_inventing_free(self):
        base = {
            "@type": "Event", "name": "Concert jazz", "startDate": "2026-09-01T20:00:00+02:00",
            "url": "https://example.org/jazz",
            "location": {"name": "Salle", "geo": {"latitude": 43.89, "longitude": -0.50}},
        }
        unknown = extract_events(
            f'<script type="application/ld+json">{json.dumps(base)}</script>', "Test", base["url"],
        )[0]
        paid_payload = {**base, "offers": {"price": "12.50", "priceCurrency": "EUR"}}
        paid = extract_events(
            f'<script type="application/ld+json">{json.dumps(paid_payload)}</script>', "Test", base["url"],
        )[0]
        self.assertEqual("MUSIC", unknown.category)
        self.assertEqual("unknown", unknown.price_type)
        self.assertEqual(("paid", 1250, "EUR"), (paid.price_type, paid.price_cents, paid.currency))

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

    def test_candidate_queue_never_promotes_unreviewed_items(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "candidates.json"
            count = export_candidates({"candidate_events": [
                {"name": "Annonce sociale", "url": "https://social.example/post"},
            ]}, output, "2026-08-02T10:00:00+00:00")
            payload = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(1, count)
            self.assertEqual("incomplete", payload["candidates"][0]["review_status"])
            self.assertIn("startDate", payload["candidates"][0]["missing_fields"])

    def test_roquefort_events_are_kept_as_curated_fallback(self):
        config_path = Path(__file__).with_name("config.json")
        config = json.loads(config_path.read_text(encoding="utf-8"))
        roquefort = [
            item for item in config["curated_events"]
            if "roquefort" in item.get("name", "").lower()
        ]
        self.assertGreaterEqual(len(roquefort), 3)
        self.assertTrue(all(item.get("startDate", "").startswith("2026-08-") for item in roquefort))
        self.assertTrue(all(item.get("location", {}).get("geo") for item in roquefort))

    def test_armagnac_has_an_independent_official_source(self):
        config_path = Path(__file__).with_name("config.json")
        config = json.loads(config_path.read_text(encoding="utf-8"))
        armagnac_hosts = {
            source["url"].split("/")[2]
            for source in config["sources"]
            if "armagnac" in source["name"].lower()
        }
        self.assertIn("www.tourisme-landesdarmagnac.fr", armagnac_hosts)
        self.assertIn("www.tourismelandes.com", armagnac_hosts)

    def test_extracts_armagnac_html_period_address_and_coordinates(self):
        body = '''
        <h1 class="title"><span>Fêtes de Roquefort</span></h1>
        <img src="map?center=44.03466+-0.32175&amp;zoom=11">
        <div class="detailManifDates">
          <span class="manif-date-day">Du mercredi</span>
          <span class="manif-date-day-num">12</span>
          <span class="manif-date-month">août</span>
          <span class="manif-date-to">au 16 août 2026</span>
        </div>
        <div class="detailManifType"><ul><li>Divertissement</li></ul></div>
        <table><tr class="address"><td>Adresse</td><td>40120 Roquefort</td></tr></table>
        <div class="detailDescriptionManif">Cavalcade<br>et fête foraine.</div>
        '''
        event = extract_armagnac_event(body, "Landes d'Armagnac", "https://example.org/agenda/1")
        self.assertIsNotNone(event)
        self.assertEqual("Fêtes de Roquefort", event.title)
        self.assertEqual("2026-08-12T00:00:00+00:00", event.start_at)
        self.assertEqual("2026-08-16T23:59:00+00:00", event.end_at)
        self.assertEqual((44.03466, -0.32175), (event.latitude, event.longitude))
        self.assertIn("40120 Roquefort", event.address)

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

    def test_previous_feed_becomes_unverified_without_becoming_cancelled(self):
        with tempfile.TemporaryDirectory() as directory:
            feed = Path(directory) / "previous.json"
            feed.write_text(json.dumps({"events": [{
                "external_id": "old", "title": "Ancien événement", "description": "",
                "start_at": "2026-08-10T10:00:00+00:00", "end_at": "2026-08-10T12:00:00+00:00",
                "venue": "Salle", "address": "Mont-de-Marsan", "latitude": 43.89, "longitude": -0.50,
                "status": "active", "fingerprint": "fingerprint-old",
                "first_seen_at": "2026-08-01T10:00:00+00:00", "last_seen_at": "2026-08-01T10:00:00+00:00",
                "source_urls": ["https://example.org/old"],
            }]}), encoding="utf-8")
            database = sqlite3.connect(":memory:")
            database.executescript(SCHEMA)
            self.assertEqual(1, hydrate_previous_feed(database, feed))
            self.assertEqual(1, mark_unverified(database, "2026-08-06T10:00:00+00:00"))
            self.assertEqual("unverified", database.execute("SELECT status FROM events").fetchone()[0])


if __name__ == "__main__":
    unittest.main()
