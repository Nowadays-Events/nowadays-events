"""Regression fixtures reduced from the Salon Habitat Tourinsoft markup."""
import html
import json
import unittest
import sqlite3
import tempfile
from datetime import datetime
from pathlib import Path

from nowadays_agent import SCHEMA, enrich_recurring_events, event_from_json, export_feed, normalize_export_schedule, persist, should_export_event
from validate_feed import validate


class TourinsoftPeriodTests(unittest.TestCase):
    def extract(self, reference, start="2026-10-02T00:00:00+02:00",
                end="2026-10-04T23:59:59+02:00", extra=None):
        period = {"type": "09.01.05", "startDate": start, "endDate": end,
                  "_isOneDay": False, **(extra or {})}
        base = event_from_json({
            "@type": "Event", "name": "Manifestation", "startDate": start,
            "endDate": end, "location": {"name": "Hall", "geo": {
                "latitude": 43.8913973, "longitude": -0.4744134}},
        }, "Tourinsoft", "https://example.org/event")
        helper = [{"date": start[:10], "day": "09.02.06", "schedules": []}]
        # The source repeats its authoritative period in three responsive widgets.
        body = '<script>{"timezone":"Europe/Paris"}</script>' + "".join(
            f"<li periods='{html.escape(json.dumps([period]), quote=True)}'></li>"
            for _ in range(3)
        ) + f"<i periods='{json.dumps(helper)}'></i>"
        event = enrich_recurring_events([base], body, datetime.fromisoformat(reference))[0]
        return event, normalize_export_schedule(event.__dict__, datetime.fromisoformat(reference))

    def assert_visible(self, reference, visible):
        event, item = self.extract(reference)
        self.assertEqual("continuous", event.schedule_type)
        self.assertEqual("tourinsoft_continuous_period", event.schedule_reason)
        self.assertEqual("2026-10-01T22:00:00+00:00", event.start_at)
        self.assertEqual("2026-10-04T21:59:59+00:00", event.end_at)
        self.assertEqual("date_only", event.time_precision)
        self.assertEqual((), event.occurrence_starts)
        self.assertIsNone(event.next_occurrence_at)
        self.assertEqual(visible, should_export_event(item, datetime.fromisoformat(reference)))

    def test_salon_period_in_progress(self):
        self.assert_visible("2026-10-04T12:00:00+02:00", True)

    def test_salon_period_future(self):
        self.assert_visible("2026-10-01T12:00:00+02:00", True)

    def test_salon_period_past(self):
        self.assert_visible("2026-10-05T12:00:00+02:00", False)

    def test_salon_inclusive_last_second(self):
        self.assert_visible("2026-10-04T23:59:59+02:00", True)

    def test_salon_midnight_paris_not_utc(self):
        self.assert_visible("2026-10-05T00:00:00+02:00", False)

    def test_continuous_exact_end_is_not_extended(self):
        event, item = self.extract("2026-10-04T18:00:01+02:00",
                                   end="2026-10-04T18:00:00+02:00")
        self.assertEqual("continuous", event.schedule_type)
        self.assertEqual("exact", event.time_precision)
        self.assertFalse(should_export_event(item, datetime.fromisoformat("2026-10-04T18:00:01+02:00")))

    def test_period_across_paris_dst_uses_each_bound_offset(self):
        event, _ = self.extract("2026-10-25T12:00:00+01:00",
                                start="2026-10-24T00:00:00+02:00",
                                end="2026-10-26T23:59:59+01:00")
        self.assertEqual("continuous", event.schedule_type)
        self.assertEqual("2026-10-23T22:00:00+00:00", event.start_at)
        self.assertEqual("2026-10-26T22:59:59+00:00", event.end_at)

    def test_naive_period_bounds_use_paris_not_host_timezone(self):
        event, _ = self.extract("2026-10-04T12:00:00+02:00",
                                start="2026-10-02T00:00:00",
                                end="2026-10-04T23:59:59")
        self.assertEqual("2026-10-01T22:00:00+00:00", event.start_at)
        self.assertEqual("2026-10-04T21:59:59+00:00", event.end_at)

    def test_weekly_rule_is_not_reclassified_as_continuous(self):
        event, _ = self.extract("2026-10-01T12:00:00+02:00", extra={
            "_formated_days": [{"day": "09.02.06", "schedules": [{"startTime": "18:00:00"}]}],
            "_isMultipleOneDays": True,
        })
        self.assertEqual("recurring", event.schedule_type)
        self.assertEqual("tourinsoft_weekly_rule", event.schedule_reason)
        self.assertIn("2026-10-02T16:00:00+00:00", event.occurrence_starts)

    def test_exact_single_event_has_no_technical_day_extension(self):
        event = event_from_json({"name": "Ponctuel", "startDate": "2026-10-04T14:00:00+02:00",
                                 "endDate": "2026-10-04T15:00:00+02:00", "location": {
                                     "geo": {"latitude": 43.89, "longitude": -0.5}}}, "Test", "https://example.org/single")
        self.assertEqual("single", event.schedule_type)
        for reference, visible in [("2026-10-04T14:30:00+02:00", True),
                                   ("2026-10-04T15:00:01+02:00", False)]:
            clock = datetime.fromisoformat(reference)
            self.assertEqual(visible, should_export_event(normalize_export_schedule(event.__dict__, clock), clock))

    def test_explicit_existing_recurrence_is_preserved(self):
        base = event_from_json({"name": "Séances", "startDate": "2026-10-02",
                                "endDate": "2026-10-04", "scheduleType": "recurring",
                                "occurrenceDates": ["2026-10-02T14:00:00+02:00", "2026-10-04T14:00:00+02:00"],
                                "location": {"geo": {"latitude": 43.89, "longitude": -0.5}}},
                               "Test", "https://example.org/sessions")
        periods = [{"startDate": "2026-10-02T00:00:00+02:00", "endDate": "2026-10-04T23:59:59+02:00"}]
        clock = datetime.fromisoformat("2026-10-03T12:00:00+02:00")
        event = enrich_recurring_events([base], f"<i periods='{json.dumps(periods)}'></i>", clock)[0]
        self.assertEqual(base, event)
        self.assertEqual("2026-10-04T12:00:00+00:00", normalize_export_schedule(event.__dict__, clock)["next_occurrence_at"])

    def test_distinct_one_day_periods_remain_recurring(self):
        base, _ = self.extract("2026-10-01T12:00:00+02:00")
        periods = [{"startDate": f"2026-10-{day}T00:00:00+02:00",
                    "endDate": f"2026-10-{day}T23:59:59+02:00", "_isOneDay": True}
                   for day in ("02", "04")]
        event = enrich_recurring_events([base], f"<i periods='{json.dumps(periods)}'></i>",
                                       datetime.fromisoformat("2026-10-03T12:00:00+02:00"))[0]
        self.assertEqual("recurring", event.schedule_type)
        self.assertEqual(2, event.occurrence_count)
        self.assertEqual("2026-10-03T22:00:00+00:00", event.next_occurrence_at)
        self.assertEqual("date_only", event.time_precision)

    def test_exact_midnight_end_is_not_extended(self):
        event, item = self.extract("2026-10-04T00:00:01+02:00",
                                   start="2026-10-02T18:00:00+02:00", end="2026-10-04T00:00:00+02:00")
        self.assertEqual("exact", event.time_precision)
        self.assertEqual(event.end_at, item["end_at"])
        self.assertFalse(should_export_event(item, datetime.fromisoformat("2026-10-04T00:00:01+02:00")))

    def test_sqlite_export_preserves_period_and_legacy_fields(self):
        clock = datetime.fromisoformat("2026-10-04T12:00:00+02:00")
        event, _ = self.extract(clock.isoformat())
        with sqlite3.connect(":memory:") as connection, tempfile.TemporaryDirectory() as folder:
            connection.executescript(SCHEMA)
            persist(connection, [event], clock.isoformat())
            output = Path(folder) / "events.json"
            self.assertEqual(1, export_feed(connection, output, clock))
            payload = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual([], validate(payload, minimum_events=1, now=clock))
            item = payload["events"][0]
            for field in ("external_id", "start_at", "end_at", "source_urls", "occurrence_count"):
                self.assertIn(field, item)
            self.assertEqual([event.source_url], item["source_urls"])
            self.assertEqual(event.external_id, item["external_id"])
            self.assertEqual("continuous", item["schedule_type"])
            self.assertEqual(event.end_at, item["end_at"])
            self.assertEqual(0, export_feed(connection, output, datetime.fromisoformat("2026-10-05T00:00:00+02:00")))


if __name__ == "__main__":
    unittest.main()
