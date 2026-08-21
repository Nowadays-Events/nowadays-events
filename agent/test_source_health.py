import unittest

from source_health import assess_observation


class SourceHealthTests(unittest.TestCase):
    def setUp(self):
        self.source = {"name": "Agenda", "expect_events": True, "empty_warning_threshold": 3}

    def test_isolated_empty_collection_is_not_a_warning(self):
        result = assess_observation(self.source, 0)
        self.assertEqual("ok", result["status"])
        self.assertEqual(1, result["consecutive_empty_collections"])

    def test_repeated_empty_collections_become_a_warning(self):
        previous = {"consecutive_empty_collections": 2, "last_nonzero_candidates": 12}
        result = assess_observation(self.source, 0, previous)
        self.assertEqual("warning", result["status"])
        self.assertEqual("repeated_empty_collection", result["reason"])

    def test_return_to_normal_resets_empty_streak(self):
        result = assess_observation(self.source, 8, {"consecutive_empty_collections": 4})
        self.assertEqual("ok", result["status"])
        self.assertEqual(0, result["consecutive_empty_collections"])
        self.assertEqual(8, result["last_nonzero_candidates"])

    def test_normally_empty_source_does_not_warn(self):
        source = {"name": "Optional", "expect_events": False, "empty_warning_threshold": 2}
        result = assess_observation(source, 0, {"consecutive_empty_collections": 9})
        self.assertEqual("ok", result["status"])

    def test_clearly_abnormal_drop_warns_immediately_when_configured(self):
        source = {
            "name": "Large agenda", "abnormal_drop_ratio": 0.25,
            "abnormal_drop_min_reference": 20,
        }
        result = assess_observation(source, 4, {"last_nonzero_candidates": 40})
        self.assertEqual("warning", result["status"])
        self.assertEqual("abnormal_candidate_drop", result["reason"])


if __name__ == "__main__":
    unittest.main()
