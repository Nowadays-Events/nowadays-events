#!/usr/bin/env python3
"""API HTTP locale en lecture seule pour le flux Nowadays."""

from __future__ import annotations

import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit


class EventsHandler(BaseHTTPRequestHandler):
    feed_path: Path

    def do_GET(self) -> None:
        path = urlsplit(self.path).path
        if path == "/health":
            self.send_json({"status": "ok"})
            return
        if path == "/events":
            try:
                payload = json.loads(self.feed_path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError) as error:
                self.send_json({"error": str(error)}, status=503)
                return
            self.send_json(payload)
            return
        self.send_json({"error": "not found"}, status=404)

    def send_json(self, payload: object, status: int = 200) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, message: str, *args: object) -> None:
        print(f"{self.address_string()} - {message % args}", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    root = Path(__file__).resolve().parent
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--feed", type=Path, default=root / "output" / "events.json")
    args = parser.parse_args()
    EventsHandler.feed_path = args.feed
    server = ThreadingHTTPServer((args.host, args.port), EventsHandler)
    print(f"Nowadays API listening on http://{args.host}:{args.port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
