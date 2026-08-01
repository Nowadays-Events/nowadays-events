#!/usr/bin/env python3
"""Crée une issue GitHub unique pour chaque candidat à vérifier."""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import subprocess
from pathlib import Path

LABELS = {
    "à vérifier": "D4C5F9",
    "validé": "0E8A16",
    "refusé": "B60205",
    "à corriger": "FBCA04",
}


def gh(*arguments: str, input_text: str | None = None) -> str:
    result = subprocess.run(
        ["gh", *arguments], input=input_text, text=True, capture_output=True, check=True,
    )
    return result.stdout.strip()


def ensure_labels(repository: str) -> None:
    for name, color in LABELS.items():
        subprocess.run(
            ["gh", "label", "create", name, "--repo", repository, "--color", color, "--force"],
            check=True,
        )


def candidate_id(candidate: dict) -> str:
    identity = "|".join(str(candidate.get(key) or "") for key in ("url", "name", "startDate"))
    return hashlib.sha256(identity.encode("utf-8")).hexdigest()[:12]


def issue_body(candidate: dict, identifier: str) -> str:
    encoded = base64.b64encode(json.dumps(candidate, ensure_ascii=False).encode("utf-8")).decode("ascii")
    missing = ", ".join(candidate.get("missing_fields", [])) or "aucun"
    return f"""## Proposition d’événement

- **Titre :** {candidate.get('name', 'Non renseigné')}
- **Début :** {candidate.get('startDate', 'Non renseigné')}
- **Fin :** {candidate.get('endDate', 'Non renseigné')}
- **Lieu :** {candidate.get('location', 'Non renseigné')}
- **Source :** {candidate.get('url', 'Non renseignée')}
- **Confiance :** {candidate.get('confidence', 'à vérifier')}
- **Champs manquants :** {missing}

Répondez à l’e-mail GitHub ou commentez l’issue avec une commande seule :

- `/valider`
- `/refuser`
- `/corriger` suivi de vos indications

Une validation crée une Pull Request : elle ne publie jamais directement l’événement.

<!-- nowadays-candidate-id:{identifier} -->
<!-- nowadays-candidate-json:{encoded} -->
"""


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--candidates", type=Path, required=True)
    arguments = parser.parse_args()
    repository = os.environ["GITHUB_REPOSITORY"]
    owner = repository.split("/", 1)[0]
    ensure_labels(repository)
    payload = json.loads(arguments.candidates.read_text(encoding="utf-8"))
    existing = gh(
        "issue", "list", "--repo", repository, "--state", "all",
        "--limit", "200", "--json", "title,body",
    )
    existing_bodies = "\n".join(item.get("body") or "" for item in json.loads(existing or "[]"))
    created = 0
    for candidate in payload.get("candidates", []):
        identifier = candidate_id(candidate)
        if f"nowadays-candidate-id:{identifier}" in existing_bodies:
            continue
        title = f"[Validation événement] {candidate.get('name', 'Sans titre')} ({identifier})"
        gh(
            "issue", "create", "--repo", repository, "--title", title,
            "--body", issue_body(candidate, identifier), "--label", "à vérifier", "--assignee", owner,
        )
        created += 1
    print(json.dumps({"candidates": len(payload.get("candidates", [])), "issues_created": created}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
