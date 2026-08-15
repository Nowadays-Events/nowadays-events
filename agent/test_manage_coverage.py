import unittest

from manage_coverage import MARKER, notification_body


class ManageCoverageTests(unittest.TestCase):
    def test_notification_is_explicit_and_requires_human_validation(self):
        body = notification_body({
            "required_areas": ["Dax", "Mimizan", "Biscarrosse"],
            "current_radius_km": 50,
            "target_radius_km": 100,
            "preview_events_outside_current_radius": 27,
        })
        self.assertIn("Dax, Mimizan, Biscarrosse", body)
        self.assertIn("50 km", body)
        self.assertIn("100 km", body)
        self.assertIn("27 événement(s)", body)
        self.assertIn("validation humaine", body)
        self.assertIn(MARKER, body)


if __name__ == "__main__":
    unittest.main()
