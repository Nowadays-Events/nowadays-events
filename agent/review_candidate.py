#!/usr/bin/env python3
"""Valide strictement une fiche issue d'une issue GitHub avant création de PR."""

from __future__ import annotations

import argparse
import base64
import json
import re
from pathlib import Path

MARKER = re.compile(r"<!-- nowadays-candidate-json:([A-Za-z0-9+/=]+) -->")


def extract_candidate(issue_body: str) -> dict:
    match = MARKER.search(issue_body)
    if not match:
        raise ValueError("fiche candidate absente de l'issue")
    candidate = json.loads(base64.b64decode(match.group(1)).decode("utf-8"))
    required = ("name", "startDate", "url", "location")
    missing = [field for field in required if not candidate.get(field)]
    if missing:
        raise ValueError(f"champs obligatoires manquants: {', '.join(missing)}")
    location = candidate["location"]
    geo = location.get("geo") if isinstance(location, dict) else None
    if not isinstance(geo, dict) or geo.get("latitude") is None or geo.get("longitude") is None:
        raise ValueError("coordonnées obligatoires manquantes")
    candidate.pop("review_status", None)
    candidate.pop("missing_fields", None)
    candidate["source_name"] = candidate.get("source_name") or "Source validée par l’administrateur"
    return candidate


def approve(config_path: Path, issue_body: str) -> None:
    config = json.loads(config_path.read_text(encoding="utf-8"))
    candidate = extract_candidate(issue_body)
    existing_urls = {item.get("url") for item in config.get("curated_events", [])}
    if candidate.get("url") not in existing_urls:
        config.setdefault("curated_events", []).append(candidate)
    config_path.write_text(json.dumps(config, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=Path, required=True)
    body_source = parser.add_mutually_exclusive_group(required=True)
    body_source.add_argument("--issue-body")
    body_source.add_argument("--issue-body-file", type=Path)
    arguments = parser.parse_args()
    issue_body = (
        arguments.issue_body_file.read_text(encoding="utf-8")
        if arguments.issue_body_file
        else arguments.issue_body
    )
    approve(arguments.config, issue_body)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
