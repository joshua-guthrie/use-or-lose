"""Shared write-side day-editing operations.

Pure standard library, importable by the CLI on desktop and by the Android
app: the ledger mutations behind ``add`` and ``set-day`` live in exactly one
place. Row construction delegates to ``ledger.create_transaction``.

``replace_day_group`` reproduces the CLI's ``set-day`` semantics when
``match_direction`` is None (the category-wide replacement) and the phone
editor's group semantics when a direction is supplied (one leave type +
direction only). ``append_entry`` reproduces the CLI's ``add`` row append.
"""

from __future__ import annotations

from typing import Any

from .ledger import add_transaction_to_leave_year, create_transaction


def is_protected(tx: dict, *, protected_sources: set[str] | None = None) -> bool:
    """Rows a replacement never consolidates."""
    return (
        tx.get("carryover_generated") is True
        or tx.get("source") == "expiration-rollover"
        or tx.get("source") in (protected_sources or set())
    )


def replace_day_group(
    ledger: dict,
    *,
    date: str,
    category: str,
    direction: str,
    hours: float,
    comment: str | None,
    status: str = "reconciled",
    source: str = "set-day",
    match_direction: str | None = None,
    protected_sources: set[str] | None = None,
) -> dict[str, Any]:
    """Replace matching day entries with one new row (none when hours is 0).

    ``match_direction`` is None for the CLI's category-wide replacement and
    a concrete direction for the phone editor's group scope. Protected rows
    are reported but never removed. A ``None`` comment keeps the first
    non-empty description of the replaced rows, matching the CLI. The id
    pool includes the removed rows so a created id advances past them.
    """
    transactions = ledger.setdefault("transactions", [])
    matching = [
        tx
        for tx in transactions
        if tx.get("date") == date
        and tx.get("category") == category
        and (match_direction is None or tx.get("direction") == match_direction)
    ]
    replaced = [tx for tx in matching if not is_protected(tx, protected_sources=protected_sources)]
    protected = [tx for tx in matching if is_protected(tx, protected_sources=protected_sources)]
    existing_comment = next(
        (str(tx.get("description", "")).strip() for tx in replaced if str(tx.get("description", "")).strip()),
        "",
    )
    description = existing_comment if comment is None else comment
    removed_ids = [str(tx.get("id", "")) for tx in replaced]
    existing_ids = [str(tx.get("id", "")) for tx in transactions]
    transactions[:] = [tx for tx in transactions if tx not in replaced]
    created_ids: list[str] = []
    if hours > 0:
        row = create_transaction(
            date=date,
            category=category,
            direction=direction,
            hours=hours,
            description=description,
            status=status,
            source=source,
            existing_ids=existing_ids,
        )
        add_transaction_to_leave_year(ledger, row)
        created_ids.append(row.id)
    return {
        "removed_transaction_ids": removed_ids,
        "created_transaction_ids": created_ids,
        "comment": description,
        "replaced_rows": replaced,
        "protected_rows": protected,
    }


def append_entry(
    ledger: dict,
    *,
    date: str,
    category: str,
    direction: str,
    hours: float,
    comment: str = "",
    status: str = "planned",
    source: str = "manual",
    existing_ids: list[str] | None = None,
) -> str:
    """Append one ordinary entry and return the created transaction id."""
    transactions = ledger.setdefault("transactions", [])
    ids = existing_ids if existing_ids is not None else [str(tx.get("id", "")) for tx in transactions]
    row = create_transaction(
        date=date,
        category=category,
        direction=direction,
        hours=hours,
        description=comment,
        status=status,
        source=source,
        existing_ids=ids,
    )
    add_transaction_to_leave_year(ledger, row)
    return row.id
