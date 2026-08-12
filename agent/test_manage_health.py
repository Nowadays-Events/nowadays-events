import unittest

from manage_health import degraded_sources, incident_id, issue_body


class ManageHealthTests(unittest.TestCase):
    def test_ignores_healthy_and_missing_credentials_sources(self):
        payload = {"source_reports": [
            {"name": "Officielle", "status": "ok"},
            {"name": "Optionnelle", "status": "credentials_missing"},
        ]}
        self.assertEqual([], degraded_sources(payload))

    def test_incident_is_stable_regardless_of_source_order(self):
        first = [{"name": "B"}, {"name": "A"}]
        second = list(reversed(first))
        self.assertEqual(incident_id(first), incident_id(second))

    def test_issue_body_contains_actionable_source_counts(self):
        sources = [{
            "name": "Tourisme Landes", "status": "degraded",
            "candidates": 0, "accepted_in_radius": 0, "failures": 1,
        }]
        body = issue_body(
            {
                "status": "degraded", "generated_at": "2026-08-12T10:00:00Z", "exported": 34,
                "failures": ["Tourisme Landes: minimum attendu 1"],
            },
            sources, "abc123",
        )
        self.assertIn("Tourisme Landes", body)
        self.assertIn("34", body)
        self.assertIn("xymis-health-incident:abc123", body)
        self.assertIn("minimum attendu 1", body)


if __name__ == "__main__":
    unittest.main()
