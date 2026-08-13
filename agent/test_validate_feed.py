import unittest
from datetime import datetime, timezone

from validate_feed import validate


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


if __name__ == "__main__":
    unittest.main()
