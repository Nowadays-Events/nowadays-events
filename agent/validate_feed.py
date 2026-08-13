#!/usr/bin/env python3
"""Valide le flux public avant son déploiement dans l'application."""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timedelta, timezone
from pathlib import Path


def parse_datetime(value: object) -> datetime:
    parsed = datetime.fromisoformat(str(value or "").replace("Z", "+00:00"))
    return parsed.replace(tzinfo=timezone.utc) if parsed.tzinfo is None else parsed


def validate(
    payload: dict,
    minimum_events: int = 10,
    now: datetime | None = None,
    previous_payload: dict | None = None,
    minimum_retention_ratio: float = 0.5,
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
            latitude = float(event.get("latitude"))
            longitude = float(event.get("longitude"))
            if not -90 <= latitude <= 90 or not -180 <= longitude <= 180:
                errors.append(f"{label}: coordonnées hors limites")
        except (TypeError, ValueError):
            errors.append(f"{label}: coordonnées invalides")
        if not event.get("source_urls"):
            errors.append(f"{label}: source manquante")
    if stale_count:
        errors.append(f"{stale_count} événement(s) terminé(s) depuis plus de 30 jours")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--feed", type=Path, required=True)
    parser.add_argument("--previous-feed", type=Path)
    parser.add_argument("--minimum-events", type=int, default=10)
    parser.add_argument("--minimum-retention-ratio", type=float, default=0.5)
    arguments = parser.parse_args()
    payload = json.loads(arguments.feed.read_text(encoding="utf-8"))
    previous_payload = None
    if arguments.previous_feed and arguments.previous_feed.exists():
        previous_payload = json.loads(arguments.previous_feed.read_text(encoding="utf-8"))
    errors = validate(
        payload,
        arguments.minimum_events,
        previous_payload=previous_payload,
        minimum_retention_ratio=arguments.minimum_retention_ratio,
    )
    print(json.dumps({"valid": not errors, "errors": errors}, ensure_ascii=False, indent=2))
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
