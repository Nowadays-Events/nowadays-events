import base64
import json
import tempfile
import unittest
from pathlib import Path

from review_candidate import approve, extract_candidate


class ReviewCandidateTests(unittest.TestCase):
    def candidate_body(self, candidate):
        encoded = base64.b64encode(
            json.dumps(candidate).encode("utf-8")
        ).decode("ascii")
        return f"<!-- nowadays-candidate-json:{encoded} -->"

    def test_extracts_strict_candidate(self):
        candidate = {
            "name": "Concert",
            "startDate": "2026-08-10T20:00:00+02:00",
            "url": "https://example.org/concert",
            "location": {"geo": {"latitude": 43.89, "longitude": -0.50}},
            "review_status": "pending",
            "missing_fields": [],
        }
        result = extract_candidate(self.candidate_body(candidate))
        self.assertNotIn("review_status", result)
        self.assertNotIn("missing_fields", result)

    def test_approval_is_idempotent_by_source_url(self):
        candidate = {
            "name": "Concert",
            "startDate": "2026-08-10T20:00:00+02:00",
            "url": "https://example.org/concert",
            "location": {"geo": {"latitude": 43.89, "longitude": -0.50}},
        }
        with tempfile.TemporaryDirectory() as directory:
            config = Path(directory) / "config.json"
            config.write_text('{"curated_events": []}', encoding="utf-8")
            body = self.candidate_body(candidate)
            approve(config, body)
            approve(config, body)
            result = json.loads(config.read_text(encoding="utf-8"))
            self.assertEqual(1, len(result["curated_events"]))

    def test_rejects_candidate_without_coordinates(self):
        candidate = {
            "name": "Concert",
            "startDate": "2026-08-10T20:00:00+02:00",
            "url": "https://example.org/concert",
            "location": {"name": "Mont-de-Marsan"},
        }
        with self.assertRaises(ValueError):
            extract_candidate(self.candidate_body(candidate))


if __name__ == "__main__":
    unittest.main()
