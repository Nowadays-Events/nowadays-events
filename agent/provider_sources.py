"""Connecteurs API optionnels. Aucun secret n'est conservé dans le dépôt."""

from __future__ import annotations

import json
import os
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone
from typing import Any

USER_AGENT = "NowadaysEventAgent/0.2 (+https://github.com/Nowadays-Events/nowadays-events)"


class MissingCredentials(RuntimeError):
    pass


def request_json(
    url: str,
    *,
    headers: dict[str, str] | None = None,
    body: dict[str, Any] | None = None,
    form: dict[str, str] | None = None,
    timeout: int = 20,
) -> dict[str, Any]:
    request_headers = {"User-Agent": USER_AGENT, "Accept": "application/json", **(headers or {})}
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        request_headers["Content-Type"] = "application/json"
    elif form is not None:
        data = urllib.parse.urlencode(form).encode("utf-8")
        request_headers["Content-Type"] = "application/x-www-form-urlencoded"
    request = urllib.request.Request(url, data=data, headers=request_headers)
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read(8_000_000).decode(response.headers.get_content_charset() or "utf-8"))


def localized(value: Any) -> str:
    if isinstance(value, str):
        return value
    if isinstance(value, dict):
        for key in ("fr", "text", "name", "html"):
            if value.get(key):
                return localized(value[key])
        return next((localized(item) for item in value.values() if item), "")
    return ""


def number(*values: Any) -> float | None:
    for value in values:
        try:
            if value is not None and str(value).strip():
                return float(value)
        except (TypeError, ValueError):
            pass
    return None


def schema_event(
    *, title: str, description: str, start: str, end: str, url: str,
    venue: str, address: str, latitude: float | None, longitude: float | None,
    cancelled: bool = False,
) -> dict[str, Any] | None:
    if not title or not start or latitude is None or longitude is None:
        return None
    return {
        "@type": "Event",
        "name": title,
        "description": description,
        "startDate": start,
        "endDate": end or start,
        "url": url,
        "eventStatus": "https://schema.org/EventCancelled" if cancelled else "https://schema.org/EventScheduled",
        "location": {
            "@type": "Place",
            "name": venue,
            "address": address,
            "geo": {"latitude": latitude, "longitude": longitude},
        },
    }


def map_openagenda(item: dict[str, Any]) -> dict[str, Any] | None:
    location = item.get("location") or {}
    timings = item.get("timings") or []
    first = timings[0] if timings else {}
    last = timings[-1] if timings else first
    coordinates = location.get("coordinates") or location.get("geo") or {}
    agenda = item.get("originAgenda") or item.get("sourceAgenda") or item.get("agenda") or {}
    slug = localized(item.get("slug"))
    agenda_slug = localized(agenda.get("slug"))
    url = localized(item.get("canonicalUrl") or item.get("url"))
    if not url and agenda_slug and slug:
        url = f"https://openagenda.com/{agenda_slug}/{slug}"
    address = localized(location.get("address"))
    if not address:
        address = ", ".join(filter(None, (
            localized(location.get("street")), localized(location.get("postalCode")),
            localized(location.get("city") or location.get("adminLevel4")),
        )))
    return schema_event(
        title=localized(item.get("title")),
        description=localized(item.get("longDescription") or item.get("description")),
        start=localized(first.get("begin") or first.get("start")),
        end=localized(last.get("end") or last.get("finish")),
        url=url or "https://openagenda.com",
        venue=localized(location.get("name")), address=address,
        latitude=number(location.get("latitude"), coordinates.get("latitude"), coordinates.get("lat")),
        longitude=number(location.get("longitude"), coordinates.get("longitude"), coordinates.get("lng")),
        cancelled=bool(item.get("removed")) or item.get("status") == 6,
    )


def openagenda_events(source: dict[str, Any], center: dict[str, Any], timeout: int) -> list[dict[str, Any]]:
    key = os.getenv(source.get("api_key_env", "OPENAGENDA_API_KEY"), "").strip()
    if not key:
        raise MissingCredentials("secret OPENAGENDA_API_KEY absent")
    radius_degrees = float(source.get("radius_km", 50)) / 85.0
    lat, lng = float(center["latitude"]), float(center["longitude"])
    query: list[tuple[str, str]] = [
        ("relative[]", "current"), ("relative[]", "upcoming"), ("monolingual", "fr"),
        ("detailed", "1"), ("size", str(source.get("page_size", 100))),
        ("geo[northEast][lat]", str(lat + radius_degrees)),
        ("geo[northEast][lng]", str(lng + radius_degrees)),
        ("geo[southWest][lat]", str(lat - radius_degrees)),
        ("geo[southWest][lng]", str(lng - radius_degrees)),
    ]
    endpoint = source.get("url", "https://api.openagenda.com/v2/events")
    payload = request_json(f"{endpoint}?{urllib.parse.urlencode(query)}", headers={"key": key}, timeout=timeout)
    return [mapped for item in payload.get("events", []) if (mapped := map_openagenda(item))]


def helloasso_token(source: dict[str, Any], timeout: int) -> str:
    client_id = os.getenv(source.get("client_id_env", "HELLOASSO_CLIENT_ID"), "").strip()
    client_secret = os.getenv(source.get("client_secret_env", "HELLOASSO_CLIENT_SECRET"), "").strip()
    if not client_id or not client_secret:
        raise MissingCredentials("secrets HELLOASSO_CLIENT_ID/HELLOASSO_CLIENT_SECRET absents")
    payload = request_json(
        "https://api.helloasso.com/oauth2/token",
        form={"grant_type": "client_credentials", "client_id": client_id, "client_secret": client_secret},
        timeout=timeout,
    )
    return str(payload["access_token"])


def map_helloasso(item: dict[str, Any]) -> dict[str, Any] | None:
    place = item.get("place") or item.get("location") or {}
    address_node = place.get("address") or item.get("address") or {}
    address = address_node if isinstance(address_node, str) else ", ".join(filter(None, (
        localized(address_node.get("address")), localized(address_node.get("zipCode")),
        localized(address_node.get("city")),
    )))
    organization = item.get("organization") or {}
    slug = localized(item.get("formSlug") or item.get("slug"))
    org_slug = localized(organization.get("organizationSlug") or organization.get("slug"))
    url = localized(item.get("url"))
    if not url and org_slug and slug:
        url = f"https://www.helloasso.com/associations/{org_slug}/evenements/{slug}"
    return schema_event(
        title=localized(item.get("title") or item.get("name")),
        description=localized(item.get("description")),
        start=localized(item.get("startDate")), end=localized(item.get("endDate")),
        url=url or "https://www.helloasso.com", venue=localized(place.get("name")), address=address,
        latitude=number(place.get("latitude"), address_node.get("latitude") if isinstance(address_node, dict) else None),
        longitude=number(place.get("longitude"), address_node.get("longitude") if isinstance(address_node, dict) else None),
        cancelled=localized(item.get("state")).lower() in {"disabled", "deleted"},
    )


def helloasso_events(source: dict[str, Any], center: dict[str, Any], timeout: int) -> list[dict[str, Any]]:
    del center
    token = helloasso_token(source, timeout)
    now = datetime.now(timezone.utc)
    body = {
        "formTypes": ["Event"],
        "formDepartments": source.get("departments", ["Landes", "Gers"]),
        "formEndDateMin": now.isoformat(),
        "formStartDateMax": (now + timedelta(days=int(source.get("horizon_days", 180)))).isoformat(),
    }
    payload = request_json(
        "https://api.helloasso.com/v5/directory/forms?pageSize=100",
        headers={"Authorization": f"Bearer {token}"}, body=body, timeout=timeout,
    )
    items = payload.get("data") or payload.get("forms") or []
    return [mapped for item in items if (mapped := map_helloasso(item))]


def map_eventbrite(item: dict[str, Any]) -> dict[str, Any] | None:
    venue = item.get("venue") or {}
    address_node = venue.get("address") or {}
    start, end = item.get("start") or {}, item.get("end") or {}
    return schema_event(
        title=localized(item.get("name")), description=localized(item.get("description") or item.get("summary")),
        start=localized(start.get("utc") or start.get("local")), end=localized(end.get("utc") or end.get("local")),
        url=localized(item.get("url")) or "https://www.eventbrite.com",
        venue=localized(venue.get("name")), address=localized(address_node.get("localized_address_display")),
        latitude=number(address_node.get("latitude"), venue.get("latitude")),
        longitude=number(address_node.get("longitude"), venue.get("longitude")),
        cancelled=localized(item.get("status")).lower() in {"canceled", "cancelled"},
    )


def eventbrite_events(source: dict[str, Any], center: dict[str, Any], timeout: int) -> list[dict[str, Any]]:
    del center
    token = os.getenv(source.get("token_env", "EVENTBRITE_PRIVATE_TOKEN"), "").strip()
    organizations = os.getenv(source.get("organizations_env", "EVENTBRITE_ORGANIZATION_IDS"), "").strip()
    if not token or not organizations:
        raise MissingCredentials("secrets EVENTBRITE_PRIVATE_TOKEN/EVENTBRITE_ORGANIZATION_IDS absents")
    found = []
    for organization in filter(None, (value.strip() for value in organizations.split(","))):
        for status in ("live", "started", "canceled"):
            query = urllib.parse.urlencode({
                "status": status,
                "time_filter": "all" if status == "canceled" else "current_future",
                "expand": "venue,organizer,category",
            })
            payload = request_json(
                f"https://www.eventbriteapi.com/v3/organizations/{organization}/events/?{query}",
                headers={"Authorization": f"Bearer {token}"}, timeout=timeout,
            )
            found.extend(mapped for item in payload.get("events", []) if (mapped := map_eventbrite(item)))
    return found


def collect_api_source(source: dict[str, Any], center: dict[str, Any], timeout: int) -> list[dict[str, Any]]:
    provider = source.get("type")
    if provider == "openagenda":
        return openagenda_events(source, center, timeout)
    if provider == "helloasso":
        return helloasso_events(source, center, timeout)
    if provider == "eventbrite":
        return eventbrite_events(source, center, timeout)
    raise ValueError(f"type de source non pris en charge: {provider}")
