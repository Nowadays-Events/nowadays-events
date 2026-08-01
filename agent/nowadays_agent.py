#!/usr/bin/env python3
"""Collecteur local Nowadays, sans dépendance externe.

Il extrait les objets schema.org/Event JSON-LD, conserve leur historique dans
SQLite, rapproche les doublons et exporte un flux JSON consommable par une API
ou, ultérieurement, directement par l'application Android.
"""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import math
import re
import sqlite3
import sys
import time
import unicodedata
import urllib.request
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from html.parser import HTMLParser
from pathlib import Path
from typing import Any, Iterable
from urllib.parse import urlsplit, urlunsplit
from urllib.parse import urljoin

USER_AGENT = "NowadaysEventAgent/0.1 (+local prototype)"
CANCELLED_TOKENS = ("annulé", "annule", "cancelled", "canceled", "reporté", "reporte")


class JsonLdParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.in_json_ld = False
        self.current: list[str] = []
        self.blocks: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = dict(attrs)
        self.in_json_ld = tag.lower() == "script" and values.get("type", "").lower() == "application/ld+json"
        if self.in_json_ld:
            self.current = []

    def handle_data(self, data: str) -> None:
        if self.in_json_ld:
            self.current.append(data)

    def handle_endtag(self, tag: str) -> None:
        if tag.lower() == "script" and self.in_json_ld:
            self.blocks.append("".join(self.current))
            self.in_json_ld = False


class LinkParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.links: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag.lower() == "a":
            href = dict(attrs).get("href")
            if href:
                self.links.append(href)


@dataclass(frozen=True)
class Event:
    external_id: str
    title: str
    description: str
    start_at: str
    end_at: str
    venue: str
    address: str
    latitude: float
    longitude: float
    source_url: str
    source_name: str
    status: str
    fingerprint: str


def normalize(value: str) -> str:
    value = unicodedata.normalize("NFKD", value)
    value = "".join(char for char in value if not unicodedata.combining(char))
    return re.sub(r"[^a-z0-9]+", " ", value.lower()).strip()


def canonical_url(value: str) -> str:
    parts = urlsplit(value.strip())
    return urlunsplit((parts.scheme.lower(), parts.netloc.lower(), parts.path.rstrip("/"), "", ""))


def first_text(value: Any) -> str:
    if isinstance(value, str):
        return value
    if isinstance(value, list) and value:
        return first_text(value[0])
    if isinstance(value, dict):
        return first_text(value.get("url") or value.get("name") or "")
    return ""


def walk_events(node: Any) -> Iterable[dict[str, Any]]:
    if isinstance(node, list):
        for item in node:
            yield from walk_events(item)
    elif isinstance(node, dict):
        raw_type = node.get("@type")
        types = raw_type if isinstance(raw_type, list) else [raw_type]
        if "Event" in types:
            yield node
        for value in node.values():
            yield from walk_events(value)


def address_text(node: Any) -> str:
    if isinstance(node, str):
        return node
    if not isinstance(node, dict):
        return ""
    return ", ".join(
        str(node.get(key, "")).strip()
        for key in ("streetAddress", "postalCode", "addressLocality", "addressCountry")
        if str(node.get(key, "")).strip()
    )


def parse_float(value: Any) -> float | None:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def iso_datetime(value: Any) -> str:
    raw = str(value or "").strip()
    if not raw:
        return ""
    candidate = raw.replace("Z", "+00:00")
    try:
        parsed = datetime.fromisoformat(candidate)
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=timezone.utc)
        return parsed.astimezone(timezone.utc).isoformat()
    except ValueError:
        return raw


def distance_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    radius = 6371.0
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    d_phi = math.radians(lat2 - lat1)
    d_lambda = math.radians(lon2 - lon1)
    a = math.sin(d_phi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(d_lambda / 2) ** 2
    return 2 * radius * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def event_from_json(node: dict[str, Any], source_name: str, page_url: str) -> Event | None:
    location = node.get("location") if isinstance(node.get("location"), dict) else {}
    geo = location.get("geo") if isinstance(location.get("geo"), dict) else {}
    latitude = parse_float(geo.get("latitude"))
    longitude = parse_float(geo.get("longitude"))
    title = html.unescape(first_text(node.get("name"))).strip()
    start_at = iso_datetime(node.get("startDate"))
    if not title or not start_at or latitude is None or longitude is None:
        return None
    end_at = iso_datetime(node.get("endDate")) or start_at
    event_url = canonical_url(first_text(node.get("url")) or page_url)
    status_value = normalize(first_text(node.get("eventStatus")))
    combined = normalize(" ".join((title, first_text(node.get("description")), status_value)))
    status = "cancelled" if "cancel" in status_value or any(token in combined for token in CANCELLED_TOKENS) else "active"
    venue = html.unescape(first_text(location.get("name"))).strip()
    address = html.unescape(address_text(location.get("address"))).strip()
    fingerprint_source = "|".join((normalize(title), start_at[:10], normalize(venue or address)))
    fingerprint = hashlib.sha256(fingerprint_source.encode("utf-8")).hexdigest()[:24]
    external_id = hashlib.sha256(f"{event_url}|{fingerprint}".encode("utf-8")).hexdigest()[:32]
    return Event(
        external_id=external_id,
        title=title,
        description=html.unescape(re.sub("<[^>]+>", " ", first_text(node.get("description")))).strip(),
        start_at=start_at,
        end_at=end_at,
        venue=venue,
        address=address,
        latitude=latitude,
        longitude=longitude,
        source_url=event_url,
        source_name=source_name,
        status=status,
        fingerprint=fingerprint,
    )


def event_from_curated(node: dict[str, Any]) -> Event | None:
    """Convertit une fiche validée manuellement avec le même contrat schema.org."""
    return event_from_json(
        node,
        str(node.get("source_name") or "Source validée"),
        str(node.get("url") or ""),
    )


def extract_events(body: str, source_name: str, page_url: str) -> list[Event]:
    parser = JsonLdParser()
    parser.feed(body)
    found: list[Event] = []
    for block in parser.blocks:
        try:
            payload = json.loads(block)
        except json.JSONDecodeError:
            continue
        for node in walk_events(payload):
            event = event_from_json(node, source_name, page_url)
            if event:
                found.append(event)
    unique: dict[str, Event] = {}
    for event in found:
        unique[event.external_id] = event
    return list(unique.values())


def fetch(url: str, timeout: int = 20) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "text/html"})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        charset = response.headers.get_content_charset() or "utf-8"
        content = response.read(4_000_000)
        try:
            return content.decode("utf-8")
        except UnicodeDecodeError:
            return content.decode(charset, errors="replace")


def detail_links(body: str, base_url: str, limit: int) -> list[str]:
    parser = LinkParser()
    parser.feed(body)
    base = urlsplit(base_url)
    accepted: list[str] = []
    for raw in parser.links:
        url = canonical_url(urljoin(base_url, html.unescape(raw)))
        parts = urlsplit(url)
        if parts.netloc != base.netloc or url == canonical_url(base_url):
            continue
        path = parts.path.lower()
        if "/agenda/" not in path and "/agenda-" not in path:
            continue
        if url not in accepted:
            accepted.append(url)
        if len(accepted) >= limit:
            break
    return accepted


SCHEMA = """
CREATE TABLE IF NOT EXISTS events (
    external_id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    start_at TEXT NOT NULL,
    end_at TEXT NOT NULL,
    venue TEXT NOT NULL,
    address TEXT NOT NULL,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    status TEXT NOT NULL,
    fingerprint TEXT NOT NULL,
    first_seen_at TEXT NOT NULL,
    last_seen_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS event_sources (
    external_id TEXT NOT NULL,
    source_url TEXT NOT NULL,
    source_name TEXT NOT NULL,
    last_seen_at TEXT NOT NULL,
    PRIMARY KEY (external_id, source_url)
);
CREATE INDEX IF NOT EXISTS idx_events_fingerprint ON events(fingerprint);
"""


def persist(connection: sqlite3.Connection, events: Iterable[Event], now: str) -> tuple[int, int]:
    inserted = updated = 0
    for event in events:
        existing = connection.execute(
            "SELECT external_id FROM events WHERE external_id=? OR fingerprint=? LIMIT 1",
            (event.external_id, event.fingerprint),
        ).fetchone()
        chosen_id = existing[0] if existing else event.external_id
        if existing:
            updated += 1
        else:
            inserted += 1
        connection.execute(
            """
            INSERT INTO events VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(external_id) DO UPDATE SET
              title=excluded.title, description=excluded.description,
              start_at=excluded.start_at, end_at=excluded.end_at,
              venue=excluded.venue, address=excluded.address,
              latitude=excluded.latitude, longitude=excluded.longitude,
              status=excluded.status, fingerprint=excluded.fingerprint,
              last_seen_at=excluded.last_seen_at
            """,
            (
                chosen_id, event.title, event.description, event.start_at, event.end_at,
                event.venue, event.address, event.latitude, event.longitude, event.status,
                event.fingerprint, now, now,
            ),
        )
        connection.execute(
            """
            INSERT INTO event_sources VALUES (?,?,?,?)
            ON CONFLICT(external_id,source_url) DO UPDATE SET last_seen_at=excluded.last_seen_at
            """,
            (chosen_id, event.source_url, event.source_name, now),
        )
    return inserted, updated


def export_feed(connection: sqlite3.Connection, output: Path) -> int:
    rows = connection.execute(
        """
        SELECT e.*, GROUP_CONCAT(s.source_url, char(10)) AS source_urls
        FROM events e JOIN event_sources s USING(external_id)
        GROUP BY e.external_id ORDER BY e.start_at
        """
    ).fetchall()
    columns = [column[0] for column in connection.execute(
        """
        SELECT e.*, GROUP_CONCAT(s.source_url, char(10)) AS source_urls
        FROM events e JOIN event_sources s USING(external_id) LIMIT 0
        """
    ).description]
    payload = []
    for row in rows:
        item = dict(zip(columns, row))
        item["source_urls"] = item["source_urls"].splitlines()
        payload.append(item)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({"generated_at": datetime.now(timezone.utc).isoformat(), "events": payload}, ensure_ascii=False, indent=2), encoding="utf-8")
    return len(payload)


def export_candidates(config: dict[str, Any], output: Path, now: str) -> int:
    """Exporte une file séparée qui ne peut jamais alimenter l'application directement."""
    candidates = []
    required = ("name", "startDate", "url")
    for item in config.get("candidate_events", []):
        missing = [field for field in required if not str(item.get(field) or "").strip()]
        candidates.append({
            **item,
            "review_status": "incomplete" if missing else "pending",
            "missing_fields": missing,
        })
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({
        "generated_at": now,
        "count": len(candidates),
        "candidates": candidates,
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    return len(candidates)


def run(
    config_path: Path,
    database_path: Path,
    output_path: Path,
    max_pages: int | None = None,
    max_runtime_seconds: int = 180,
) -> int:
    config = json.loads(config_path.read_text(encoding="utf-8"))
    center = config["center"]
    radius = float(config["radius_km"])
    collected: list[Event] = []
    failures: list[str] = []
    deadline = time.monotonic() + max_runtime_seconds
    source_reports: list[dict[str, Any]] = []
    sources = sorted(config["sources"], key=lambda item: int(item.get("priority", 0)), reverse=True)
    for source in sources:
        source_failure_count = 0
        source_candidate_count = 0
        source_accepted_count = 0
        source_reachable = False
        if time.monotonic() >= deadline:
            failures.append("Durée maximale atteinte avant la fin des sources")
            break
        try:
            if source.get("type", "jsonld") != "jsonld":
                raise ValueError(f"type de source non pris en charge: {source.get('type')}")
            body = fetch(source["url"], timeout=min(20, max(1, int(deadline - time.monotonic()))))
            source_reachable = True
            candidates = extract_events(body, source["name"], source["url"])
            page_limit = max_pages if max_pages is not None else int(source.get("max_detail_pages", 20))
            for detail_url in detail_links(body, source["url"], page_limit):
                if time.monotonic() >= deadline:
                    failures.append(f"{source['name']}: durée maximale atteinte")
                    break
                try:
                    timeout = min(20, max(1, int(deadline - time.monotonic())))
                    candidates.extend(extract_events(fetch(detail_url, timeout=timeout), source["name"], detail_url))
                except Exception as error:
                    source_failure_count += 1
                    failures.append(f"{source['name']} ({detail_url}): {error}")
            source_candidate_count = len(candidates)
            accepted = [event for event in candidates
                if distance_km(center["latitude"], center["longitude"], event.latitude, event.longitude) <= radius]
            source_accepted_count = len(accepted)
            collected.extend(accepted)
        except Exception as error:  # une source en panne ne bloque pas les autres
            source_failure_count += 1
            failures.append(f"{source['name']}: {error}")
        source_reports.append({
            "name": source["name"],
            "type": source.get("type", "jsonld"),
            "priority": int(source.get("priority", 0)),
            "trust": source.get("trust", "unknown"),
            "reachable": source_reachable,
            "candidates": source_candidate_count,
            "accepted_in_radius": source_accepted_count,
            "failures": source_failure_count,
            "status": "ok" if source_failure_count == 0 else "degraded",
        })
    for curated in config.get("curated_events", []):
        event = event_from_curated(curated)
        if event and distance_km(center["latitude"], center["longitude"], event.latitude, event.longitude) <= radius:
            collected.append(event)
    database_path.parent.mkdir(parents=True, exist_ok=True)
    now = datetime.now(timezone.utc).isoformat()
    with sqlite3.connect(database_path) as connection:
        connection.executescript(SCHEMA)
        inserted, updated = persist(connection, collected, now)
        exported = export_feed(connection, output_path)
    pending_candidates = export_candidates(config, output_path.with_name("candidates.json"), now)
    report = {
        "status": "ok" if not failures else "degraded",
        "generated_at": now,
        "sources": len(config["sources"]),
        "source_reports": source_reports,
        "curated_events": len(config.get("curated_events", [])),
        "pending_candidates": pending_candidates,
        "fetched_events": len(collected),
        "inserted": inserted,
        "updated": updated,
        "exported": exported,
        "failures": failures,
    }
    output_path.with_name("health.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8",
    )
    print(json.dumps(report, ensure_ascii=False))
    return 0 if any(item["reachable"] for item in source_reports) or bool(config.get("curated_events")) else 2


def main() -> int:
    parser = argparse.ArgumentParser()
    root = Path(__file__).resolve().parent
    parser.add_argument("--config", type=Path, default=root / "config.json")
    parser.add_argument("--database", type=Path, default=root / "data" / "agent.db")
    parser.add_argument("--output", type=Path, default=root / "output" / "events.json")
    parser.add_argument("--max-pages", type=int, default=None, help="plafond de pages de détail par source")
    parser.add_argument("--max-runtime-seconds", type=int, default=180)
    arguments = parser.parse_args()
    return run(
        arguments.config,
        arguments.database,
        arguments.output,
        arguments.max_pages,
        arguments.max_runtime_seconds,
    )


if __name__ == "__main__":
    sys.exit(main())
