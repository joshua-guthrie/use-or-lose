"""Android write-side creation adapter over the approved desktop builders.

Reuses the pure accounting builders already bundled for the read path —
``cross_year.manual_ledger``, ``ledger.upsert_accrual_rate_change``,
``ledger.calculate_balances``, ``ledger.create_transaction`` — so the phone
creates desktop-compatible schema-v1 ledgers without importing the desktop
CLI stack (typer, rich, pydantic) and without adding any accounting logic
of its own. The final store publish remains the Kotlin I04 writer.
"""

from __future__ import annotations

import json
import math
from copy import deepcopy

from useorlose.cross_year import AccountingError
from useorlose.expirations import EPSILON
from useorlose.ledger import (
    accrual_hours_for_date,
    calculate_balances,
    generate_transaction_id,
    upsert_accrual_rate_change,
)
from useorlose.payperiods import generate_pay_periods, leave_year_period_count
from useorlose.rule_defaults import DEFAULT_RULES

SCHEMA_VERSION = 1

SETUP_REASON = "Balances as of Today \u00b7 leave year setup"

_DEFAULT_DEFAULTS = {
    "annual_leave_accrual_hours": 6.0,
    "sick_leave_accrual_hours": 4.0,
    "time_increment_hours": 0.25,
    "workday_start": "07:00",
    "workday_end": "15:30",
    "lunch_start": "11:30",
    "lunch_end": "12:00",
    "normal_workdays": ["mon", "tue", "wed", "thu", "fri"],
}

_DEFAULT_HOLIDAYS = {
    "allow_manual_override": True,
    "cache_dir": "holiday_cache",
    "cache_enabled": True,
    "country": "US",
    "enabled": True,
    "fail_if_holidays_unavailable": False,
    "ics_url": "https://www.opm.gov/policy-data-oversight/pay-leave/federal-holidays/holidays.ics",
    "include_observed_dates": True,
    "mark_actual_and_observed": False,
    "source_preference": "python_holidays",
}


def _error(code: str, field: str, message: str) -> str:
    return json.dumps(
        {
            "schema_version": SCHEMA_VERSION,
            "ok": False,
            "error": {"code": code, "field": field, "message": message},
        },
        sort_keys=True,
    )


def default_config(annual_accrual: float, sick_accrual: float, timezone: str) -> dict:
    """The default config shape from the desktop Config model; the rules
    section comes from the bundled DEFAULT_RULES and the parity gate locks
    this shape against the desktop CLI output."""
    defaults = deepcopy(_DEFAULT_DEFAULTS)
    defaults["annual_leave_accrual_hours"] = float(annual_accrual)
    defaults["sick_leave_accrual_hours"] = float(sick_accrual)
    return {
        "schema_version": 1,
        "user": {"display_name": "User", "timezone": timezone},
        "defaults": defaults,
        "holidays": deepcopy(_DEFAULT_HOLIDAYS),
        "rules": deepcopy(DEFAULT_RULES),
    }


def _require_str(body: dict, field: str) -> str:
    value = body.get(field)
    if not isinstance(value, str) or not value:
        raise AccountingError("INVALID_REQUEST", f"{field} must be a non-empty string.")
    return value


def _require_number(body: dict, field: str) -> float:
    value = body.get(field)
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise AccountingError("INVALID_REQUEST", f"{field} must be a number.")
    return float(value)


def _require_int(body: dict, field: str) -> int:
    value = body.get(field)
    if isinstance(value, bool) or not isinstance(value, int):
        raise AccountingError("INVALID_REQUEST", f"{field} must be an integer.")
    return value


def _balances(body: dict) -> dict:
    raw = body.get("balances")
    if not isinstance(raw, dict):
        raise AccountingError("INVALID_REQUEST", "balances must be an object.")
    balances = {}
    for category, hours in raw.items():
        if not isinstance(category, str) or isinstance(hours, bool) or not isinstance(hours, (int, float)):
            raise AccountingError("INVALID_REQUEST", f"balances.{category} must be a number.")
        balances[category] = float(hours)
    return balances


def _preview(ledger: dict, today: str | None = None) -> dict:
    preview = {
        "year": ledger["leave_year"],
        "start": ledger["leave_year_start"],
        "end": ledger["leave_year_end"],
        "pay_period_count": ledger["pay_period_count"],
        "first_pay_period": ledger["pay_periods"][0]["start_date"],
        "annual_accrual": ledger["annual_leave_accrual_hours"],
        "sick_accrual": ledger["sick_leave_accrual_hours"],
        "openings": deepcopy(ledger.get("starting_balances", {})),
        "next_accrual": None,
    }
    if today is not None:
        for period in ledger.get("pay_periods", []):
            accrual = period.get("accrual_date") or period.get("end_date")
            if accrual and str(accrual) >= today:
                preview["next_accrual"] = {
                    "date": str(accrual),
                    "annual": accrual_hours_for_date(ledger, "annual", str(accrual)),
                    "sick": accrual_hours_for_date(ledger, "sick", str(accrual)),
                }
                break
    return preview


def _config(body: dict) -> dict | None:
    """The existing store's config as a JSON string (Chaquopy-safe) or None."""
    raw_config = body.get("config")
    if raw_config is None:
        return None
    if not isinstance(raw_config, str):
        raise AccountingError("INVALID_REQUEST", "config must be a JSON string or null.")
    config = json.loads(raw_config)
    if not isinstance(config, dict):
        raise AccountingError("INVALID_REQUEST", "config must decode to a JSON object.")
    return config


def _number(value, description: str) -> float:
    try:
        number = float(value)
    except (TypeError, ValueError) as exc:
        raise AccountingError("INVALID_HOURS", f"{description} must be a number") from exc
    if not math.isfinite(number):
        raise AccountingError("INVALID_HOURS", f"{description} must be finite")
    return number


def _has_auto_accrual(ledger: dict, category: str, accrual_date: str) -> bool:
    return any(
        tx.get("source") == "auto_accrual"
        and tx.get("category") == category
        and tx.get("date") == accrual_date
        for tx in ledger.get("transactions", [])
    )


def _accrual_row(ledger: dict, category: str, accrual_date: str, period_number, existing_ids: list[str]) -> dict:
    """The canonical auto_accrual row (from the desktop Transaction model),
    built without pydantic; deterministic timestamps from the accrual date."""
    return {
        "id": generate_transaction_id(accrual_date, existing_ids),
        "date": accrual_date,
        "category": category,
        "direction": "earned",
        "hours": accrual_hours_for_date(ledger, category, accrual_date),
        "description": f"Automatic {category} leave accrual for pay period {period_number}",
        "status": "reconciled",
        "source": "auto_accrual",
        "created_at": f"{accrual_date}T00:00:00",
        "updated_at": f"{accrual_date}T00:00:00",
        "expiration_date": None,
        "expiration_pay_period": None,
        "earned_transaction_id": None,
        "expiration_extension_reason": None,
        "expiration_extended_at": None,
    }


def _seed_accruals_pure(ledger: dict, through_date: str) -> int:
    """Mirror of ledger.ensure_automatic_accruals with pydantic-free rows."""
    cutoff = through_date
    existing_ids = [str(tx.get("id", "")) for tx in ledger.get("transactions", [])]
    added = 0
    for pay_period in ledger.get("pay_periods", []):
        period_number = pay_period.get("pay_period_number")
        accrual_date = pay_period.get("accrual_date") or pay_period.get("end_date")
        if not accrual_date or accrual_date > cutoff:
            continue
        for category in ("annual", "sick"):
            hours = accrual_hours_for_date(ledger, category, accrual_date)
            if hours <= 0 or _has_auto_accrual(ledger, category, accrual_date):
                continue
            row = _accrual_row(ledger, category, accrual_date, period_number, existing_ids)
            ledger.setdefault("transactions", []).append(row)
            existing_ids.append(row["id"])
            added += 1
    return added


def _manual_ledger_pure(
    year: int,
    start: str,
    annual_accrual: float,
    balances: dict,
    *,
    pay_periods: int | None = None,
    config: dict | None = None,
) -> dict:
    """Mirror of cross_year.manual_ledger with pydantic-free accrual rows.

    Every semantic decision delegates to the core's pure helpers
    (generate_pay_periods, leave_year_period_count, accrual_hours_for_date,
    generate_transaction_id); only the row and envelope construction lives
    here, because the desktop builders create rows through the pydantic
    Transaction model. The device parity gate locks the drift risk.
    """
    if any(float(balances.get(category, 0.0)) for category in ("ppl", "fmla")):
        raise AccountingError("INVALID_PARENTAL_OPENING", "PPL needs a dated earned entitlement; FMLA is usage only")
    if not 1 <= year <= 9998:
        raise AccountingError("INVALID_YEAR", "Leave-year identifier must be between 1 and 9998")
    increment = _number((config or {}).get("defaults", {}).get("time_increment_hours", 0.25), "time increment")
    if increment <= 0:
        raise AccountingError("INVALID_HOURS", "Time increment must be positive")
    for category, value in {**balances, "annual_accrual": annual_accrual}.items():
        hours = _number(value, category)
        if hours < 0 or abs(hours / increment - round(hours / increment)) > EPSILON:
            raise AccountingError("INVALID_HOURS", f"{category} must be nonnegative in increments of {increment}")
    first = __import__("datetime").date.fromisoformat(start)
    count = (
        pay_periods
        if pay_periods is not None
        else (
            leave_year_period_count(first, year)
            if first.month == 1 and first.year == year and first.day <= 14
            else 26
        )
    )
    if count not in {26, 27}:
        raise AccountingError("UNSUPPORTED_PAY_PERIODS", "Choose 26 or 27 periods")
    periods = generate_pay_periods(first, count)
    ledger = {
        "schema_version": 1,
        "leave_year": year,
        "leave_year_start": start,
        "leave_year_end": periods[-1]["end_date"],
        "pay_period_count": count,
        "pay_periods": periods,
        "annual_leave_accrual_hours": annual_accrual,
        "sick_leave_accrual_hours": (config or {}).get("defaults", {}).get("sick_leave_accrual_hours", 4.0),
        "starting_balances": deepcopy(balances),
        "carryover_from_previous_year": deepcopy(balances),
        "transactions": [],
        "holidays": [],
        "initialization": {"mode": "manual", "method": "manual", "source_leave_year": None},
        "rollover_status": {
            "rolled_from_previous_year": False,
            "rolled_to_next_year": False,
            "rollover_completed_at": None,
        },
    }
    _seed_accruals_pure(ledger, str(ledger["leave_year_end"]))
    return ledger


def _manual_create(body: dict) -> dict:
    year = _require_int(body, "year")
    start = _require_str(body, "leave_year_start")
    pay_periods = body.get("pay_periods")
    if pay_periods is not None and (isinstance(pay_periods, bool) or not isinstance(pay_periods, int)):
        raise AccountingError("INVALID_REQUEST", "pay_periods must be an integer or null.")
    annual_accrual = _require_number(body, "annual_accrual")
    sick_accrual = _require_number(body, "sick_accrual")
    timezone = _require_str(body, "timezone")
    config = _config(body)
    balances = _balances(body)

    build_config = deepcopy(config) if config is not None else {"defaults": deepcopy(_DEFAULT_DEFAULTS)}
    build_config.setdefault("defaults", {})["sick_leave_accrual_hours"] = sick_accrual
    ledger = _manual_ledger_pure(
        year,
        start,
        annual_accrual,
        balances,
        pay_periods=pay_periods,
        config=build_config,
    )
    files = {f"leave_years/{year}.json": json.dumps(ledger, sort_keys=True)}
    if config is None:
        files["config.json"] = json.dumps(default_config(annual_accrual, sick_accrual, timezone), sort_keys=True)
    return files, _preview(ledger)


def _today_create(body: dict) -> dict:
    year = _require_int(body, "year")
    start = _require_str(body, "leave_year_start")
    today = _require_str(body, "today")
    pay_periods = body.get("pay_periods")
    if pay_periods is not None and (isinstance(pay_periods, bool) or not isinstance(pay_periods, int)):
        raise AccountingError("INVALID_REQUEST", "pay_periods must be an integer or null.")
    annual_accrual = _require_number(body, "annual_accrual")
    sick_accrual = _require_number(body, "sick_accrual")
    timezone = _require_str(body, "timezone")
    config = _config(body)
    targets = _balances(body)

    # Build with zero base rates; the configured rates become dated changes
    # effective Today, so nothing accrues before Today.
    build_config = deepcopy(config) if config is not None else {
        "defaults": deepcopy(_DEFAULT_DEFAULTS),
    }
    build_config.setdefault("defaults", {})["sick_leave_accrual_hours"] = 0.0
    ledger = _manual_ledger_pure(year, start, 0.0, {}, pay_periods=pay_periods, config=build_config)
    ledger["annual_leave_accrual_hours"] = 0.0
    ledger["sick_leave_accrual_hours"] = 0.0
    upsert_accrual_rate_change(
        ledger, category="annual", effective_date=today,
        hours_per_pay_period=annual_accrual, reason=SETUP_REASON,
    )
    upsert_accrual_rate_change(
        ledger, category="sick", effective_date=today,
        hours_per_pay_period=sick_accrual, reason=SETUP_REASON,
    )
    # Seed the scheduled accruals through year-end at the dated rates, so an
    # accrual scheduled for Today is part of the pre-adjustment balance (the
    # target includes it and must still be exact).
    _seed_accruals_pure(ledger, str(ledger["leave_year_end"]))

    current = calculate_balances(ledger, until_date=today)
    existing_ids = [str(tx.get("id", "")) for tx in ledger.get("transactions", [])]
    for category, target in sorted(targets.items()):
        difference = float(target) - float(current.get(category, 0.0))
        if abs(difference) <= 0.000001:
            continue
        # The canonical forced-balance row from the desktop command, built
        # without the pydantic Transaction model. Timestamps are derived
        # from the setup date so candidates are deterministic on the phone.
        transaction = {
            "id": generate_transaction_id(today, existing_ids),
            "date": today,
            "category": category,
            "direction": "forced_increase" if difference > 0 else "forced_decrease",
            "hours": abs(difference),
            "description": SETUP_REASON,
            "status": "reconciled",
            "source": "forced-balance",
            "created_at": f"{today}T00:00:00",
            "updated_at": f"{today}T00:00:00",
            "expiration_date": None,
            "expiration_pay_period": None,
            "earned_transaction_id": None,
            "expiration_extension_reason": None,
            "expiration_extended_at": None,
        }
        ledger.setdefault("transactions", []).append(transaction)
        existing_ids.append(transaction["id"])

    initialization = ledger.setdefault("initialization", {})
    initialization["baseline_as_of"] = today

    files = {f"leave_years/{year}.json": json.dumps(ledger, sort_keys=True)}
    if config is None:
        files["config.json"] = json.dumps(default_config(annual_accrual, sick_accrual, timezone), sort_keys=True)
    return files, _preview(ledger, today)


_OPERATIONS = {
    "manual_create": _manual_create,
    "today_create": _today_create,
}


def create(payload: str) -> str:
    """Run one creation preview and return the candidate files plus the preview."""
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
