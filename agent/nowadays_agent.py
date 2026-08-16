#!/usr/bin/env python3
"""Collecteur local Xymis Events, sans dépendance externe.

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
from concurrent.futures import ThreadPoolExecutor
from urllib.error import URLError
from dataclasses import asdict, dataclass, replace
from datetime import datetime, timedelta, timezone
from html.parser import HTMLParser
from pathlib import Path
from typing import Any, Callable, Iterable
from urllib.parse import urlencode, urlsplit, urlunsplit
from urllib.parse import urljoin
from zoneinfo import ZoneInfo

from provider_sources import InvalidCredentials, MissingCredentials, collect_api_source

USER_AGENT = "XymisEventsAgent/0.1 (+local prototype)"
CANCELLED_TOKENS = ("annulé", "annule", "cancelled", "canceled")
POSTPONED_TOKENS = ("reporté", "reporte", "postponed", "rescheduled")
TITLE_STOP_WORDS = {
    "a", "au", "aux", "d", "de", "des", "du", "en", "et", "la", "le", "les",
    "l", "programme", "complet", "agenda", "edition",
}


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
    category: str = "COMMUNITY"
    price_type: str = "unknown"
    price_cents: int | None = None
    currency: str = "EUR"
    occurrence_count: int = 1
    next_occurrence_at: str | None = None


def normalize(value: str) -> str:
    value = unicodedata.normalize("NFKD", value)
    value = "".join(char for char in value if not unicodedata.combining(char))
    return re.sub(r"[^a-z0-9]+", " ", value.lower()).strip()


def canonical_url(value: str) -> str:
    parts = urlsplit(value.strip())
    return urlunsplit((parts.scheme.lower(), parts.netloc.lower(), parts.path.rstrip("/"), "", ""))


def significant_title_tokens(value: str) -> set[str]:
    return {
        token.rstrip("s")
        for token in normalize(value).split()
        if token not in TITLE_STOP_WORDS and not re.fullmatch(r"20\d{2}", token)
    }


def likely_duplicate(
    event: Event,
    title: str,
    start_at: str,
    end_at: str,
    latitude: float,
    longitude: float,
) -> bool:
    if event.start_at[:10] != start_at[:10] or event.end_at[:10] != end_at[:10]:
        return False
    if distance_km(event.latitude, event.longitude, latitude, longitude) > 1.5:
        return False
    left = significant_title_tokens(event.title)
    right = significant_title_tokens(title)
    if not left or not right:
        return False
    return len(left & right) / len(left | right) >= 0.75


def merge_event_status(existing: str, incoming: str) -> str:
    """Conserve l'information la plus prudente lors d'une fusion multi-source."""
    priority = {"unverified": 0, "active": 1, "postponed": 2, "cancelled": 3}
    return max((existing, incoming), key=lambda value: priority.get(value, 0))


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


def is_transient_network_error(error: Exception) -> bool:
    if isinstance(error, TimeoutError):
        return True
    if isinstance(error, URLError):
        reason = error.reason
        return isinstance(reason, TimeoutError) or "timed out" in str(reason).lower()
    return False


def event_category(node: dict[str, Any]) -> str:
    raw = normalize(" ".join((
        first_text(node.get("category")),
        first_text(node.get("keywords")),
        first_text(node.get("name")),
    )))
    mappings = (
        ("MUSIC", ("musique", "concert", "festival", "dj")),
        ("SPORT", ("sport", "course", "match", "randonnée", "randonnee", "vélo", "velo")),
        ("FOOD", ("gastronomie", "marché", "marche", "repas", "dégustation", "degustation")),
        ("FAMILY", ("famille", "enfant", "jeunesse")),
        ("TECHNOLOGY", ("numérique", "numerique", "technologie", "science")),
        ("CULTURE", ("culture", "exposition", "théâtre", "theatre", "cinéma", "cinema", "patrimoine")),
    )
    return next((category for category, tokens in mappings if any(token in raw for token in tokens)), "COMMUNITY")


def event_price(node: dict[str, Any]) -> tuple[str, int | None, str]:
    offers = node.get("offers")
    offer = offers[0] if isinstance(offers, list) and offers else offers
    if not isinstance(offer, dict) or offer.get("price") in (None, ""):
        return "unknown", None, "EUR"
    price = parse_float(offer.get("price"))
    currency = str(offer.get("priceCurrency") or "EUR").upper()
    if price is None:
        return "unknown", None, currency
    return ("free", 0, currency) if price == 0 else ("paid", round(price * 100), currency)


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
    if "cancel" in status_value or any(token in combined for token in CANCELLED_TOKENS):
        status = "cancelled"
    elif "postpon" in status_value or any(token in combined for token in POSTPONED_TOKENS):
        status = "postponed"
    else:
        status = "active"
    venue = html.unescape(first_text(location.get("name"))).strip()
    address = html.unescape(address_text(location.get("address"))).strip()
    fingerprint_source = "|".join((normalize(title), start_at[:10], normalize(venue or address)))
    fingerprint = hashlib.sha256(fingerprint_source.encode("utf-8")).hexdigest()[:24]
    external_id = hashlib.sha256(f"{event_url}|{fingerprint}".encode("utf-8")).hexdigest()[:32]
    price_type, price_cents, currency = event_price(node)
    occurrence_count = max(1, int(node.get("occurrenceCount") or 1))
    next_occurrence_at = iso_datetime(node.get("nextOccurrenceDate")) or None
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
        category=event_category(node),
        price_type=price_type,
        price_cents=price_cents,
        currency=currency,
        occurrence_count=occurrence_count,
        next_occurrence_at=next_occurrence_at,
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
    return enrich_recurring_events(list(unique.values()), body)


def localized_text(value: Any) -> str:
    if isinstance(value, dict):
        return str(value.get("fr") or value.get("en") or next(iter(value.values()), ""))
    return str(value or "")


def dax_period_datetime(period: dict[str, Any], key: str, end_of_day: bool = False) -> str:
    raw_date = str(period.get(key) or "")
    parsed_date = datetime.fromisoformat(raw_date).date()
    weekdays = ("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche")
    hours = (period.get("horaire") or {}).get(weekdays[parsed_date.weekday()]) or []
    values = [str(value).strip() for value in hours if str(value or "").strip()]
    raw_time = values[-1] if end_of_day and values else values[0] if values else "23:59" if end_of_day else "00:00"
    local = datetime.fromisoformat(f"{raw_date}T{raw_time}").replace(tzinfo=ZoneInfo("Europe/Paris"))
    return local.astimezone(timezone.utc).isoformat()


def extract_dax_events(body: str, source_name: str, page_url: str) -> list[Event]:
    match = re.search(r"var\s+objectsSit\s*=\s*(\[.*?\])\s*;", body, re.DOTALL)
    if not match:
        return []
    try:
        objects = json.loads(match.group(1))
    except json.JSONDecodeError:
        return []
    events: list[Event] = []
    now = datetime.now(timezone.utc)
    for wrapper in objects:
        source = wrapper.get("_source") or {}
        location = source.get("localisation") or {}
        geo = location.get("geoJson") or {}
        periods = (source.get("informations") or {}).get("periode") or []
        periods = [period for period in periods if period.get("debut") and period.get("fin")]
        if not periods:
            continue
        periods.sort(key=lambda period: str(period["debut"]))
        start_at = dax_period_datetime(periods[0], "debut")
        end_at = dax_period_datetime(periods[-1], "fin", end_of_day=True)
        future_starts = [dax_period_datetime(period, "debut") for period in periods]
        next_occurrence = next((value for value in future_starts if datetime.fromisoformat(value) >= now), None)
        address = {
            "streetAddress": ", ".join(filter(None, (
                str(location.get("adresse1") or "").strip(),
                str(location.get("adresse2") or "").strip(),
            ))),
            "postalCode": location.get("cp"),
            "addressLocality": localized_text((location.get("commune") or {}).get("nom")),
            "addressCountry": "France",
        }
        information = source.get("informations") or {}
        tariffs = information.get("tarifs") or []
        minimum_price = next((tariff.get("min") for tariff in tariffs if tariff.get("min") is not None), None)
        node: dict[str, Any] = {
            "@type": "Event",
            "name": localized_text(source.get("nom") or source.get("label")),
            "description": localized_text(source.get("descriptifLong") or source.get("descriptifCourt")),
            "startDate": start_at,
            "endDate": end_at,
            "url": page_url,
            "occurrenceCount": len(periods),
            "nextOccurrenceDate": next_occurrence,
            "keywords": " ".join(
                localized_text(item.get("values") or item.get("value"))
                for item in source.get("caracteristiques") or []
            ),
            "location": {
                "@type": "Place",
                "name": str(location.get("description") or address["addressLocality"]),
                "address": address,
                "geo": {"latitude": geo.get("lat"), "longitude": geo.get("lon")},
            },
        }
        if minimum_price is not None:
            node["offers"] = {"price": minimum_price, "priceCurrency": "EUR"}
        event = event_from_json(node, source_name, page_url)
        if event:
            events.append(event)
    return events


def enrich_recurring_events(
    events: list[Event], body: str, now: datetime | None = None,
) -> list[Event]:
    """Ajoute la prochaine occurrence à partir des périodes Tourinsoft du HTML."""
    reference = (now or datetime.now(timezone.utc)).astimezone(timezone.utc)
    decoded_body = html.unescape(body)
    offset_match = re.search(r'"startDate"\s*:\s*"[^"\n]*([+-])(\d{2}):(\d{2})"', decoded_body)
    if offset_match:
        offset_minutes = int(offset_match.group(2)) * 60 + int(offset_match.group(3))
        if offset_match.group(1) == "-":
            offset_minutes *= -1
        page_timezone = timezone(timedelta(minutes=offset_minutes))
    else:
        page_timezone = timezone.utc
    schedules: list[dict[str, Any]] = []
    explicit_occurrences: list[datetime] = []
    for _, encoded in re.findall(r"\bperiods=(['\"])(.*?)\1", body, flags=re.IGNORECASE | re.DOTALL):
        try:
            payload = json.loads(html.unescape(encoded))
        except (json.JSONDecodeError, TypeError):
            continue
        if isinstance(payload, list):
            schedules.extend(item for item in payload if isinstance(item, dict) and item.get("startDate") and item.get("days"))
            for item in payload:
                if not isinstance(item, dict) or not item.get("date"):
                    continue
                first_schedule = (item.get("schedules") or [{}])[0]
                start_time = str(first_schedule.get("startTime") or "00:00:00")
                try:
                    explicit = datetime.fromisoformat(f"{item['date']}T{start_time}").replace(tzinfo=page_timezone)
                except ValueError:
                    continue
                if explicit >= reference:
                    explicit_occurrences.append(explicit.astimezone(timezone.utc))
    if not schedules and not explicit_occurrences:
        return events

    occurrences: list[datetime] = list(explicit_occurrences)
    for period in schedules:
        try:
            start = datetime.fromisoformat(str(period["startDate"]).replace("Z", "+00:00"))
            end = datetime.fromisoformat(str(period.get("endDate") or period["startDate"]).replace("Z", "+00:00"))
        except ValueError:
            continue
        period_timezone = start.tzinfo or timezone.utc
        start = start.astimezone(timezone.utc)
        end = end.astimezone(timezone.utc)
        weekdays: set[int] = set()
        times: dict[int, str] = {}
        for group in period.get("days") or []:
            for day in group.get("days") or []:
                try:
                    weekday = int(str(day.get("day", "")).rsplit(".", 1)[-1]) - 2
                except ValueError:
                    continue
                # 09.02.08 représente des dates irrégulières, pas le dimanche.
                # Celles-ci sont fournies séparément sous la forme {"date": ...}.
                if 0 <= weekday <= 5:
                    weekdays.add(weekday)
                    first_schedule = (day.get("schedules") or [{}])[0]
                    times[weekday] = str(first_schedule.get("startTime") or "00:00:00")
        cursor = max(reference.date(), start.date())
        while cursor <= end.date():
            if cursor.weekday() in weekdays:
                try:
                    hour, minute, second = (int(part) for part in times[cursor.weekday()].split(":"))
                except (ValueError, KeyError):
                    hour = minute = second = 0
                occurrence = datetime(
                    cursor.year, cursor.month, cursor.day, hour, minute, second, tzinfo=period_timezone,
                ).astimezone(timezone.utc)
                if occurrence >= reference:
                    occurrences.append(occurrence)
            cursor += timedelta(days=1)
    if not occurrences:
        return events
    unique_occurrences = sorted(set(occurrences))
    return [
        replace(
            event,
            occurrence_count=max(
                event.occurrence_count,
                len(unique_occurrences),
                2 if explicit_occurrences and event.end_at[:10] != event.start_at[:10] else 1,
            ),
            next_occurrence_at=unique_occurrences[0].isoformat(),
        )
        for event in events
    ]


FRENCH_MONTHS = {
    "janv": 1, "janvier": 1, "fevr": 2, "fevrier": 2, "mars": 3,
    "avr": 4, "avril": 4, "mai": 5, "juin": 6, "juil": 7, "juillet": 7,
    "aout": 8, "sept": 9, "septembre": 9, "oct": 10, "octobre": 10,
    "nov": 11, "novembre": 11, "dec": 12, "decembre": 12,
}


def html_fragment_text(fragment: str) -> str:
    value = re.sub(r"<br\s*/?>", "\n", fragment, flags=re.IGNORECASE)
    value = re.sub(r"<[^>]+>", " ", value)
    return re.sub(r"[ \t\r\f\v]+", " ", html.unescape(value)).strip()


def class_fragment(body: str, class_name: str, tag: str = r"(?:div|span|td)") -> str:
    match = re.search(
        rf'<{tag}\b[^>]*class=["\'][^"\']*\b{re.escape(class_name)}\b[^"\']*["\'][^>]*>(.*?)</{tag.split("|")[0].replace("(?:", "")}>',
        body,
        flags=re.IGNORECASE | re.DOTALL,
    )
    return match.group(1) if match else ""


def french_month(value: str) -> int | None:
    key = normalize(value).replace(" ", "")[:4]
    return FRENCH_MONTHS.get(key) or FRENCH_MONTHS.get(normalize(value))


def extract_armagnac_event(body: str, source_name: str, page_url: str) -> Event | None:
    """Extrait les fiches Tourinsoft de l'agenda Landes d'Armagnac sans JSON-LD."""
    title_match = re.search(r'<h1\b[^>]*>(.*?)</h1>', body, flags=re.IGNORECASE | re.DOTALL)
    latitude_match = re.search(r'(?:center=|\blat\s*:)\s*([+-]?\d{2}\.\d+)', body, flags=re.IGNORECASE)
    longitude_match = re.search(r'(?:center=[^&"\']*\+|\blng\s*:)\s*(-?\d+\.\d+)', body, flags=re.IGNORECASE)
    date_block = class_fragment(body, "detailManifDates")
    if not title_match or not latitude_match or not longitude_match or not date_block:
        return None

    day_match = re.search(r'class=["\'][^"\']*manif-date-day-num[^"\']*["\'][^>]*>\s*(\d{1,2})', date_block, re.IGNORECASE)
    month_match = re.search(r'class=["\'][^"\']*manif-date-month[^"\']*["\'][^>]*>\s*([^<]+)', date_block, re.IGNORECASE)
    to_match = re.search(r'class=["\'][^"\']*manif-date-to[^"\']*["\'][^>]*>\s*au\s+(\d{1,2})\s+([^<\d]+?)\s+(20\d{2})', date_block, re.IGNORECASE)
    if not day_match or not month_match:
        return None
    start_month = french_month(month_match.group(1))
    year_match = re.search(r'\b(20\d{2})\b', date_block)
    # Les pages contiennent aussi l'année de création du site dans leur pied de
    # page (2014). Elle ne doit jamais servir d'année à une date d'événement.
    year = int(to_match.group(3)) if to_match else int(year_match.group(1)) if year_match else datetime.now().year
    if start_month is None:
        return None
    start = datetime(year, start_month, int(day_match.group(1)), tzinfo=timezone.utc)
    if to_match:
        end_month = french_month(to_match.group(2))
        if end_month is None:
            return None
        end = datetime(int(to_match.group(3)), end_month, int(to_match.group(1)), 23, 59, tzinfo=timezone.utc)
    else:
        end = start

    address_row = re.search(r'<tr\b[^>]*class=["\'][^"\']*\baddress\b[^"\']*["\'][^>]*>(.*?)</tr>', body, re.IGNORECASE | re.DOTALL)
    address = html_fragment_text(address_row.group(1)) if address_row else ""
    address = re.sub(r"^Adresse\s*", "", address, flags=re.IGNORECASE).strip()
    description = html_fragment_text(class_fragment(body, "detailDescriptionManif"))
    categories = html_fragment_text(class_fragment(body, "detailManifType"))
    node = {
        "@type": "Event",
        "name": html_fragment_text(title_match.group(1)),
        "description": description,
        "startDate": start.isoformat(),
        "endDate": end.isoformat(),
        "url": page_url,
        "category": categories,
        "location": {
            "@type": "Place",
            "name": address.split(" ", 1)[-1] if address else "Landes d'Armagnac",
            "address": address,
            "geo": {
                "latitude": latitude_match.group(1),
                "longitude": longitude_match.group(1),
            },
        },
    }
    if re.search(r"\bgratuit\b", body, re.IGNORECASE):
        node["offers"] = {"price": 0, "priceCurrency": "EUR"}
    return event_from_json(node, source_name, page_url)


def geocode_coordinates(
    payload: dict[str, Any], expected_city: str, minimum_score: float = 0.35,
) -> tuple[float, float] | None:
    """Valide une réponse GeoJSON de la Géoplateforme avant utilisation."""
    for feature in payload.get("features") or []:
        properties = feature.get("properties") or {}
        geometry = feature.get("geometry") or {}
        coordinates = geometry.get("coordinates") or []
        if normalize(str(properties.get("city") or "")) != normalize(expected_city):
            continue
        if float(properties.get("score") or 0) < minimum_score or len(coordinates) < 2:
            continue
        longitude = parse_float(coordinates[0])
        latitude = parse_float(coordinates[1])
        if latitude is not None and longitude is not None:
            return latitude, longitude
    return None


def geocode_french_address(
    query: str, expected_city: str, timeout: int = 8,
) -> tuple[float, float] | None:
    parameters = urlencode({"q": query, "limit": 3})
    request = urllib.request.Request(
        f"https://data.geopf.fr/geocodage/search?{parameters}",
        headers={"User-Agent": USER_AGENT, "Accept": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        payload = json.loads(response.read(1_000_000).decode("utf-8"))
    return geocode_coordinates(payload, expected_city)


def extract_biscarrosse_event(
    body: str,
    source_name: str,
    page_url: str,
    geocode: Callable[[str], tuple[float, float] | None] | None = None,
) -> Event | None:
    """Extrait une fiche de l'agenda municipal de Biscarrosse.

    Le site n'expose pas de schema.org/Event, mais publie des coordonnées GPS
    précises et un résumé homogène pour chaque manifestation.
    """
    title_match = re.search(
        r'<h1\b[^>]*class=["\'][^"\']*cover__title[^"\']*["\'][^>]*>(.*?)</h1>',
        body, re.IGNORECASE | re.DOTALL,
    )
    latitude_match = re.search(r'\bdata-lat=["\']([+-]?\d+(?:\.\d+)?)["\']', body, re.IGNORECASE)
    longitude_match = re.search(r'\bdata-long=["\']([+-]?\d+(?:\.\d+)?)["\']', body, re.IGNORECASE)
    meta_match = re.search(
        r'<meta\b[^>]*name=["\']description["\'][^>]*content=["\'](.*?)["\']\s*/?>',
        body, re.IGNORECASE | re.DOTALL,
    )
    if not title_match or not meta_match:
        return None

    description = html_fragment_text(meta_match.group(1))
    explicit_dates = [
        datetime(int(year), int(month), int(day), tzinfo=ZoneInfo("Europe/Paris"))
        for day, month, year in re.findall(r'\b(\d{2})/(\d{2})/(20\d{2})\b', description)
    ]
    date_heading_match = re.search(
        r'<h2\b[^>]*class=["\'][^"\']*date-event__title[^"\']*["\'][^>]*>(.*?)</h2>',
        body, re.IGNORECASE | re.DOTALL,
    )
    heading_dates: list[datetime] = []
    if date_heading_match:
        heading = normalize(html_fragment_text(date_heading_match.group(1)))
        for day, month_name, year in re.findall(r'\b(\d{1,2})\s+([a-z]+)\s+(20\d{2})\b', heading):
            month = french_month(month_name)
            if month:
                heading_dates.append(
                    datetime(int(year), month, int(day), tzinfo=ZoneInfo("Europe/Paris"))
                )
    dates = explicit_dates or heading_dates
    if not dates:
        return None

    time_match = re.search(
        r'(?:De\s*)?(\d{1,2})[h:](\d{2})(?:\s*(?:a|à)\s*(\d{1,2})[h:](\d{2}))?',
        description, re.IGNORECASE,
    )
    start_hour = int(time_match.group(1)) if time_match else 0
    start_minute = int(time_match.group(2)) if time_match else 0
    end_hour = int(time_match.group(3)) if time_match and time_match.group(3) else start_hour
    end_minute = int(time_match.group(4)) if time_match and time_match.group(4) else start_minute
    start = min(dates).replace(hour=start_hour, minute=start_minute)
    end = max(dates).replace(hour=end_hour, minute=end_minute)
    if len(heading_dates) >= 2:
        start = min(start, heading_dates[0].replace(hour=start_hour, minute=start_minute))
        end = max(end, heading_dates[-1].replace(hour=end_hour, minute=end_minute))

    location_match = re.search(
        r'<p\b[^>]*class=["\'][^"\']*listing__location[^"\']*["\'][^>]*>(.*?)</p>',
        body, re.IGNORECASE | re.DOTALL,
    )
    if not location_match:
        location_match = re.search(
            r'<p\b[^>]*class=["\'][^"\']*listing__title[^"\']*["\'][^>]*>\s*Localisation\s*</p>'
            r'(.*?)</div>',
            body, re.IGNORECASE | re.DOTALL,
        )
    location_html = location_match.group(1) if location_match else ""
    venue_match = re.search(r'<strong\b[^>]*>(.*?)</strong>', location_html, re.IGNORECASE | re.DOTALL)
    venue = html_fragment_text(venue_match.group(1)) if venue_match else "Biscarrosse"
    address = html_fragment_text(re.sub(r'<strong\b[^>]*>.*?</strong>', "", location_html, flags=re.IGNORECASE | re.DOTALL))
    latitude = parse_float(latitude_match.group(1)) if latitude_match else None
    longitude = parse_float(longitude_match.group(1)) if longitude_match else None
    if (latitude is None or longitude is None) and geocode and address:
        venue_query = venue if normalize(venue) != normalize("Biscarrosse") else ""
        coordinates = geocode(" ".join(part for part in (venue_query, address, "40600 Biscarrosse") if part))
        if coordinates:
            latitude, longitude = coordinates
    if latitude is None or longitude is None:
        return None
    intro_match = re.search(
        r'<p\b[^>]*class=["\'][^"\']*cover__intro[^"\']*["\'][^>]*>(.*?)</p>',
        body, re.IGNORECASE | re.DOTALL,
    )
    intro = html_fragment_text(intro_match.group(1)) if intro_match else description
    now = datetime.now(ZoneInfo("Europe/Paris"))
    next_occurrence = next((value for value in sorted(explicit_dates) if value.date() >= now.date()), None)
    if not next_occurrence and start.date() <= now.date() <= end.date():
        next_occurrence = now.replace(hour=start_hour, minute=start_minute, second=0, microsecond=0)
    node = {
        "@type": "Event",
        "name": html_fragment_text(title_match.group(1)),
        "description": intro,
        "startDate": start.isoformat(),
        "endDate": end.isoformat(),
        "url": page_url,
        "occurrenceCount": max(len(set(explicit_dates)), 2 if start.date() != end.date() else 1),
        "nextOccurrenceDate": next_occurrence.isoformat() if next_occurrence else None,
        "location": {
            "@type": "Place",
            "name": venue,
            "address": address,
            "geo": {"latitude": latitude, "longitude": longitude},
        },
    }
    if re.search(r'\bgratuit\b', body, re.IGNORECASE):
        node["offers"] = {"price": 0, "priceCurrency": "EUR"}
    return event_from_json(node, source_name, page_url)


def extract_detail_events(
    source_type: str, body: str, source_name: str, detail_url: str,
) -> list[Event]:
    if source_type == "armagnac_html":
        event = extract_armagnac_event(body, source_name, detail_url)
        return [event] if event else []
    if source_type == "biscarrosse_html":
        event = extract_biscarrosse_event(body, source_name, detail_url)
        return [event] if event else []
    return extract_events(body, source_name, detail_url)


def fetch(url: str, timeout: int = 20) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "text/html"})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        charset = response.headers.get_content_charset() or "utf-8"
        content = response.read(4_000_000)
        try:
            return content.decode("utf-8")
        except UnicodeDecodeError:
            return content.decode(charset, errors="replace")


def detail_links(
    body: str, base_url: str, limit: int, path_tokens: Iterable[str] | None = None,
) -> list[str]:
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
        accepted_tokens = tuple(path_tokens or ("/agenda/", "/agenda-"))
        if not any(token.lower() in path for token in accepted_tokens):
            continue
        if url not in accepted:
            accepted.append(url)
        if len(accepted) >= limit:
            break
    return accepted


def listing_page_url(source: dict[str, Any], page: int) -> str:
    """Construit une page de liste selon le mécanisme propre à la source."""
    template = str(source.get("list_page_template") or "").strip()
    if template:
        return template.format(page=page)
    separator = "&" if "?" in source["url"] else "?"
    return f"{source['url']}{separator}listpage={page}"


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
    ,category TEXT NOT NULL DEFAULT 'COMMUNITY'
    ,price_type TEXT NOT NULL DEFAULT 'unknown'
    ,price_cents INTEGER
    ,currency TEXT NOT NULL DEFAULT 'EUR'
    ,occurrence_count INTEGER NOT NULL DEFAULT 1
    ,next_occurrence_at TEXT
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
            "SELECT external_id,status FROM events WHERE external_id=? OR fingerprint=? LIMIT 1",
            (event.external_id, event.fingerprint),
        ).fetchone()
        if not existing:
            nearby = connection.execute(
                """
                SELECT external_id,title,start_at,end_at,latitude,longitude,status
                FROM events
                WHERE substr(start_at,1,10)=? AND substr(end_at,1,10)=?
                """,
                (event.start_at[:10], event.end_at[:10]),
            ).fetchall()
            existing = next((
                (row[0], row[6]) for row in nearby
                if likely_duplicate(event, row[1], row[2], row[3], row[4], row[5])
            ), None)
        chosen_id = existing[0] if existing else event.external_id
        if existing:
            updated += 1
            event = replace(event, status=merge_event_status(existing[1], event.status))
        else:
            inserted += 1
        connection.execute(
            """
            INSERT INTO events
            (external_id,title,description,start_at,end_at,venue,address,latitude,longitude,status,
             fingerprint,first_seen_at,last_seen_at,category,price_type,price_cents,currency,
             occurrence_count,next_occurrence_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(external_id) DO UPDATE SET
              title=excluded.title, description=excluded.description,
              start_at=excluded.start_at, end_at=excluded.end_at,
              venue=excluded.venue, address=excluded.address,
              latitude=excluded.latitude, longitude=excluded.longitude,
              status=excluded.status, fingerprint=excluded.fingerprint,
              category=excluded.category, price_type=excluded.price_type,
              price_cents=excluded.price_cents, currency=excluded.currency,
              occurrence_count=excluded.occurrence_count, next_occurrence_at=excluded.next_occurrence_at,
              last_seen_at=excluded.last_seen_at
            """,
            (
                chosen_id, event.title, event.description, event.start_at, event.end_at,
                event.venue, event.address, event.latitude, event.longitude, event.status,
                event.fingerprint, now, now, event.category, event.price_type, event.price_cents, event.currency,
                event.occurrence_count, event.next_occurrence_at,
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


def hydrate_previous_feed(connection: sqlite3.Connection, feed_path: Path | None) -> int:
    """Recharge le dernier flux public pour conserver l'historique entre deux runners GitHub."""
    if feed_path is None or not feed_path.exists():
        return 0
    payload = json.loads(feed_path.read_text(encoding="utf-8"))
    hydrated = 0
    history_cutoff = datetime.now(timezone.utc) - timedelta(days=30)
    for item in payload.get("events", []):
        required = ("external_id", "title", "start_at", "end_at", "latitude", "longitude")
        if any(item.get(key) is None for key in required):
            continue
        try:
            previous_end = datetime.fromisoformat(str(item["end_at"]).replace("Z", "+00:00"))
            if previous_end.tzinfo is None:
                previous_end = previous_end.replace(tzinfo=timezone.utc)
            if previous_end < history_cutoff:
                continue
        except ValueError:
            continue
        connection.execute(
            """
            INSERT OR IGNORE INTO events
            (external_id,title,description,start_at,end_at,venue,address,latitude,longitude,
             status,fingerprint,first_seen_at,last_seen_at,category,price_type,price_cents,currency,
             occurrence_count,next_occurrence_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
            (
                item["external_id"], item["title"], item.get("description", ""),
                item["start_at"], item["end_at"], item.get("venue", ""), item.get("address", ""),
                item["latitude"], item["longitude"], item.get("status", "active"),
                item.get("fingerprint") or hashlib.sha256(str(item["external_id"]).encode()).hexdigest()[:24],
                item.get("first_seen_at") or item.get("last_seen_at") or datetime.now(timezone.utc).isoformat(),
                item.get("last_seen_at") or datetime.now(timezone.utc).isoformat(),
                item.get("category", "COMMUNITY"), item.get("price_type", "unknown"),
                item.get("price_cents"), item.get("currency", "EUR"),
                item.get("occurrence_count", 1), item.get("next_occurrence_at"),
            ),
        )
        for url in item.get("source_urls") or []:
            connection.execute(
                "INSERT OR IGNORE INTO event_sources VALUES (?,?,?,?)",
                (item["external_id"], url, "Flux public précédent", item.get("last_seen_at") or datetime.now(timezone.utc).isoformat()),
            )
        hydrated += 1
    return hydrated


def mark_unverified(connection: sqlite3.Connection, now: str, grace_hours: int = 48) -> int:
    cutoff = (datetime.fromisoformat(now) - timedelta(hours=grace_hours)).isoformat()
    cursor = connection.execute(
        "UPDATE events SET status='unverified' WHERE status='active' AND last_seen_at < ?",
        (cutoff,),
    )
    return cursor.rowcount


def should_export_event(item: dict[str, Any], now: datetime) -> bool:
    try:
        end = datetime.fromisoformat(str(item.get("end_at") or "").replace("Z", "+00:00"))
        if end.tzinfo is None:
            end = end.replace(tzinfo=timezone.utc)
    except ValueError:
        return False
    status = str(item.get("status") or "active")
    if status in ("cancelled", "postponed"):
        return end >= now - timedelta(days=30)
    next_occurrence = item.get("next_occurrence_at")
    if next_occurrence:
        try:
            occurrence = datetime.fromisoformat(str(next_occurrence).replace("Z", "+00:00"))
            if occurrence.tzinfo is None:
                occurrence = occurrence.replace(tzinfo=timezone.utc)
            if occurrence >= now:
                return True
        except ValueError:
            pass
    try:
        if int(item.get("occurrence_count") or 1) > 1:
            return end >= now
    except (TypeError, ValueError):
        return False
    return end.date() >= now.date()


def normalize_export_schedule(item: dict[str, Any], reference: datetime) -> dict[str, Any]:
    """Rend les périodes sans horaires et leurs récurrences cohérentes à l'export."""
    normalized_item = dict(item)
    try:
        start = datetime.fromisoformat(str(item.get("start_at") or "").replace("Z", "+00:00"))
        end = datetime.fromisoformat(str(item.get("end_at") or "").replace("Z", "+00:00"))
    except ValueError:
        return normalized_item
    if start.tzinfo is None:
        start = start.replace(tzinfo=timezone.utc)
    if end.tzinfo is None:
        end = end.replace(tzinfo=timezone.utc)
    # Les sources touristiques utilisent 00:00 quand aucun horaire n'est
    # fourni. La manifestation reste alors valable pendant toute la journée.
    if end.hour == 0 and end.minute == 0 and end.second == 0:
        end = end + timedelta(days=1) - timedelta(seconds=1)
        normalized_item["end_at"] = end.isoformat()

    try:
        occurrence_count = int(item.get("occurrence_count") or 1)
    except (TypeError, ValueError):
        occurrence_count = 1
    if occurrence_count <= 1 or item.get("status", "active") not in {"active", "unverified"}:
        return normalized_item
    try:
        next_occurrence = datetime.fromisoformat(
            str(item.get("next_occurrence_at") or "").replace("Z", "+00:00")
        )
        if next_occurrence.tzinfo is None:
            next_occurrence = next_occurrence.replace(tzinfo=timezone.utc)
    except ValueError:
        next_occurrence = None
    if next_occurrence is None or next_occurrence < reference or next_occurrence > end:
        end_of_today = reference.replace(hour=23, minute=59, second=59, microsecond=0)
        replacement = start if start > reference else min(end, end_of_today)
        normalized_item["next_occurrence_at"] = (
            replacement.isoformat() if reference <= replacement <= end else None
        )
    return normalized_item


def export_feed(connection: sqlite3.Connection, output: Path, now: datetime | None = None) -> int:
    reference = now or datetime.now(timezone.utc)
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
        item = normalize_export_schedule(item, reference)
        if should_export_event(item, reference):
            payload.append(item)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({"generated_at": reference.isoformat(), "events": payload}, ensure_ascii=False, indent=2), encoding="utf-8")
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


def collection_status(
    failures: list[str], warnings: list[str], source_reports: list[dict[str, Any]],
) -> str:
    if failures:
        return "degraded"
    if warnings:
        return "partial"
    if any(source.get("status") == "credentials_invalid" for source in source_reports):
        return "attention"
    return "ok"


def coverage_readiness(config: dict[str, Any], source_reports: list[dict[str, Any]]) -> dict[str, Any]:
    plan = config.get("expansion_plan") or {}
    current_radius = float(config.get("radius_km") or 0)
    target_radius = float(plan.get("target_radius_km") or 0)
    expansion_active = bool(target_radius) and current_radius >= target_radius
    required_areas = set(plan.get("required_areas") or [])
    reports_by_name = {report.get("name"): report for report in source_reports}
    covered_areas: set[str] = set()
    blocked_sources: list[str] = []
    preview_events = 0
    for source in config.get("sources", []):
        source_areas = set(source.get("coverage_areas") or []) & required_areas
        if not source_areas:
            continue
        report = reports_by_name.get(source.get("name"), {})
        target_count = report.get("accepted_in_target_radius")
        has_target_events = target_count is None or int(target_count) > 0
        if report.get("status") == "ok" and report.get("reachable") and has_target_events:
            covered_areas.update(source_areas)
            preview_events += int(report.get("preview_outside_current_radius") or 0)
        elif report.get("status") in {"degraded", "credentials_invalid", "transient_error"}:
            blocked_sources.append(str(source.get("name")))
        elif report.get("reachable") and not has_target_events:
            blocked_sources.append(f"{source.get('name')} (aucun événement dans le rayon cible)")
    missing_areas = sorted(required_areas - covered_areas)
    return {
        "current_radius_km": config.get("radius_km"),
        "target_radius_km": plan.get("target_radius_km"),
        "required_areas": sorted(required_areas),
        "covered_areas": sorted(covered_areas & required_areas),
        "missing_areas": missing_areas,
        "blocked_sources": blocked_sources,
        "preview_events_outside_current_radius": preview_events,
        "expansion_active": expansion_active,
        "expansion_ready": (
            bool(required_areas) and not expansion_active and not missing_areas and not blocked_sources
        ),
    }


def run(
    config_path: Path,
    database_path: Path,
    output_path: Path,
    max_pages: int | None = None,
    max_runtime_seconds: int = 180,
    previous_feed_path: Path | None = None,
) -> int:
    config = json.loads(config_path.read_text(encoding="utf-8"))
    center = config["center"]
    radius = float(config["radius_km"])
    target_radius = float((config.get("expansion_plan") or {}).get("target_radius_km") or radius)
    collected: list[Event] = []
    failures: list[str] = []
    warnings: list[str] = []
    deadline = time.monotonic() + max_runtime_seconds
    source_reports: list[dict[str, Any]] = []
    sources = sorted(config["sources"], key=lambda item: int(item.get("priority", 0)), reverse=True)
    for source in sources:
        source_failure_count = 0
        source_candidate_count = 0
        source_accepted_count = 0
        source_target_count = 0
        source_reachable = False
        source_status = "ok"
        if time.monotonic() >= deadline:
            failures.append("Durée maximale atteinte avant la fin des sources")
            break
        try:
            source_type = source.get("type", "jsonld")
            timeout = min(20, max(1, int(deadline - time.monotonic())))
            if source_type in ("jsonld", "armagnac_html", "biscarrosse_html", "dax_embedded"):
                body = fetch(source["url"], timeout=timeout)
                candidates = (
                    extract_dax_events(body, source["name"], source["url"])
                    if source_type == "dax_embedded"
                    else extract_events(body, source["name"], source["url"])
                )
                page_limit = max_pages if max_pages is not None else int(source.get("max_detail_pages", 20))
                listing_bodies = [body]
                for list_page in range(2, int(source.get("list_pages", 1)) + 1):
                    if time.monotonic() >= deadline:
                        break
                    listing_url = listing_page_url(source, list_page)
                    try:
                        timeout = min(20, max(1, int(deadline - time.monotonic())))
                        listing_bodies.append(fetch(listing_url, timeout=timeout))
                    except Exception as error:
                        source_failure_count += 1
                        failures.append(f"{source['name']} (liste {list_page}): {error}")
                source_detail_links: list[str] = []
                for listing_body in listing_bodies:
                    for detail_url in detail_links(
                        listing_body,
                        source["url"],
                        page_limit,
                        source.get("detail_path_tokens"),
                    ):
                        if detail_url not in source_detail_links:
                            source_detail_links.append(detail_url)
                        if len(source_detail_links) >= page_limit:
                            break
                    if len(source_detail_links) >= page_limit:
                        break
                detail_urls = source_detail_links if source_type != "dax_embedded" else []

                def fetch_detail(detail_url: str) -> tuple[list[Event], Exception | None]:
                    remaining = int(deadline - time.monotonic())
                    if remaining <= 0:
                        return [], TimeoutError("durée maximale atteinte")
                    try:
                        timeout = min(15, max(1, remaining))
                        detail_body = fetch(detail_url, timeout=timeout)
                        if source_type == "biscarrosse_html":
                            event = extract_biscarrosse_event(
                                detail_body,
                                source["name"],
                                detail_url,
                                geocode=lambda query: geocode_french_address(
                                    query, "Biscarrosse", timeout=min(8, max(1, remaining)),
                                ),
                            )
                            return ([event] if event else []), None
                        return extract_detail_events(source_type, detail_body, source["name"], detail_url), None
                    except Exception as error:
                        return [], error

                workers = min(max(1, int(source.get("detail_workers", 4))), len(detail_urls)) if detail_urls else 1
                with ThreadPoolExecutor(max_workers=workers) as executor:
                    detail_results = executor.map(fetch_detail, detail_urls)
                    for detail_url, (detail_events, error) in zip(detail_urls, detail_results):
                        candidates.extend(detail_events)
                        if error is None:
                            continue
                        source_failure_count += 1
                        failures.append(f"{source['name']} ({detail_url}): {error}")
            else:
                nodes = collect_api_source(source, center, timeout)
                candidates = [event for node in nodes if (event := event_from_json(node, source["name"], node.get("url", "")))]
            source_reachable = True
            source_candidate_count = len(candidates)
            minimum_candidates = int(source.get("min_candidates", 0))
            if source_candidate_count < minimum_candidates:
                source_failure_count += 1
                source_status = "degraded"
                failures.append(
                    f"{source['name']}: seulement {source_candidate_count} candidat(s), "
                    f"minimum attendu {minimum_candidates}"
                )
            accepted = [event for event in candidates
                if distance_km(center["latitude"], center["longitude"], event.latitude, event.longitude) <= radius]
            source_accepted_count = len(accepted)
            source_target_count = sum(
                distance_km(center["latitude"], center["longitude"], event.latitude, event.longitude)
                <= target_radius
                for event in candidates
            )
            collected.extend(accepted)
        except MissingCredentials as error:
            source_status = "credentials_missing"
            # Connecteur optionnel encore non configuré : visible dans le rapport,
            # mais il ne dégrade pas les sources déjà opérationnelles.
        except InvalidCredentials:
            source_status = "credentials_invalid"
        except Exception as error:  # une source en panne ne bloque pas les autres
            if is_transient_network_error(error):
                source_status = "transient_error"
                warnings.append(f"{source['name']}: {error}")
            else:
                source_failure_count += 1
                source_status = "degraded"
                failures.append(f"{source['name']}: {error}")
        source_reports.append({
            "name": source["name"],
            "type": source.get("type", "jsonld"),
            "priority": int(source.get("priority", 0)),
            "trust": source.get("trust", "unknown"),
            "reachable": source_reachable,
            "candidates": source_candidate_count,
            "accepted_in_radius": source_accepted_count,
            "accepted_in_target_radius": source_target_count,
            "preview_outside_current_radius": max(0, source_target_count - source_accepted_count),
            "failures": source_failure_count,
            "status": source_status if source_failure_count == 0 else "degraded",
        })
    for curated in config.get("curated_events", []):
        event = event_from_curated(curated)
        if event and distance_km(center["latitude"], center["longitude"], event.latitude, event.longitude) <= radius:
            collected.append(event)
    database_path.parent.mkdir(parents=True, exist_ok=True)
    now = datetime.now(timezone.utc).isoformat()
    with sqlite3.connect(database_path) as connection:
        connection.executescript(SCHEMA)
        hydrated = hydrate_previous_feed(connection, previous_feed_path)
        inserted, updated = persist(connection, collected, now)
        unverified = mark_unverified(connection, now)
        exported = export_feed(connection, output_path)
    pending_candidates = export_candidates(config, output_path.with_name("candidates.json"), now)
    report = {
        "status": collection_status(failures, warnings, source_reports),
        "generated_at": now,
        "sources": len(config["sources"]),
        "source_reports": source_reports,
        "coverage_readiness": coverage_readiness(config, source_reports),
        "curated_events": len(config.get("curated_events", [])),
        "pending_candidates": pending_candidates,
        "fetched_events": len(collected),
        "inserted": inserted,
        "updated": updated,
        "hydrated_from_previous_feed": hydrated,
        "marked_unverified": unverified,
        "exported": exported,
        "failures": failures,
        "warnings": warnings,
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
    parser.add_argument("--previous-feed", type=Path, default=None)
    arguments = parser.parse_args()
    return run(
        arguments.config,
        arguments.database,
        arguments.output,
        arguments.max_pages,
        arguments.max_runtime_seconds,
        arguments.previous_feed,
    )


if __name__ == "__main__":
    sys.exit(main())
