"""Debug-only bridge adapter over the versioned read facade."""

from __future__ import annotations

import json
import sys

from useorlose.read_api import ReadApiError, read

SCHEMA_VERSION = 1


def _error(code: str, field: str, message: str) -> str:
    return json.dumps(
        {
            "schema_version": SCHEMA_VERSION,
            "ok": False,
            "error": {"code": code, "field": field, "message": message},
        },
        sort_keys=True,
    )


def call(payload: str) -> str:
    """Run one read operation against parsed store files and return JSON."""
    try:
        body = json.loads(payload)
    except (TypeError, ValueError):
        return _error("INVALID_REQUEST", "request", "request must be a UTF-8 JSON string.")
    if not isinstance(body, dict):
        return _error("INVALID_REQUEST", "request", "request must decode to a JSON object.")
    if body.get("schema_version") != SCHEMA_VERSION:
        return _error("INVALID_REQUEST", "schema_version", "Unsupported schema_version.")

    local_date = body.get("local_date")
    raw_files = body.get("files")
    request = body.get("request")
    if not isinstance(local_date, str):
        return _error("INVALID_REQUEST", "local_date", "local_date must be an ISO date string.")
    if not isinstance(raw_files, dict):
        return _error("INVALID_REQUEST", "files", "files must be an object of JSON strings.")
    if not isinstance(request, dict):
        return _error("INVALID_REQUEST", "request", "request must be an object.")

    files = {}
    for name, text in raw_files.items():
        if not isinstance(name, str):
            return _error("INVALID_REQUEST", "files", "File names must be strings.")
        try:
            files[name] = json.loads(text)
        except (TypeError, ValueError):
            return _error("INVALID_REQUEST", "files", f"{name} must be a UTF-8 JSON string.")

    try:
        return json.dumps(read(files, request, local_date=local_date), sort_keys=True)
    except ReadApiError as exc:
        return _error(exc.code, exc.field, str(exc))


def loaded_modules() -> str:
    """Return the sorted top-level names currently present in sys.modules."""
    names = {name.split(".")[0] for name in sys.modules if name}
    return json.dumps(sorted(names), sort_keys=True)
