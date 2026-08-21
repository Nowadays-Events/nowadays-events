#!/usr/bin/env python3
"""Signale les régressions de collecte dans une issue GitHub dédupliquée."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
from pathlib import Path

LABEL = "source dégradée"
COLOR = "D93F0B"


def gh(*arguments: str) -> str:
    result = subprocess.run(["gh", *arguments], text=True, capture_output=True, check=True)
    return result.stdout.strip()


def degraded_sources(payload: dict) -> list[dict]:
    return [
        source for source in payload.get("source_reports", [])
        if source.get("status") in {"warning", "error"}
    ]


def incident_id(sources: list[dict]) -> str:
    identity = "|".join(sorted(str(source.get("name") or "") for source in sources))
    return hashlib.sha256(identity.encode("utf-8")).hexdigest()[:12]


def issue_body(payload: dict, sources: list[dict], identifier: str) -> str:
    lines = []
    for source in sources:
        status = source.get("status", "degraded")
        diagnosis = (
            "identifiants refusés — vérifier ou renouveler le secret GitHub"
            if source.get("reason") == "credentials_invalid"
            else "collectes vides répétées"
            if source.get("reason") == "repeated_empty_collection"
            else f"{source.get('failures', 0)} erreur(s)"
        )
        lines.append(
            f"- **{source.get('name', 'Source inconnue')}** : "
            f"{source.get('candidates', 0)} candidat(s), "
            f"{source.get('accepted_in_radius', 0)} retenu(s), "
            f"{diagnosis}"
        )
    details = "\n".join(lines) or "- Aucune source détaillée"
    failures = payload.get("failures") or []
    failure_details = "\n".join(f"- `{failure}`" for failure in failures) or "- Aucun message détaillé"
    return f"""## Alerte de collecte Xymis Events

Le flux a été produit, mais une ou plusieurs sources nécessitent une intervention.

{details}

### Erreurs détaillées

{failure_details}

- **État global :** {payload.get('status', 'inconnu')}
- **Dernière collecte :** {payload.get('generated_at', 'inconnue')}
- **Événements exportés :** {payload.get('exported', 'inconnu')}

Cette alerte est automatiquement fermée lorsqu'une collecte saine est observée.

<!-- xymis-health-incident:{identifier} -->
"""


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--health", type=Path, required=True)
    arguments = parser.parse_args()
    repository = os.environ["GITHUB_REPOSITORY"]
    owner = repository.split("/", 1)[0]
    subprocess.run(
        ["gh", "label", "create", LABEL, "--repo", repository, "--color", COLOR, "--force"],
        check=True,
    )
    open_issues = json.loads(gh(
        "issue", "list", "--repo", repository, "--state", "open", "--label", LABEL,
        "--limit", "20", "--json", "number,body",
    ) or "[]")
    payload = json.loads(arguments.health.read_text(encoding="utf-8"))
    sources = degraded_sources(payload)
    if not sources:
        for issue in open_issues:
            gh(
                "issue", "close", str(issue["number"]), "--repo", repository,
                "--comment", "Collecte saine confirmée : cette alerte est résolue automatiquement.",
            )
        print(json.dumps({"status": "healthy", "issues_closed": len(open_issues)}))
        return 0

    identifier = incident_id(sources)
    marker = f"xymis-health-incident:{identifier}"
    if any(marker in (issue.get("body") or "") for issue in open_issues):
        print(json.dumps({"status": "degraded", "issue_created": False}))
        return 0
    for issue in open_issues:
        gh(
            "issue", "close", str(issue["number"]), "--repo", repository,
            "--comment", "Cette combinaison de pannes a changé ; une alerte actualisée la remplace.",
        )
    title = f"[Santé collecte] {len(sources)} source(s) dégradée(s) ({identifier})"
    gh(
        "issue", "create", "--repo", repository, "--title", title,
        "--body", issue_body(payload, sources, identifier), "--label", LABEL, "--assignee", owner,
    )
    print(json.dumps({"status": "degraded", "issue_created": True}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
