import unittest

from provider_sources import map_eventbrite, map_helloasso, map_openagenda


class ProviderSourceTests(unittest.TestCase):
    def test_maps_openagenda_occurrences_and_cancellation(self):
        result = map_openagenda({
            "title": {"fr": "Exposition"},
            "description": {"fr": "Entrée libre"},
            "slug": "exposition",
            "originAgenda": {"slug": "agenda-landes"},
            "status": 6,
            "timings": [
                {"begin": "2026-08-06T10:00:00+0200", "end": "2026-08-06T18:00:00+0200"},
                {"begin": "2026-08-07T10:00:00+0200", "end": "2026-08-07T18:00:00+0200"},
            ],
            "location": {"name": "Musée", "address": "Mont-de-Marsan", "latitude": 43.89, "longitude": -0.50},
        })
        self.assertEqual("Exposition", result["name"])
        self.assertEqual("2026-08-07T18:00:00+0200", result["endDate"])
        self.assertIn("EventCancelled", result["eventStatus"])

    def test_maps_helloasso_directory_form(self):
        result = map_helloasso({
            "title": "Course solidaire", "description": "Pour tous",
            "startDate": "2026-09-01T08:00:00+02:00", "endDate": "2026-09-01T12:00:00+02:00",
            "formSlug": "course", "organization": {"organizationSlug": "club"},
            "place": {"name": "Stade", "latitude": 43.89, "longitude": -0.50,
                      "address": {"address": "1 rue du Stade", "zipCode": "40000", "city": "Mont-de-Marsan"}},
        })
        self.assertEqual("Course solidaire", result["name"])
        self.assertIn("helloasso.com/associations/club/evenements/course", result["url"])

    def test_maps_eventbrite_expanded_venue(self):
        result = map_eventbrite({
            "name": {"text": "Concert"}, "summary": "Live", "status": "live",
            "url": "https://eventbrite.com/e/1",
            "start": {"utc": "2026-09-01T18:00:00Z"}, "end": {"utc": "2026-09-01T20:00:00Z"},
            "venue": {"name": "Salle", "address": {"localized_address_display": "Mont-de-Marsan",
                                                       "latitude": "43.89", "longitude": "-0.50"}},
        })
        self.assertEqual("Concert", result["name"])
        self.assertEqual(43.89, result["location"]["geo"]["latitude"])


if __name__ == "__main__":
    unittest.main()
