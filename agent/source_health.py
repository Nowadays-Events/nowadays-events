"""Pure source-health decisions shared by collection and tests."""

from __future__ import annotations

from typing import Any


def previous_report(previous_health: dict[str, Any], name: str) -> dict[str, Any]:
    return next(
        (item for item in previous_health.get("source_reports", []) if item.get("name") == name),
        {},
    )


def assess_observation(
    source: dict[str, Any],
    candidates: int,
    previous: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """Classify a successful collection without alarming on one isolated empty result."""
    previous = previous or {}
    previous_streak = int(previous.get("consecutive_empty_collections") or 0)
    empty_streak = previous_streak + 1 if candidates == 0 else 0
    previous_reference = int(previous.get("last_nonzero_candidates") or 0)
    reference = candidates if candidates > 0 else previous_reference

    threshold = max(2, int(source.get("empty_warning_threshold", 3)))
    expects_events = bool(source.get("expect_events", source.get("min_candidates", 0) > 0))
    status = "ok"
    reason = None
    if expects_events and candidates == 0 and empty_streak >= threshold:
        status = "warning"
        reason = "repeated_empty_collection"
    elif candidates > 0 and previous_reference > 0:
        ratio = float(source.get("abnormal_drop_ratio", 0))
        minimum_reference = int(source.get("abnormal_drop_min_reference", 10))
        if ratio > 0 and previous_reference >= minimum_reference and candidates / previous_reference < ratio:
            status = "warning"
            reason = "abnormal_candidate_drop"

    return {
        "status": status,
        "reason": reason,
        "consecutive_empty_collections": empty_streak,
        "last_nonzero_candidates": reference,
        "empty_warning_threshold": threshold,
    }
