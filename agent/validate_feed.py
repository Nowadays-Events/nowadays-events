#!/usr/bin/env python3
"""Valide le flux public avant son déploiement dans l'application."""

from __future__ import annotations

import argparse
import json
import math
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import urlsplit


BROKEN_TEXT_MARKERS = ("\ufffd", "Ã", "â€", "ðŸ")


def has_broken_encoding(value: object) -> bool:
    text = str(value or "")
    return any(marker in text for marker in BROKEN_TEXT_MARKERS)


def parse_datetime(value: object) -> datetime:
    parsed = datetime.fromisoformat(str(value or "").replace("Z", "+00:00"))
    return parsed.replace(tzinfo=timezone.utc) if parsed.tzinfo is None else parsed


def distance_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    radius = 6371.0
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)
    value = (
        math.sin(delta_phi / 2) ** 2
        + math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2) ** 2
    )
    return 2 * radius * math.atan2(math.sqrt(value), math.sqrt(1 - value))


def geographic_settings(
    config: dict,
    latitude: float | None = None,
    longitude: float | None = None,
    radius_km: float | None = None,
) -> tuple[tuple[float, float] | None, float | None]:
    configured_center = config.get("center") or {}
    resolved_latitude = latitude if latitude is not None else configured_center.get("latitude")
    resolved_longitude = longitude if longitude is not None else configured_center.get("longitude")
    resolved_radius = radius_km if radius_km is not None else config.get("radius_km")
    center = (
        (float(resolved_latitude), float(resolved_longitude))
        if resolved_latitude is not None and resolved_longitude is not None
        else None
    )
    return center, float(resolved_radius) if resolved_radius is not None else None


def validate(
    payload: dict,
    minimum_events: int = 10,
    now: datetime | None = None,
    previous_payload: dict | None = None,
    minimum_retention_ratio: float = 0.5,
    center: tuple[float, float] | None = None,
    radius_km: float | None = None,
) -> list[str]:
    errors: list[str] = []
    events = payload.get("events")
    if not isinstance(events, list):
        return ["Le champ events doit être une liste"]
    if len(events) < minimum_events:
        errors.append(f"Seulement {len(events)} événement(s), minimum attendu {minimum_events}")
    previous_events = (previous_payload or {}).get("events")
    if isinstance(previous_events, list) and previous_events:
        minimum_retained = max(minimum_events, int(len(previous_events) * minimum_retention_ratio))
        if len(events) < minimum_retained:
            errors.append(
                f"Chute anormale du flux : {len(events)} événement(s) contre "
                f"{len(previous_events)} précédemment (minimum toléré {minimum_retained})"
            )
    reference = now or datetime.now(timezone.utc)
    stale_cutoff = reference - timedelta(days=30)
    identifiers: set[str] = set()
    stale_count = 0
    for index, event in enumerate(events):
        label = f"Événement #{index + 1}"
        identifier = str(event.get("external_id") or "")
        if not identifier:
            errors.append(f"{label}: identifiant manquant")
        elif identifier in identifiers:
            errors.append(f"{label}: identifiant dupliqué {identifier}")
        identifiers.add(identifier)
        if not str(event.get("title") or "").strip():
            errors.append(f"{label}: titre manquant")
        for field in ("title", "description", "venue", "address", "category"):
            if has_broken_encoding(event.get(field)):
                errors.append(f"{label}: encodage illisible dans {field}")
                break
        end: datetime | None = None
        try:
            start = parse_datetime(event.get("start_at"))
            end = parse_datetime(event.get("end_at"))
            if end < start:
                errors.append(f"{label}: fin antérieure au début")
            if end < stale_cutoff:
                stale_count += 1
        except (TypeError, ValueError):
            errors.append(f"{label}: date invalide")
        try:
            occurrence_count = int(event.get("occurrence_count") or 1)
        except (TypeError, ValueError):
            occurrence_count = 0
            errors.append(f"{label}: nombre d'occurrences invalide")
        if occurrence_count > 1 and event.get("status", "active") in {"active", "unverified"}:
            try:
                next_occurrence = parse_datetime(event.get("next_occurrence_at"))
                if next_occurrence < reference:
                    errors.append(f"{label}: prochaine occurrence déjà passée")
                if end is not None and next_occurrence > end:
                    errors.append(f"{label}: prochaine occurrence postérieure à la fin")
            except (TypeError, ValueError):
                errors.append(f"{label}: prochaine occurrence manquante ou invalide")
        try:
            latitude = float(event.get("latitude"))
            longitude = float(event.get("longitude"))
            if not -90 <= latitude <= 90 or not -180 <= longitude <= 180:
                errors.append(f"{label}: coordonnées hors limites")
            elif center is not None and radius_km is not None and distance_km(
                center[0], center[1], latitude, longitude,
            ) > radius_km:
                errors.append(f"{label}: événement hors du rayon de {radius_km:g} km")
        except (TypeError, ValueError):
            errors.append(f"{label}: coordonnées invalides")
        source_urls = event.get("source_urls")
        if not isinstance(source_urls, list) or not source_urls:
            errors.append(f"{label}: source manquante")
        else:
            for source_url in source_urls:
                parsed_url = urlsplit(str(source_url or ""))
                if parsed_url.scheme not in {"http", "https"} or not parsed_url.netloc:
                    errors.append(f"{label}: URL source non sûre ou invalide")
                    break
    if stale_count:
        errors.append(f"{stale_count} événement(s) terminé(s) depuis plus de 30 jours")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--feed", type=Path, required=True)
    parser.add_argument("--config", type=Path)
    parser.add_argument("--previous-feed", type=Path)
    parser.add_argument("--minimum-events", type=int, default=10)
    parser.add_argument("--minimum-retention-ratio", type=float, default=0.5)
    parser.add_argument("--center-latitude", type=float)
    parser.add_argument("--center-longitude", type=float)
    parser.add_argument("--radius-km", type=float)
    arguments = parser.parse_args()
    payload = json.loads(arguments.feed.read_text(encoding="utf-8"))
    config = {}
    if arguments.config:
        config = json.loads(arguments.config.read_text(encoding="utf-8"))
    center, radius_km = geographic_settings(
        config, arguments.center_latitude, arguments.center_longitude, arguments.radius_km,
    )
    previous_payload = None
    if arguments.previous_feed and arguments.previous_feed.exists():
        previous_payload = json.loads(arguments.previous_feed.read_text(encoding="utf-8"))
    errors = validate(
        payload,
        arguments.minimum_events,
        previous_payload=previous_payload,
        minimum_retention_ratio=arguments.minimum_retention_ratio,
        center=center,
        radius_km=radius_km,
    )
    print(json.dumps({"valid": not errors, "errors": errors}, ensure_ascii=False, indent=2))
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
