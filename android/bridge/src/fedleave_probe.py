"""Debug-only read probe for the I02 bridge packaging experiment.

The probe is experimental and versioned. I03 replaces it with the typed
read facade from the shared core.
"""

from __future__ import annotations

import json
import sys

from fedleave.ledger import calculate_balances

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


def dated_balance(request: str) -> str:
    """Return the dated category balances for a supplied ledger JSON string."""
    try:
        payload = json.loads(request)
    except (TypeError, ValueError):
        return _error("INVALID_REQUEST", "request", "request must be a UTF-8 JSON string.")

    if not isinstance(payload, dict):
        return _error("INVALID_REQUEST", "request", "request must decode to a JSON object.")

    if payload.get("schema_version") != SCHEMA_VERSION:
        return _error("INVALID_REQUEST", "schema_version", "Unsupported schema_version.")

    for key in ("leave_year", "as_of"):
        if key not in payload:
            return _error("INVALID_REQUEST", key, f"Missing required field {key!r}.")

    leave_year = payload["leave_year"]
    if not isinstance(leave_year, dict):
        return _error("INVALID_REQUEST", "leave_year", "leave_year must be a JSON object.")

    as_of = payload["as_of"]
    if not isinstance(as_of, str):
        return _error("INVALID_REQUEST", "as_of", "as_of must be an ISO date string.")

    try:
        balances = calculate_balances(leave_year, until_date=as_of)
    except Exception as exc:
        return _error("CALCULATION_ERROR", "leave_year", type(exc).__name__)

    return json.dumps(
        {
            "schema_version": SCHEMA_VERSION,
            "ok": True,
            "year": leave_year.get("leave_year"),
            "as_of": as_of,
            "balances": dict(sorted(balances.items())),
        },
        sort_keys=True,
    )


def loaded_modules() -> str:
    """Return the sorted top-level names currently present in sys.modules."""
    names = {name.split(".")[0] for name in sys.modules if name}
    return json.dumps(sorted(names), sort_keys=True)
