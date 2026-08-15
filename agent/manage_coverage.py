#!/usr/bin/env python3
"""Prévient l'administrateur lorsque l'extension géographique devient sûre."""

from __future__ import annotations

import argparse
import json
import os
import subprocess
from pathlib import Path

LABEL = "extension zone"
COLOR = "1D76DB"
MARKER = "xymis-coverage-expansion-ready"


def notification_body(readiness: dict) -> str:
    areas = ", ".join(readiness.get("required_areas") or [])
    preview_count = int(readiness.get("preview_events_outside_current_radius") or 0)
    return f"""## Extension géographique prête

Les sources requises pour **{areas}** sont maintenant disponibles et saines.
Le mode d'observation a détecté **{preview_count} événement(s)** au-delà du rayon actuel.
Le rayon peut être étudié pour passer de **{readiness.get('current_radius_km')} km** à
**{readiness.get('target_radius_km')} km**, après un dernier contrôle visuel des regroupements sur la carte.

Cette notification est générée automatiquement ; elle ne modifie pas le rayon sans validation humaine.

<!-- {MARKER} -->
"""


def gh(*arguments: str) -> str:
    result = subprocess.run(["gh", *arguments], text=True, capture_output=True, check=True)
    return result.stdout.strip()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--health", type=Path, required=True)
    arguments = parser.parse_args()
    payload = json.loads(arguments.health.read_text(encoding="utf-8"))
    readiness = payload.get("coverage_readiness") or {}
    if not readiness.get("expansion_ready"):
        print(json.dumps({"status": "not_ready", "missing_areas": readiness.get("missing_areas", [])}))
        return 0
    repository = os.environ["GITHUB_REPOSITORY"]
    owner = repository.split("/", 1)[0]
    subprocess.run(
        ["gh", "label", "create", LABEL, "--repo", repository, "--color", COLOR, "--force"],
        check=True,
    )
    issues = json.loads(gh(
        "issue", "list", "--repo", repository, "--state", "all", "--limit", "100",
        "--json", "number,body",
    ) or "[]")
    if any(MARKER in (issue.get("body") or "") for issue in issues):
        print(json.dumps({"status": "ready", "issue_created": False}))
        return 0
    gh(
        "issue", "create", "--repo", repository,
        "--title", "[Couverture] Extension vers la côte landaise prête à valider",
        "--body", notification_body(readiness), "--label", LABEL, "--assignee", owner,
    )
    print(json.dumps({"status": "ready", "issue_created": True}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
