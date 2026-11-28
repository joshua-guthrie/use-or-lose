"""Android write-side day-editing adapter over the approved desktop semantics.

Mirrors the CLI writes for the phone without importing the CLI stack
(typer, rich) or pydantic: ``edit_day`` follows ``commands/transactions.py``
``_set_day_values`` scoped to one category+direction group, and ``add_entry``
follows ``add``. Row shape and validation mirror the desktop ``Transaction``
model dump; IDs and direction/status/category rules delegate to the bundled
pure modules. The final store publish remains the Kotlin I04 writer.
"""

from __future__ import annotations

import json
import math

from useorlose.categories import CATEGORY_POLICIES
from useorlose.cross_year import AccountingError
from useorlose.ledger import generate_transaction_id
from useorlose.transaction_effects import TRANSACTION_DIRECTIONS, TRANSACTION_STATUSES
from useorlose.validation import sanitize_text

SCHEMA_VERSION = 1

# Rows the day editor never consolidates: generated carryover/expiration
# provenance and system-generated records stay as separate activity.
_PROTECTED_SOURCES = {"auto_accrual", "forced-balance", "expiration-engine"}

_EDIT_PROTECTED_FIELDS = ("carryover_generated",)


def _error(code: str, field: str, message: str) -> str:
    return json.dumps(
        {
            "schema_version": SCHEMA_VERSION,
            "ok": False,
            "error": {"code": code, "field": field, "message": message},
        },
        sort_keys=True,
    )


def _require_str(body: dict, field: str) -> str:
    value = body.get(field)
    if not isinstance(value, str):
        raise AccountingError("INVALID_REQUEST", f"{field} must be a string.")
    return value


def _require_int(body: dict, field: str) -> int:
    value = body.get(field)
    if isinstance(value, bool) or not isinstance(value, int):
        raise AccountingError("INVALID_REQUEST", f"{field} must be an integer.")
    return value


def _require_number(body: dict, field: str) -> float:
    value = body.get(field)
    try:
        number = float(value)
    except (TypeError, ValueError) as exc:
        raise AccountingError("INVALID_REQUEST", f"{field} must be a number.") from exc
    if not math.isfinite(number):
        raise AccountingError("INVALID_REQUEST", f"{field} must be finite.")
    return number


def _transaction_row(
    date: str,
    category: str,
    direction: str,
    hours: float,
    description: str,
    status: str,
    source: str,
    existing_ids: list[str],
) -> dict:
    """The desktop Transaction model dump, built without pydantic.

    Validation mirrors the model validators: category, direction and status
    must be known, hours finite and nonnegative. Timestamps are deterministic
    (date-derived) so phone candidates are reproducible; the parity gate
    normalizes the CLI's now()-timestamps before comparing.
    """
    if category not in CATEGORY_POLICIES:
        raise AccountingError("INVALID_CATEGORY", "category", f"Unknown category: {category}")
    if direction not in TRANSACTION_DIRECTIONS:
        raise AccountingError("INVALID_DIRECTION", "direction", f"Unknown direction: {direction}")
    if status not in TRANSACTION_STATUSES:
        raise AccountingError("INVALID_STATUS", "status", f"Unknown status: {status}")
    if not math.isfinite(hours) or hours < 0:
        raise AccountingError("INVALID_HOURS", "hours", "Hours must be a finite nonnegative number.")
    if category == "fmla" and direction != "used":
        raise AccountingError("FMLA_USAGE_ONLY", "direction", "FMLA has used transactions only")
    if category == "ppl" and direction not in {"earned", "used"}:
        raise AccountingError("INVALID_PPL_DIRECTION", "direction", "PPL accepts earned and used transactions only")
    return {
        "id": generate_transaction_id(date, existing_ids),
        "date": date,
        "category": category,
        "direction": direction,
        "hours": hours,
        "description": description,
        "status": status,
        "source": source,
        "created_at": f"{date}T00:00:00",
        "updated_at": f"{date}T00:00:00",
        "expiration_date": None,
        "expiration_pay_period": None,
        "earned_transaction_id": None,
        "expiration_extension_reason": None,
        "expiration_extended_at": None,
    }


def _parse_ledger(body: dict) -> tuple[int, dict]:
    year = _require_int(body, "year")
    raw = body.get("ledger")
    if not isinstance(raw, str):
        raise AccountingError("INVALID_REQUEST", "ledger must be the leave-year JSON string.")
    try:
        parsed = json.loads(raw)
    except ValueError as exc:
        raise AccountingError("INVALID_REQUEST", f"ledger is not valid JSON: {exc}") from exc
    if not isinstance(parsed, dict):
        raise AccountingError("INVALID_REQUEST", "ledger must decode to a JSON object.")
    if parsed.get("leave_year") != year:
        raise AccountingError("YEAR_MISMATCH", "year", "The ledger does not match the requested leave year.")
    return year, parsed


def _is_protected(tx: dict) -> bool:
    return (
        tx.get("carryover_generated") is True
        or tx.get("source") in _PROTECTED_SOURCES
        or tx.get("source") == "expiration-rollover"
    )


def _edit_day(body: dict) -> tuple[dict, dict]:
    """Replace one ordinary category+direction group on a date (set-day semantics)."""
    year, ledger = _parse_ledger(body)
    date = _require_str(body, "date")
    category = _require_str(body, "category")
    direction = _require_str(body, "direction")
    hours = _require_number(body, "hours")
    comment = body.get("comment")
    if comment is not None and not isinstance(comment, str):
        raise AccountingError("INVALID_REQUEST", "comment must be a string or null.")
    if hours < 0:
        raise AccountingError("INVALID_HOURS", "hours", "Hours must be zero or positive.")

    transactions = ledger.setdefault("transactions", [])
    group = [
        tx
        for tx in transactions
        if tx.get("date") == date and tx.get("category") == category and tx.get("direction") == direction
    ]
    protected = [tx for tx in group if _is_protected(tx)]
    editable = [tx for tx in group if not _is_protected(tx)]

    # Consolidation is only safe when the editable rows agree on stored
    # metadata; otherwise the review routes to individual correction (I09).
    metadata = {
        (
            str(tx.get("status", "")),
            str(tx.get("source", "")),
            str(tx.get("description", "")).strip(),
        )
        for tx in editable
    }
    if len(metadata) > 1:
        return {}, {
            "kind": "mixed_metadata",
            "year": year,
            "date": date,
            "category": category,
            "direction": direction,
            "rows": [dict(tx) for tx in editable],
            "protected_rows": [dict(tx) for tx in protected],
        }

    existing_comment = next(
        (str(tx.get("description", "")).strip() for tx in editable if str(tx.get("description", "")).strip()),
        "",
    )
    if comment is None:
        description = existing_comment
    else:
        description = sanitize_text(comment, field_name="comment")

    removed_ids = [str(tx.get("id", "")) for tx in editable]
    # The desktop captures the id pool before replacement, so the created
    # row advances past the removed ids (20260116-002, not -001).
    existing_ids = [str(tx.get("id", "")) for tx in transactions]
    transactions[:] = [tx for tx in transactions if tx not in editable]

    created_ids: list[str] = []
    if hours > 0:
        row = _transaction_row(
            date=date,
            category=category,
            direction=direction,
            hours=hours,
            description=description,
            status="reconciled",
            source="set-day",
            existing_ids=existing_ids,
        )
        transactions.append(row)
        created_ids.append(row["id"])

    before = sum(float(tx.get("hours", 0.0)) for tx in editable)
    files = {f"leave_years/{year}.json": json.dumps(ledger, sort_keys=True)}
    preview = {
        "kind": "edit_day",
        "year": year,
        "date": date,
        "category": category,
        "direction": direction,
        "before_hours": before,
        "after_hours": hours if hours > 0 else 0.0,
        "removed_transaction_ids": removed_ids,
        "created_transaction_ids": created_ids,
        "comment": description,
        "protected_rows": [dict(tx) for tx in protected],
    }
    return files, preview


def _add_entry(body: dict) -> tuple[dict, dict]:
    """Append one ordinary entry (desktop add semantics)."""
    year, ledger = _parse_ledger(body)
    date = _require_str(body, "date")
    category = _require_str(body, "category")
    direction = _require_str(body, "direction")
    hours = _require_number(body, "hours")
    description = sanitize_text(_require_str(body, "comment"), field_name="comment")
    status = body.get("status") or "planned"
    if not isinstance(status, str):
        raise AccountingError("INVALID_REQUEST", "status must be a string.")
    if hours <= 0:
        raise AccountingError("INVALID_HOURS", "hours", "Hours must be positive.")

    transactions = ledger.setdefault("transactions", [])
    existing_ids = [str(tx.get("id", "")) for tx in transactions]
    row = _transaction_row(
        date=date,
        category=category,
        direction=direction,
        hours=hours,
        description=description,
        status=status,
        source="manual",
        existing_ids=existing_ids,
    )
    transactions.append(row)
    files = {f"leave_years/{year}.json": json.dumps(ledger, sort_keys=True)}
    preview = {
        "kind": "add_entry",
        "year": year,
        "date": date,
        "category": category,
        "direction": direction,
        "hours": hours,
        "comment": description,
        "status": status,
        "created_transaction_ids": [row["id"]],
    }
    return files, preview


_OPERATIONS = {
    "edit_day": _edit_day,
    "add_entry": _add_entry,
}


def edit(payload: str) -> str:
    """Run one editing preview and return the candidate files plus the preview."""
    try:
        body = json.loads(payload)
    except (TypeError, ValueError):
        return _error("INVALID_REQUEST", "request", "request must be a UTF-8 JSON string.")
    if not isinstance(body, dict):
        return _error("INVALID_REQUEST", "request", "request must decode to a JSON object.")
    if body.get("schema_version") != SCHEMA_VERSION:
        return _error("INVALID_REQUEST", "schema_version", "Unsupported schema_version.")
    operation = body.get("operation")
    function = _OPERATIONS.get(operation) if isinstance(operation, str) else None
    if function is None:
        return _error("UNKNOWN_OPERATION", "operation", f"Unsupported operation: {operation}.")
    try:
        files, preview = function(body)
    except AccountingError as exc:
        return _error(exc.code, getattr(exc, "field", None) or "request", str(exc))
    except (ValueError, TypeError, KeyError) as exc:
        return _error("INVALID_REQUEST", "request", str(exc))
    return json.dumps(
        {
            "schema_version": SCHEMA_VERSION,
            "ok": True,
            "result": {"files": files, "preview": preview},
        },
        sort_keys=True,
    )


def loaded_modules() -> str:
    """Return the sorted top-level names currently present in sys.modules."""
    import sys

    names = {name.split(".")[0] for name in sys.modules if name}
    return json.dumps(sorted(names), sort_keys=True)
