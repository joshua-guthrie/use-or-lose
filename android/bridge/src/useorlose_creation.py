"""Android write-side creation adapter over the shared pure core.

Calls the same bundled core builders the CLI uses — ``cross_year.manual_ledger``,
``cross_year.derive_carryover``, ``ledger.ensure_automatic_accruals``,
``ledger.upsert_accrual_rate_change``, ``ledger.create_transaction`` — so the
phone creates desktop-compatible schema-v1 ledgers with no duplicated
accounting logic. The final store publish remains the Kotlin I04 writer.
"""

from __future__ import annotations

import json
import math
from copy import deepcopy

import useorlose.cross_year as cross_year

from useorlose.cross_year import AccountingError, checked_ledger, derive_carryover, fingerprint
from useorlose.ledger import (
    accrual_hours_for_date,
    calculate_balances,
    create_transaction,
    ensure_automatic_accruals,
    upsert_accrual_rate_change,
)
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
    this shape against the CLI output."""
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
    ledger = cross_year.manual_ledger(
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


def _carryover_create(body: dict) -> dict:
    """Preview a carryover from the adjacent source ledger (YEAR-CARRY-01).

    Delegates entirely to the bundled pydantic-free cross_year builders and
    reproduces creation_plan's preview fingerprint tuple, so the phone and
    the CLI derive identical candidates from identical inputs.
    """
    year = _require_int(body, "year")
    source_year = _require_int(body, "source_year")
    source_raw = body.get("source")
    if not isinstance(source_raw, str):
        raise AccountingError("INVALID_REQUEST", "source must be the source ledger JSON string.")
    try:
        parsed = json.loads(source_raw)
    except ValueError as exc:
        raise AccountingError("INVALID_REQUEST", f"source is not valid JSON: {exc}") from exc
    source = checked_ledger(parsed, source_year)
    config = _config(body) or {}
    pay_periods = body.get("pay_periods")
    if pay_periods is not None and (isinstance(pay_periods, bool) or not isinstance(pay_periods, int)):
        raise AccountingError("INVALID_REQUEST", "pay_periods must be an integer or null.")
    start = body.get("leave_year_start")
    if start is not None and not isinstance(start, str):
        raise AccountingError("INVALID_REQUEST", "leave_year_start must be an ISO date string or null.")

    target, summary = derive_carryover(source, year, config, start=start, pay_periods=pay_periods)
    summary.update(
        {
            "preview_fingerprint": fingerprint({"target": target, "source": source, "config": config}),
            "created_file": None,
            "created_transaction_ids": [],
        }
    )
    files = {f"leave_years/{year}.json": json.dumps(target, sort_keys=True)}
    preview = {
        "kind": "carryover",
        "year": year,
        "source_year": summary["source_leave_year"],
        "source_start": summary["source_start"],
        "source_end": summary["source_end"],
        "start": summary["leave_year_start"],
        "end": summary["leave_year_end"],
        "pay_period_count": summary["pay_period_count"],
        "first_pay_period": target["pay_periods"][0]["start_date"],
        "openings": summary["opening_balances"],
        "reductions": summary["adjustments"],
        "lots": summary["lots"],
        "warnings": summary["warnings"],
        "source_fingerprint": summary["source_fingerprint"],
        "preview_fingerprint": summary["preview_fingerprint"],
    }
    return files, preview


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
    ledger = cross_year.manual_ledger(year, start, 0.0, {}, pay_periods=pay_periods, config=build_config)
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
    ensure_automatic_accruals(ledger, str(ledger["leave_year_end"]))

    current = calculate_balances(ledger, until_date=today)
    existing_ids = [str(tx.get("id", "")) for tx in ledger.get("transactions", [])]
    for category, target in sorted(targets.items()):
        difference = float(target) - float(current.get(category, 0.0))
        if abs(difference) <= 0.000001:
            continue
        # The canonical forced-balance row from the desktop command, built
        # through the same shared create_transaction the CLI uses.
        transaction = create_transaction(
            date=today,
            category=category,
            direction="forced_increase" if difference > 0 else "forced_decrease",
            hours=abs(difference),
            description=SETUP_REASON,
            status="reconciled",
            source="forced-balance",
            existing_ids=existing_ids,
        )
        ledger.setdefault("transactions", []).append(transaction.model_dump())
        existing_ids.append(transaction.id)

    initialization = ledger.setdefault("initialization", {})
    initialization["baseline_as_of"] = today

    files = {f"leave_years/{year}.json": json.dumps(ledger, sort_keys=True)}
    if config is None:
        files["config.json"] = json.dumps(default_config(annual_accrual, sick_accrual, timezone), sort_keys=True)
    return files, _preview(ledger, today)


_OPERATIONS = {
    "manual_create": _manual_create,
    "today_create": _today_create,
    "carryover_create": _carryover_create,
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
