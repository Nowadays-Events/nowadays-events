import unittest
from datetime import datetime, timezone

from validate_feed import geographic_settings, has_broken_encoding, validate


def event(identifier="one", start="2026-08-12T10:00:00+00:00", end="2026-08-12T12:00:00+00:00"):
    return {
        "external_id": identifier,
        "title": "Événement",
        "start_at": start,
        "end_at": end,
        "latitude": 43.89,
        "longitude": -0.50,
        "source_urls": ["https://example.org/event"],
    }


class ValidateFeedTests(unittest.TestCase):
    NOW = datetime(2026, 8, 12, tzinfo=timezone.utc)

    def test_accepts_valid_feed(self):
        self.assertEqual([], validate({"events": [event()]}, minimum_events=1, now=self.NOW))

    def test_reads_geographic_settings_from_single_config(self):
        config = {
            "center": {"latitude": 43.8904, "longitude": -0.5007},
            "radius_km": 50,
        }
        self.assertEqual(((43.8904, -0.5007), 50.0), geographic_settings(config))
        self.assertEqual(((44.0, -1.0), 90.0), geographic_settings(config, 44.0, -1.0, 90))

    def test_rejects_collapsed_feed(self):
        errors = validate({"events": []}, minimum_events=10, now=self.NOW)
        self.assertTrue(any("minimum attendu" in error for error in errors))

    def test_rejects_large_drop_compared_with_previous_feed(self):
        current = {"events": [event(str(index)) for index in range(12)]}
        previous = {"events": [event(str(index)) for index in range(30)]}
        errors = validate(
            current,
            minimum_events=10,
            now=self.NOW,
            previous_payload=previous,
        )
        self.assertTrue(any("Chute anormale" in error for error in errors))

    def test_accepts_normal_drop_compared_with_previous_feed(self):
        current = {"events": [event(str(index)) for index in range(18)]}
        previous = {"events": [event(str(index)) for index in range(30)]}
        self.assertEqual([], validate(
            current,
            minimum_events=10,
            now=self.NOW,
            previous_payload=previous,
        ))

    def test_rejects_duplicate_invalid_and_stale_entries(self):
        payload = {"events": [
            event("same", "2014-08-11T00:00:00Z", "2014-08-11T00:00:00Z"),
            {**event("same"), "latitude": 120, "source_urls": []},
        ]}
        errors = validate(payload, minimum_events=1, now=self.NOW)
        self.assertTrue(any("dupliqué" in error for error in errors))
        self.assertTrue(any("coordonnées hors limites" in error for error in errors))
        self.assertTrue(any("source manquante" in error for error in errors))
        self.assertTrue(any("plus de 30 jours" in error for error in errors))

    def test_rejects_end_before_start(self):
        errors = validate(
            {"events": [event(end="2026-08-11T12:00:00Z")]}, minimum_events=1, now=self.NOW,
        )
        self.assertTrue(any("fin antérieure" in error for error in errors))

    def test_rejects_broken_french_text_encoding(self):
        broken = {**event(), "title": "Biblioth�que vue sur mer"}
        errors = validate({"events": [broken]}, minimum_events=1, now=self.NOW)
        self.assertTrue(any("encodage illisible" in error for error in errors))
        self.assertTrue(has_broken_encoding("FÃªte locale"))
        self.assertFalse(has_broken_encoding("Fête locale à Biscarrosse"))

    def test_validates_next_recurring_occurrence(self):
        recurring = {
            **event(end="2026-09-30T18:00:00Z"),
            "occurrence_count": 8,
            "next_occurrence_at": "2026-08-15T10:00:00Z",
        }
        self.assertEqual([], validate({"events": [recurring]}, minimum_events=1, now=self.NOW))
        missing = {**recurring, "next_occurrence_at": None}
        stale = {**recurring, "next_occurrence_at": "2026-08-10T10:00:00Z"}
        after_end = {**recurring, "next_occurrence_at": "2026-10-01T10:00:00Z"}
        self.assertTrue(any("manquante ou invalide" in error for error in validate(
            {"events": [missing]}, minimum_events=1, now=self.NOW,
        )))
        self.assertTrue(any("déjà passée" in error for error in validate(
            {"events": [stale]}, minimum_events=1, now=self.NOW,
        )))
        self.assertTrue(any("postérieure à la fin" in error for error in validate(
            {"events": [after_end]}, minimum_events=1, now=self.NOW,
        )))

    def test_rejects_unsafe_or_malformed_source_links(self):
        unsafe = {**event(), "source_urls": ["javascript:alert(1)"]}
        malformed = {**event(), "source_urls": ["https:///sans-domaine"]}
        self.assertTrue(any("URL source non sûre" in error for error in validate(
            {"events": [unsafe]}, minimum_events=1, now=self.NOW,
        )))
        self.assertTrue(any("URL source non sûre" in error for error in validate(
            {"events": [malformed]}, minimum_events=1, now=self.NOW,
        )))

    def test_rejects_event_outside_configured_radius(self):
        paris = {**event(), "latitude": 48.8566, "longitude": 2.3522}
        errors = validate(
            {"events": [paris]}, minimum_events=1, now=self.NOW,
            center=(43.8904, -0.5007), radius_km=50,
        )
        self.assertTrue(any("hors du rayon de 50 km" in error for error in errors))
        self.assertEqual([], validate(
            {"events": [event()]}, minimum_events=1, now=self.NOW,
            center=(43.8904, -0.5007), radius_km=50,
        ))


if __name__ == "__main__":
    unittest.main()
