"""Versioned read-only facade over canonical FedLeave store files.

Every function takes a complete snapshot of parsed store files, an explicit
request, and an explicit local date. The facade never opens paths, locks,
writes, or mutates its inputs. Desktop CLI commands delegate their read
calculations here so the phone bridge and the CLI share one result pipeline.
"""

from __future__ import annotations

import calendar as _calendar
import re
from collections.abc import Callable, Mapping
from datetime import date as _date
from datetime import timedelta as _timedelta
from typing import Any

from .cross_year import AccountingError, resolve_snapshot_from
from .ledger import (
    _parse_iso_date,
    calculate_balances,
    calculate_daily_activity,
    calculate_pay_period_activity,
    calculate_use_or_lose,
    ensure_automatic_accruals,
)
from .payperiods import normalize_pay_period, pay_period_pay_date
from .transaction_effects import signed_balance_effect, transaction_is_effective, transaction_is_physical_absence

SCHEMA_VERSION = 1

_DISPLAY_CATEGORY_LABELS = {
    "annual": "A",
    "sick": "S",
    "overtime": "OT",
    "comp": "Comp",
    "credit": "Cr",
    "travel_comp": "TC",
    "admin": "Admin",
    "lwop": "LWOP",
    "military": "Mil",
    "court": "Court",
    "religious_comp": "RC",
    "time_off_award": "TOA",
    "excused": "Exc",
    "holiday": "Hol",
    "flex": "Flex",
    "other": "Other",
    "restored_annual": "RA",
    "ppl": "PPL",
    "fmla": "FMLA",
}

_LEAVE_YEAR_FILE = re.compile(r"^leave_years/(\d+)\.json$")
_HOLIDAY_CACHE_FILE = re.compile(r"^holiday_cache/.*\.json$")


class ReadApiError(ValueError):
    """A structured read failure with a machine-readable code and field."""

    def __init__(self, code: str, field: str, message: str):
        super().__init__(message)
        self.code = code
        self.field = field


def _config(files: Mapping[str, Any]) -> dict[str, Any]:
    if not isinstance(files, Mapping):
        raise ReadApiError("INVALID_SNAPSHOT", "files", "files must be a mapping of relative names to JSON objects.")
    config = files.get("config.json")
    if config is None:
        raise ReadApiError("MISSING_CONFIG", "config.json", "Config file not found.")
    if not isinstance(config, dict):
        raise ReadApiError("INVALID_SNAPSHOT", "config.json", "config.json must be a JSON object.")
    return config


def _load_year(files: Mapping[str, Any], year: int) -> dict[str, Any]:
    ledger = files.get(f"leave_years/{year}.json")
    if ledger is None:
        raise ReadApiError("LEAVE_YEAR_NOT_FOUND", "leave_year", f"Leave year file not found: {year}.")
    if not isinstance(ledger, dict):
        raise ReadApiError("INVALID_SNAPSHOT", "leave_year", f"leave_years/{year}.json must be a JSON object.")
    return ledger


def _find_year(files: Mapping[str, Any], target: _date) -> int:
    for name in sorted(files):
        match = _LEAVE_YEAR_FILE.match(str(name))
        if not match:
            continue
        year = int(match.group(1))
        ledger = files[name]
        if not isinstance(ledger, dict):
            raise ReadApiError("INVALID_SNAPSHOT", name, f"{name} must be a JSON object.")
        try:
            start = _parse_iso_date(str(ledger.get("leave_year_start", "")))
            end = _parse_iso_date(str(ledger.get("leave_year_end", "")))
        except ValueError:
            continue
        if start <= target <= end:
            return year
    raise ReadApiError("LEAVE_YEAR_NOT_FOUND", "as_of", f"No leave year covers {target.isoformat()}.")


def _snapshot(files: Mapping[str, Any], year: int) -> dict[str, Any]:
    config = _config(files)
    try:
        return resolve_snapshot_from(lambda requested: _load_year(files, requested), year, config)
    except AccountingError as exc:
        raise ReadApiError(exc.code, "leave_year", str(exc)) from exc


def _physical_absence_hours(ledger: dict[str, Any], start: str, end: str) -> float:
    return sum(
        float(row["hours"])
        for row in ledger.get("transactions", [])
        if start <= row["date"] <= end and transaction_is_physical_absence(row)
    )


def _parental_payload(ledger: dict[str, Any], as_of: str | None = None, *, local_date: str) -> dict[str, Any] | None:
    from .parental import parental_report

    if not any(tx.get("category") in {"ppl", "fmla"} for tx in ledger.get("transactions", [])) and not getattr(
        ledger, "entitlement_history", []
    ):
        return None
    return parental_report(
        ledger,
        getattr(ledger, "config", ledger.get("snapshot_rules")),
        as_of=_parse_iso_date(as_of) if as_of else _parse_iso_date(local_date),
    )


def _display_line(transaction: dict[str, Any]) -> str:
    category_value: Any = transaction.get("category")
    category = category_value if isinstance(category_value, str) else str(category_value)
    label = _DISPLAY_CATEGORY_LABELS.get(category, category)
    signed_hours = signed_balance_effect(transaction)
    hours = abs(signed_hours)
    sign = "-" if signed_hours < 0 else "+"
    return f"{label} {sign}{hours:.1f}"


def _month_bounds(year: int, month: int) -> tuple[_date, _date, _date, _date]:
    if month < 1 or month > 12:
        raise ReadApiError("INVALID_MONTH", "month", "--month must be between 1 and 12.")
    month_start = _date(year, month, 1)
    month_end = _date(year, month, _calendar.monthrange(year, month)[1])
    calendar_start = month_start - _timedelta(days=(month_start.weekday() + 1) % 7)
    calendar_end = month_end + _timedelta(days=(5 - month_end.weekday()) % 7)
    return month_start, month_end, calendar_start, calendar_end


def _names_from_rows(rows: list[dict[str, Any]]) -> dict[str, str]:
    names: dict[str, str] = {}
    for holiday in rows:
        display_date = holiday.get("display_date") or holiday.get("observed_date") or holiday.get("actual_date")
        name = holiday.get("name") or holiday.get("short_name")
        if display_date and name:
            names[str(display_date)] = str(name)
    return names


def _holiday_rows_from_files(files: Mapping[str, Any]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for name in sorted(files):
        if not _HOLIDAY_CACHE_FILE.match(str(name)):
            continue
        cache = files[name]
        if not isinstance(cache, dict) or not isinstance(cache.get("holidays"), list):
            raise ReadApiError(
                "INVALID_SNAPSHOT", str(name), "Holiday cache must be a JSON object with a holidays list."
            )
        for row in cache["holidays"]:
            if not isinstance(row, dict):
                raise ReadApiError("INVALID_SNAPSHOT", str(name), "Holiday records must be objects.")
            rows.append(row)
    return rows


def _transactions_by_date(leave_year: dict[str, Any], start: _date, end: _date) -> dict[str, list[dict[str, Any]]]:
    entries: dict[str, list[dict[str, Any]]] = {}
    for transaction in leave_year.get("transactions", []):
        if transaction.get("carryover_generated") or transaction.get("source") == "expiration-rollover":
            continue
        if not transaction_is_effective(transaction):
            continue
        try:
            transaction_date = _parse_iso_date(str(transaction.get("date", "")))
        except ValueError:
            continue
        if not start <= transaction_date <= end:
            continue
        day_entries = entries.setdefault(transaction_date.isoformat(), [])
        day_entries.append(
            {
                "id": transaction.get("id"),
                "category": transaction.get("category"),
                "direction": transaction.get("direction"),
                "hours": float(transaction.get("hours", 0.0)),
                "status": transaction.get("status"),
                "source": transaction.get("source"),
                "description": transaction.get("description", ""),
            }
        )
    for day_entries in entries.values():
        day_entries.sort(key=lambda entry: str(entry.get("id") or ""))
    return entries


def _pay_periods_for_range(
    leave_year: dict[str, Any],
    range_start: _date,
    range_end: _date,
    month_start: _date,
    month_end: _date,
) -> list[dict[str, Any]]:
    rows = []
    for pay_period in leave_year.get("pay_periods", []):
        try:
            period_start = _parse_iso_date(str(pay_period["start_date"]))
            period_end = _parse_iso_date(str(pay_period["end_date"]))
        except (KeyError, ValueError):
            continue
        if period_end < range_start or period_start > range_end:
            continue
        activity = calculate_pay_period_activity(leave_year, period_start.isoformat())
        totals = {}
        categories = sorted({*activity["earned"], *activity["used"], *activity["worked"], *activity["net"]})
        for category in categories:
            totals[category] = {
                "earned": activity["earned"].get(category, 0.0),
                "used": activity["used"].get(category, 0.0),
                "worked": activity["worked"].get(category, 0.0),
                "net": activity["net"].get(category, 0.0),
            }
        rows.append(
            {
                "number": pay_period.get("pay_period_number"),
                "start": period_start.isoformat(),
                "end": period_end.isoformat(),
                "pay_date": pay_period_pay_date(pay_period),
                "physical_absence_hours": _physical_absence_hours(
                    leave_year, period_start.isoformat(), period_end.isoformat()
                ),
                "touches_display_month": not (period_end < month_start or period_start > month_end),
                "totals": totals,
                "ending_balances": dict(
                    sorted(calculate_balances(leave_year, until_date=period_end.isoformat()).items())
                ),
            }
        )
    return rows


def _dates_from_pay_periods(pay_periods: list[dict[str, Any]], field: str) -> set[str]:
    return {str(period.get(field)) for period in pay_periods if period.get(field)}


def _is_leave_year_end_keyword(value: str | None) -> bool:
    return bool(value and value.strip().lower() == "leave-year-end")


def _resolve_date(value: str | None, leave_year: dict[str, Any], *, default_today: bool, local_date: str) -> str | None:
    if value is None:
        return local_date if default_today else None
    if _is_leave_year_end_keyword(value):
        leave_year_end = leave_year.get("leave_year_end")
        if not leave_year_end:
            raise ReadApiError("INVALID_DATE", "as_of", "Leave year is missing leave_year_end.")
        return _parse_iso_date(str(leave_year_end)).isoformat()
    if value.strip().lower() == "today":
        return local_date
    return _parse_iso_date(value).isoformat()


def _parse_date(value: Any, field: str, local_date: str) -> _date:
    if not isinstance(value, str):
        raise ReadApiError("INVALID_DATE", field, f"{field} must be an ISO date string.")
    if value.strip().lower() == "today":
        return _parse_iso_date(local_date)
    try:
        return _parse_iso_date(value)
    except ValueError:
        raise ReadApiError(
            "INVALID_DATE", field, f"Invalid date: {value}. Use YYYY-MM-DD, for example 2026-01-11, or today."
        ) from None


def dated_balance(
    files: Mapping[str, Any],
    request: Mapping[str, Any],
    *,
    local_date: str,
    snapshot: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """Return category balances through a cutoff, matching the CLI JSON."""
    as_of_raw = request.get("as_of")
    project_to_raw = request.get("project_to")
    use_or_lose_flag = bool(request.get("use_or_lose"))
    project_flag = bool(request.get("project"))

    if snapshot is None:
        year = request.get("year")
        if year is not None and (not isinstance(year, int) or isinstance(year, bool)):
            raise ReadApiError("INVALID_REQUEST", "year", "year must be an integer.")
        if year is None:
            target = local_date if as_of_raw is None or _is_leave_year_end_keyword(as_of_raw) else as_of_raw
            year = _find_year(files, _parse_date(target, "as_of", local_date))
        leave_year = _snapshot(files, year)
    else:
        leave_year = snapshot
        year = request.get("year") if isinstance(request.get("year"), int) else leave_year.get("leave_year")

    as_of = _resolve_date(as_of_raw, leave_year, default_today=True, local_date=local_date)
    project_to = _resolve_date(project_to_raw, leave_year, default_today=False, local_date=local_date)
    accrual_through = as_of
    assert accrual_through is not None
    added_accruals = ensure_automatic_accruals(leave_year, accrual_through)

    include_projected = project_flag or use_or_lose_flag or project_to is not None
    balances = calculate_balances(
        leave_year,
        until_date=as_of,
        include_projected=include_projected,
        project_until=project_to,
    )
    projection_label = project_to or leave_year.get("leave_year_end") or "year end"
    use_or_lose_data = None
    if use_or_lose_flag:
        config = getattr(leave_year, "config", leave_year.get("snapshot_rules"))
        use_or_lose_data = calculate_use_or_lose(leave_year, balances, config)

    parental = _parental_payload(
        leave_year,
        (project_to or leave_year.get("leave_year_end")) if include_projected else as_of,
        local_date=local_date,
    )
    return {
        "warnings": [*getattr(leave_year, "warnings", []), *((parental or {}).get("warnings", []))],
        "parental": parental,
        "year": year,
        "as_of": as_of,
        "projected": include_projected,
        "project_to": projection_label if include_projected else None,
        "balances": dict(sorted(balances.items())),
        "automatic_accruals_posted": added_accruals,
        "automatic_accruals_posted_through": accrual_through,
        "use_or_lose": use_or_lose_data,
    }


def pay_period(
    files: Mapping[str, Any],
    request: Mapping[str, Any],
    *,
    local_date: str,
    snapshot: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """Return one pay period's activity and ending balances, matching the CLI JSON."""
    date_value = _parse_date(request.get("date"), "date", local_date).isoformat()
    if snapshot is None:
        year = request.get("year")
        if year is None:
            year = _find_year(files, _date.fromisoformat(date_value))
        leave_year = _snapshot(files, year)
    else:
        leave_year = snapshot
        year = request.get("year") if isinstance(request.get("year"), int) else leave_year.get("leave_year")

    pay_period_data = calculate_pay_period_activity(leave_year, date_value)["pay_period"]
    accrual_through = pay_period_data.get("accrual_date") or pay_period_data.get("end_date")
    added_accruals = ensure_automatic_accruals(leave_year, accrual_through)
    activity = calculate_pay_period_activity(leave_year, date_value)
    pay_period_data = activity["pay_period"]
    ending_balances = calculate_balances(leave_year, until_date=pay_period_data.get("end_date"))
    daily_activity_rows: list[dict[str, Any]] | None = None
    if bool(request.get("daily")):
        daily_activity_rows = []
        current = _date.fromisoformat(pay_period_data["start_date"])
        end = _date.fromisoformat(pay_period_data["end_date"])
        while current <= end:
            day = current.isoformat()
            day_calculation = calculate_daily_activity(leave_year, day)
            daily_activity_rows.append({"date": day, **day_calculation})
            current += _timedelta(days=1)

    activity_output = dict(activity)
    activity_output["pay_period"] = normalize_pay_period(activity["pay_period"])
    return {
        "warnings": getattr(leave_year, "warnings", []),
        "year": year,
        "date": date_value,
        "pay_period": normalize_pay_period(pay_period_data),
        "activity": activity_output,
        "daily_activity": daily_activity_rows,
        "ending_balances": dict(sorted(ending_balances.items())),
        "parental": _parental_payload(leave_year, pay_period_data["end_date"], local_date=local_date),
        "physical_absence_hours": _physical_absence_hours(
            leave_year, pay_period_data["start_date"], pay_period_data["end_date"]
        ),
        "automatic_accruals_posted": added_accruals,
        "automatic_accruals_posted_through": accrual_through,
    }


def day_activity(
    files: Mapping[str, Any],
    request: Mapping[str, Any],
    *,
    local_date: str,
    snapshot: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """Return one date's activity, matching the CLI JSON."""
    date_value = _parse_date(request.get("date"), "date", local_date).isoformat()
    if snapshot is None:
        year = request.get("year")
        if year is None:
            year = _find_year(files, _date.fromisoformat(date_value))
        leave_year = _snapshot(files, year)
    else:
        leave_year = snapshot
        year = request.get("year") if isinstance(request.get("year"), int) else leave_year.get("leave_year")

    activity = calculate_daily_activity(leave_year, date_value)
    has_activity = any(activity.values())
    return {
        "year": year,
        "date": date_value,
        "activity": activity,
        "has_activity": has_activity,
        "physical_absence_hours": _physical_absence_hours(leave_year, date_value, date_value) if has_activity else 0.0,
        "parental": _parental_payload(leave_year, date_value, local_date=local_date),
    }


def month_view(
    files: Mapping[str, Any],
    request: Mapping[str, Any],
    *,
    local_date: str,
    snapshot: dict[str, Any] | None = None,
    holidays: dict[str, str] | None = None,
) -> dict[str, Any]:
    """Return a frozen month view, matching the CLI JSON."""
    year = request.get("year")
    month = request.get("month")
    if not isinstance(year, int) or isinstance(year, bool):
        raise ReadApiError("INVALID_REQUEST", "year", "year must be an integer.")
    if not isinstance(month, int) or isinstance(month, bool):
        raise ReadApiError("INVALID_REQUEST", "month", "month must be an integer.")
    calendar_year_value: Any = request.get("calendar_year")
    display_year = calendar_year_value if isinstance(calendar_year_value, int) else year
    month_start, month_end, calendar_start, calendar_end = _month_bounds(display_year, month)

    if snapshot is None:
        leave_year = _snapshot(files, year)
    else:
        leave_year = snapshot

    added_accruals = ensure_automatic_accruals(leave_year, calendar_end.isoformat())
    if holidays is None:
        holiday_names = _names_from_rows(_holiday_rows_from_files(files))
    else:
        holiday_names = holidays
    transactions = _transactions_by_date(leave_year, calendar_start, calendar_end)
    pay_period_rows = _pay_periods_for_range(leave_year, calendar_start, calendar_end, month_start, month_end)
    pay_dates = sorted(
        date_key
        for date_key in _dates_from_pay_periods(pay_period_rows, "pay_date")
        if calendar_start <= _parse_iso_date(date_key) <= calendar_end
    )
    pay_period_end_dates = _dates_from_pay_periods(pay_period_rows, "end")
    today = local_date

    days = []
    current = calendar_start
    while current <= calendar_end:
        date_key = current.isoformat()
        entries = transactions.get(date_key, [])
        days.append(
            {
                "date": date_key,
                "in_display_month": month_start <= current <= month_end,
                "holiday_name": holiday_names.get(date_key),
                "is_today": date_key == today,
                "is_payday": date_key in pay_dates,
                "is_pay_period_end": date_key in pay_period_end_dates,
                "entries": entries,
                "display_lines": [_display_line(entry) for entry in entries],
            }
        )
        current += _timedelta(days=1)

    today_balance = calculate_balances(leave_year, until_date=today)
    projected_balances = calculate_balances(
        leave_year,
        until_date=today,
        include_projected=True,
        project_until=leave_year.get("leave_year_end"),
    )
    config = getattr(leave_year, "config", leave_year.get("snapshot_rules"))
    use_or_lose = calculate_use_or_lose(leave_year, projected_balances, config)

    return {
        "year": display_year,
        "month": month,
        "today": today,
        "month_start": month_start.isoformat(),
        "month_end": month_end.isoformat(),
        "calendar_start": calendar_start.isoformat(),
        "calendar_end": calendar_end.isoformat(),
        "days": days,
        "pay_periods": pay_period_rows,
        "pay_dates": pay_dates,
        "pay_period_end_dates": sorted(pay_period_end_dates),
        "warnings": getattr(leave_year, "warnings", []),
        "parental": _parental_payload(leave_year, local_date=local_date),
        "balance_as_of_today": {
            "parental": _parental_payload(leave_year, local_date=local_date),
            "warnings": getattr(leave_year, "warnings", []),
            "as_of": today,
            "balances": dict(sorted(today_balance.items())),
        },
        "projected_balance": {
            "parental": _parental_payload(leave_year, leave_year.get("leave_year_end"), local_date=local_date),
            "project_to": leave_year.get("leave_year_end"),
            "balances": dict(sorted(projected_balances.items())),
            "use_or_lose": use_or_lose,
        },
        "automatic_accruals_posted": added_accruals,
        "automatic_accruals_posted_through": calendar_end.isoformat(),
    }


_OPERATIONS: dict[str, Callable[..., dict[str, Any]]] = {
    "dated_balance": dated_balance,
    "pay_period": pay_period,
    "day_activity": day_activity,
    "month_view": month_view,
}


def read(files: Mapping[str, Any], request: Mapping[str, Any], *, local_date: str) -> dict[str, Any]:
    """Dispatch one read operation and return the versioned result envelope."""
    if not isinstance(request, Mapping):
        raise ReadApiError("INVALID_REQUEST", "request", "request must be an object.")
    _parse_date(local_date, "local_date", local_date)
    operation: Any = request.get("operation")
    function = _OPERATIONS.get(operation) if isinstance(operation, str) else None
    if function is None:
        raise ReadApiError("UNKNOWN_OPERATION", "operation", f"Unsupported operation: {operation}.")
    return {"schema_version": SCHEMA_VERSION, "ok": True, "result": function(files, request, local_date=local_date)}
