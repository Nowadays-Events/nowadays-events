import json
import sqlite3
import tempfile
import unittest
from dataclasses import replace
from datetime import datetime
from pathlib import Path
from urllib.error import URLError

from nowadays_agent import SCHEMA, collection_status, coverage_readiness, detail_links, distance_km, enrich_recurring_events, event_from_curated, export_candidates, export_feed, extract_armagnac_event, extract_biscarrosse_event, extract_dax_events, extract_events, hydrate_previous_feed, is_transient_network_error, likely_duplicate, listing_page_url, mark_unverified, merge_event_status, persist, should_export_event


class NowadaysAgentTests(unittest.TestCase):
    def test_collection_status_distinguishes_invalid_from_missing_credentials(self):
        missing = [{"status": "credentials_missing"}]
        invalid = [{"status": "credentials_invalid"}]
        self.assertEqual("ok", collection_status([], [], missing))
        self.assertEqual("attention", collection_status([], [], invalid))
        self.assertEqual("partial", collection_status([], ["timeout"], invalid))
        self.assertEqual("degraded", collection_status(["parsing failed"], [], invalid))

    def test_coverage_readiness_requires_healthy_sources_for_all_target_areas(self):
        config = {
            "radius_km": 50,
            "expansion_plan": {"target_radius_km": 100, "required_areas": ["Dax", "Mimizan"]},
            "sources": [
                {"name": "Côte sud", "coverage_areas": ["Dax"]},
                {"name": "Côte nord", "coverage_areas": ["Mimizan"]},
            ],
        }
        reports = [
            {"name": "Côte sud", "status": "ok", "reachable": True,
             "accepted_in_target_radius": 4, "preview_outside_current_radius": 2},
            {"name": "Côte nord", "status": "credentials_invalid", "reachable": False},
        ]
        blocked = coverage_readiness(config, reports)
        self.assertFalse(blocked["expansion_ready"])
        self.assertEqual(["Mimizan"], blocked["missing_areas"])
        reports[1] = {"name": "Côte nord", "status": "ok", "reachable": True,
                      "accepted_in_target_radius": 3, "preview_outside_current_radius": 3}
        ready = coverage_readiness(config, reports)
        self.assertTrue(ready["expansion_ready"])
        self.assertEqual(5, ready["preview_events_outside_current_radius"])

    def test_coverage_readiness_ignores_unrelated_optional_source_failures(self):
        config = {
            "radius_km": 50,
            "expansion_plan": {"target_radius_km": 100, "required_areas": ["Biscarrosse"]},
            "sources": [
                {"name": "API optionnelle"},
                {"name": "Ville de Biscarrosse", "coverage_areas": ["Biscarrosse"]},
            ],
        }
        reports = [
            {"name": "API optionnelle", "status": "credentials_invalid", "reachable": False},
            {"name": "Ville de Biscarrosse", "status": "ok", "reachable": True,
             "accepted_in_target_radius": 12, "preview_outside_current_radius": 12},
        ]
        readiness = coverage_readiness(config, reports)
        self.assertTrue(readiness["expansion_ready"])
        self.assertEqual([], readiness["blocked_sources"])

    def test_coverage_readiness_requires_real_events_in_target_radius(self):
        config = {
            "radius_km": 50,
            "expansion_plan": {"target_radius_km": 100, "required_areas": ["Biscarrosse"]},
            "sources": [{"name": "Ville de Biscarrosse", "coverage_areas": ["Biscarrosse"]}],
        }
        reports = [{"name": "Ville de Biscarrosse", "status": "ok", "reachable": True,
                    "accepted_in_target_radius": 0, "preview_outside_current_radius": 0}]
        readiness = coverage_readiness(config, reports)
        self.assertFalse(readiness["expansion_ready"])
        self.assertEqual(["Biscarrosse"], readiness["missing_areas"])

    def test_timeout_is_a_transient_network_error(self):
        self.assertTrue(is_transient_network_error(TimeoutError("timed out")))
        self.assertTrue(is_transient_network_error(URLError(TimeoutError("timed out"))))
        self.assertFalse(is_transient_network_error(ValueError("invalid payload")))

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

    def test_enriches_tourinsoft_weekly_recurrence(self):
        base = event_from_curated({
            "@type": "Event", "name": "Marché", "startDate": "2026-01-01T00:00:00Z",
            "endDate": "2026-12-31T23:59:59Z", "url": "https://example.org/marche",
            "location": {"name": "Place", "geo": {"latitude": 43.89, "longitude": -0.50}},
        })
        periods = [{
            "startDate": "2026-01-01T00:00:00+00:00", "endDate": "2026-08-31T23:59:59+00:00",
            "days": [{"days": [
                {"day": "09.02.03", "schedules": [{"startTime": "06:00:00"}]},
                {"day": "09.02.07", "schedules": [{"startTime": "06:00:00"}]},
            ]}],
        }]
        body = f"<li periods='{json.dumps(periods)}'></li>"
        enriched = enrich_recurring_events(
            [base], body, datetime.fromisoformat("2026-08-13T00:00:00+00:00"),
        )[0]
        self.assertEqual("2026-08-15T06:00:00+00:00", enriched.next_occurrence_at)
        self.assertGreater(enriched.occurrence_count, 1)

    def test_uses_explicit_date_for_irregular_tourinsoft_schedule(self):
        base = event_from_curated({
            "@type": "Event", "name": "Visite", "startDate": "2026-08-03T00:00:00Z",
            "endDate": "2026-08-31T23:59:59Z", "url": "https://example.org/visite",
            "location": {"name": "Office", "geo": {"latitude": 43.89, "longitude": -0.50}},
        })
        broad = [{
            "startDate": "2026-08-03T00:00:00+02:00", "endDate": "2026-08-31T23:59:59+02:00",
            "days": [{"days": [{"day": "09.02.08", "schedules": [{"startTime": "10:00:00"}]}]}],
        }]
        exact = [{"date": "2026-08-17", "schedules": [{"startTime": "10:00:00"}]}]
        body = f"<i periods='{json.dumps(broad)}'></i><i periods='{json.dumps(exact)}'></i>"
        enriched = enrich_recurring_events(
            [base], body, datetime.fromisoformat("2026-08-13T00:00:00+00:00"),
        )[0]
        self.assertEqual("2026-08-17T08:00:00+00:00", enriched.next_occurrence_at)
        self.assertGreater(enriched.occurrence_count, 1)

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
        self.assertNotIn("www.tourismelandes.com", armagnac_hosts)

    def test_cloudflare_blocked_tourisme_landes_is_not_a_direct_source(self):
        config_path = Path(__file__).with_name("config.json")
        config = json.loads(config_path.read_text(encoding="utf-8"))
        hosts = {
            source["url"].split("/")[2]
            for source in config["sources"] if source.get("url", "").startswith("http")
        }
        self.assertNotIn("www.tourismelandes.com", hosts)

    def test_mont_de_marsan_source_crawls_several_list_pages(self):
        config_path = Path(__file__).with_name("config.json")
        config = json.loads(config_path.read_text(encoding="utf-8"))
        source = next(item for item in config["sources"] if item["name"] == "Mont de Marsan Tourisme")
        self.assertGreaterEqual(source.get("list_pages", 1), 3)
        self.assertGreaterEqual(source.get("max_detail_pages", 0), 36)
        self.assertGreaterEqual(source.get("min_candidates", 0), 10)

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

    def test_armagnac_single_date_ignores_footer_year(self):
        body = '''
        <h1>Marché gourmand</h1>
        <img src="map?center=43.96979+-0.18539&amp;zoom=11">
        <div class="detailManifDates">
          <span class="manif-date-day">mardi</span>
          <span class="manif-date-day-num">11</span>
          <span class="manif-date-month">août</span>
        </div>
        <tr class="address"><td>Adresse</td><td>40240 Labastide-d'Armagnac</td></tr>
        <footer>Office créé en 2014</footer>
        '''
        event = extract_armagnac_event(body, "Landes d'Armagnac", "https://example.org/agenda/2")
        self.assertIsNotNone(event)
        self.assertEqual(f"{datetime.now().year}-08-11T00:00:00+00:00", event.start_at)

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

    def test_detail_links_support_source_specific_offer_paths(self):
        body = '''
        <a href="/offres/concert-mimizan-fr-123/">Concert</a>
        <a href="/hebergements/hotel/">Hôtel</a>
        '''
        self.assertEqual(
            ["https://example.org/offres/concert-mimizan-fr-123"],
            detail_links(body, "https://example.org/agenda/", 10, ["/offres/"]),
        )

    def test_mimizan_official_source_is_prepared_without_expanding_radius(self):
        config = json.loads((Path(__file__).parent / "config.json").read_text(encoding="utf-8"))
        source = next(item for item in config["sources"] if item["name"].startswith("Mimizan Tourisme"))
        self.assertEqual(50, config["radius_km"])
        self.assertEqual(["Mimizan"], source["coverage_areas"])
        self.assertIn("/offres/", source["detail_path_tokens"])
        self.assertGreaterEqual(source["min_candidates"], 10)

    def test_extracts_embedded_grand_dax_tourinsoft_event(self):
        body = '''<script>var objectsSit = [{"_source":{
          "sit_id":"FMA040TEST", "nom":{"fr":"Concert annulé"},
          "descriptifLong":{"fr":"Concert du soir"},
          "localisation":{"geoJson":{"lat":"43.7100","lon":"-1.0500"},
            "adresse1":"Arènes", "cp":"40100", "description":"Arènes de Dax",
            "commune":{"nom":{"fr":"DAX"}}},
          "informations":{"periode":[{"debut":"2026-08-20","fin":"2026-08-20",
            "horaire":{"jeudi":["20:00","22:00"]}}],
            "tarifs":[{"min":12.5}]},
          "caracteristiques":[{"values":{"fr":"Concert"}}]
        }}];</script>'''
        events = extract_dax_events(body, "Grand Dax", "https://dax.example/agenda")
        self.assertEqual(1, len(events))
        event = events[0]
        self.assertEqual("Concert annulé", event.title)
        self.assertEqual("cancelled", event.status)
        self.assertEqual((43.71, -1.05), (event.latitude, event.longitude))
        self.assertEqual("paid", event.price_type)
        self.assertEqual(1250, event.price_cents)
        self.assertIn("DAX", event.address)

    def test_grand_dax_source_is_marked_as_dax_coverage(self):
        config = json.loads((Path(__file__).parent / "config.json").read_text(encoding="utf-8"))
        source = next(item for item in config["sources"] if item["name"].startswith("Grand Dax"))
        self.assertEqual("dax_embedded", source["type"])
        self.assertEqual(["Dax"], source["coverage_areas"])

    def test_extracts_biscarrosse_event_with_precise_map_coordinates(self):
        body = '''
        <meta name="description" content="Le 29/08/2026 De 10:00 à 16:00 Arcanson" />
        <h1 class="cover__title">Forum des associations</h1>
        <p class="cover__text cover__intro">Rencontrez les associations locales.</p>
        <h2 class="date-event__title">Le samedi 29 août 2026</h2>
        <p class="listing__location"><strong>Arcanson</strong>
          61 Rue du Lt de Vaisseau Paris<br>40600 Biscarrosse</p>
        <div class="map" data-lat="44.3965988" data-long="-1.1664309"></div>
        <p>Tarif(s) Gratuit</p>
        '''
        event = extract_biscarrosse_event(body, "Ville de Biscarrosse", "https://example.org/agenda/forum/")
        self.assertIsNotNone(event)
        self.assertEqual("Forum des associations", event.title)
        self.assertEqual((44.3965988, -1.1664309), (event.latitude, event.longitude))
        self.assertEqual("Arcanson", event.venue)
        self.assertIn("61 Rue", event.address)
        self.assertEqual("2026-08-29T08:00:00+00:00", event.start_at)
        self.assertEqual("free", event.price_type)

    def test_biscarrosse_source_completes_coastal_coverage_without_expanding_radius(self):
        config = json.loads((Path(__file__).parent / "config.json").read_text(encoding="utf-8"))
        source = next(item for item in config["sources"] if item["name"].startswith("Ville de Biscarrosse"))
        self.assertEqual(50, config["radius_km"])
        self.assertEqual("biscarrosse_html", source["type"])
        self.assertEqual(["Biscarrosse"], source["coverage_areas"])
        self.assertEqual(2, source["list_pages"])
        self.assertEqual(
            "https://www.ville-biscarrosse.fr/systeme/agenda/page/2/",
            listing_page_url(source, 2),
        )
        self.assertGreaterEqual(source["min_candidates"], 20)

    def test_default_listing_page_url_keeps_existing_query_pagination(self):
        source = {"url": "https://example.org/agenda/?category=music"}
        self.assertEqual(
            "https://example.org/agenda/?category=music&listpage=3",
            listing_page_url(source, 3),
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
            self.assertEqual(1, export_feed(database, output, datetime.fromisoformat("2026-08-01T00:00:00+00:00")))
            self.assertEqual(2, len(json.loads(output.read_text())["events"][0]["source_urls"]))

    def test_persist_merges_similar_parent_titles_from_two_sources(self):
        first_html = '''<script type="application/ld+json">{
          "@type":"Event", "name":"Fêtes de la Madeleine",
          "startDate":"2026-07-22", "endDate":"2026-07-26",
          "location":{"name":"Mont-de-Marsan","geo":{"latitude":43.8904,"longitude":-0.5007}}
        }</script>'''
        second_html = first_html.replace(
            'Fêtes de la Madeleine', 'Madeleine 2026 : le programme complet des fêtes'
        ).replace('43.8904', '43.8910')
        first = extract_events(first_html, "A", "https://a.example/madeleine")[0]
        second = extract_events(second_html, "B", "https://b.example/madeleine")[0]
        database = sqlite3.connect(":memory:")
        database.executescript(SCHEMA)
        persist(database, [first, second], "2026-07-01T10:00:00+00:00")
        self.assertEqual(1, database.execute("SELECT COUNT(*) FROM events").fetchone()[0])
        self.assertEqual(2, database.execute("SELECT COUNT(*) FROM event_sources").fetchone()[0])

    def test_specific_sub_event_is_not_merged_with_parent(self):
        parent_html = '''<script type="application/ld+json">{
          "@type":"Event", "name":"Fêtes de la Madeleine",
          "startDate":"2026-07-22", "endDate":"2026-07-26",
          "location":{"geo":{"latitude":43.8904,"longitude":-0.5007}}
        }</script>'''
        child_html = parent_html.replace(
            'Fêtes de la Madeleine', 'Journée des bandas des Fêtes de la Madeleine'
        )
        parent = extract_events(parent_html, "A", "https://a.example/parent")[0]
        child = extract_events(child_html, "A", "https://a.example/child")[0]
        self.assertFalse(likely_duplicate(
            child, parent.title, parent.start_at, parent.end_at, parent.latitude, parent.longitude,
        ))

    def test_cancelled_status_wins_multi_source_merge_in_both_orders(self):
        self.assertEqual("cancelled", merge_event_status("active", "cancelled"))
        self.assertEqual("cancelled", merge_event_status("cancelled", "active"))
        self.assertEqual("postponed", merge_event_status("active", "postponed"))
        html = '''<script type="application/ld+json">{
          "@type":"Event", "name":"Concert du soir", "startDate":"2026-08-20T20:00:00+02:00",
          "location":{"name":"Arènes","geo":{"latitude":43.89,"longitude":-0.50}}
        }</script>'''
        active = extract_events(html, "A", "https://a.example/concert")[0]
        cancelled = replace(active, source_url="https://b.example/concert", status="cancelled")
        for ordered in ([active, cancelled], [cancelled, active]):
            database = sqlite3.connect(":memory:")
            database.executescript(SCHEMA)
            persist(database, ordered, "2026-08-01T10:00:00+00:00")
            self.assertEqual(
                "cancelled", database.execute("SELECT status FROM events").fetchone()[0],
            )

    def test_public_feed_hides_expired_active_but_keeps_recent_cancelled(self):
        now = datetime.fromisoformat("2026-08-13T12:00:00+00:00")
        expired = {"end_at": "2026-08-12T23:59:00+00:00", "status": "active"}
        cancelled = {"end_at": "2026-08-05T12:00:00+00:00", "status": "cancelled"}
        recurring = {
            "end_at": "2026-08-01T12:00:00+00:00", "status": "active",
            "next_occurrence_at": "2026-08-15T10:00:00+00:00",
        }
        self.assertFalse(should_export_event(expired, now))
        self.assertTrue(should_export_event(cancelled, now))
        self.assertTrue(should_export_event(recurring, now))

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

    def test_previous_feed_drops_obviously_stale_events(self):
        with tempfile.TemporaryDirectory() as directory:
            feed = Path(directory) / "previous.json"
            feed.write_text(json.dumps({"events": [{
                "external_id": "bad-year", "title": "Date mal interprétée", "description": "",
                "start_at": "2014-08-11T00:00:00+00:00", "end_at": "2014-08-11T00:00:00+00:00",
                "venue": "", "address": "Roquefort", "latitude": 44.03, "longitude": -0.32,
                "status": "unverified", "fingerprint": "old-fingerprint",
                "source_urls": ["https://example.org/old"],
            }]}), encoding="utf-8")
            database = sqlite3.connect(":memory:")
            database.executescript(SCHEMA)
            self.assertEqual(0, hydrate_previous_feed(database, feed))
            self.assertEqual(0, database.execute("SELECT COUNT(*) FROM events").fetchone()[0])


if __name__ == "__main__":
    unittest.main()
