"""Plan canonical Use or Lose imports without writing files or depending on a CLI."""

from __future__ import annotations

import hashlib
import json
from copy import deepcopy
from dataclasses import dataclass, field
from datetime import date
from math import isclose, isfinite

from .config import Config
from .cross_year import AccountingError, Snapshot, checked_ledger, effective_rules, resolve_snapshot_from
from .expirations import EPSILON, synchronize_expirations
from .ledger import TRANSACTION_CATEGORIES, calculate_balances
from .parental import validate_parental_state
from .payperiods import pay_period_pay_date
from .storage import remove_legacy_transaction_history
from .transaction_effects import direction_effect, signed_balance_effect, transaction_is_effective

FORCED_DIRECTIONS = {"forced_increase", "forced_decrease"}
MERGE_CONFLICT_CODES = {"FORCED_BALANCE_CONFLICT", "FORCED_BALANCE_BASIS_CHANGED", "INCOMPATIBLE_LEDGER_BASIS"}


@dataclass
class ImportPlan:
    mode: str
    preview_fingerprint: str
    writes: dict[str, dict] = field(default_factory=dict)
    changes: list[dict] = field(default_factory=list)
    warnings: list[dict] = field(default_factory=list)
    conflicts: list[dict] = field(default_factory=list)
    affected_years: list[int] = field(default_factory=list)

    def summary(self) -> dict:
        return {
            "status": "blocked" if self.conflicts else "ready",
            "mode": self.mode,
            "preview_fingerprint": self.preview_fingerprint,
            "affected_years": self.affected_years,
            "changes": self.changes,
            "warnings": self.warnings,
            "conflicts": self.conflicts,
        }

    def block(self, code: str, message: str, **details) -> None:
        self.conflicts.append({"code": code, "message": message, **details})


def canonical_json(value) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True, allow_nan=False).encode("ascii")


def normalize_archive(value: object) -> tuple[dict[str, dict], str]:
    if not isinstance(value, dict):
        raise ValueError("Import archive must be a JSON object")
    if (
        isinstance(value.get("leave_year"), int)
        and isinstance(value.get("transactions"), list)
        and isinstance(value.get("pay_periods"), list)
    ):
        value = {"schema_version": 1, "leave_years": {str(value["leave_year"]): value}}
        kind = "single leave-year backup"
    else:
        kind = "archive"
    if value.get("schema_version") != 1:
        raise ValueError("Unsupported import archive schema_version")
    years = value.get("leave_years")
    holidays = value.get("holiday_cache", {})
    if not isinstance(years, dict):
        raise ValueError("Import archive missing leave_years mapping")
    if not isinstance(holidays, dict):
        raise ValueError("holiday_cache must be an object")

    incoming = {}
    if value.get("config") is not None:
        if not isinstance(value["config"], dict):
            raise ValueError("config must be an object")
        incoming["config.json"] = deepcopy(value["config"])
    for year, ledger in years.items():
        if not valid_year_key(year) or not isinstance(ledger, dict):
            raise ValueError("Invalid leave-year entry in backup")
        ledger = deepcopy(ledger)
        remove_legacy_transaction_history(ledger)
        incoming[f"leave_years/{year}.json"] = ledger
    for name, cache in holidays.items():
        if not valid_cache_key(name) or not isinstance(cache, dict):
            raise ValueError("Invalid holiday cache entry in backup")
        incoming[f"holiday_cache/{name}.json"] = deepcopy(cache)
    return incoming, kind


def valid_year_key(value: str) -> bool:
    return (
        isinstance(value, str)
        and 1 <= len(value) <= 4
        and value.isascii()
        and value.isdecimal()
        and 1 <= int(value) <= 9998
        and str(int(value)) == value
    )


def valid_cache_key(value: str) -> bool:
    return (
        isinstance(value, str)
        and bool(value)
        and not any(character in value for character in '/\\:<>"|?*')
        and not any(ord(character) < 32 for character in value)
        and not value.endswith((" ", "."))
        and value.split(".", 1)[0].upper() not in {"CON", "PRN", "AUX", "NUL"}
        and not (len(value.split(".", 1)[0]) == 4 and value[:3].upper() in {"COM", "LPT"} and value[3].isdigit())
    )


def _ledger(files: dict[str, dict], year: int) -> dict:
    if (ledger := files.get(f"leave_years/{year}.json")) is None:
        raise AccountingError("MISSING_SOURCE_YEAR", f"Leave year {year} is missing")
    return checked_ledger(ledger, year)


def _date(value) -> date:
    parsed = date.fromisoformat(value)
    if parsed.isoformat() != value:
        raise ValueError("Use a canonical date")
    return parsed


def _hours(value) -> float:
    result = float(value)
    if not isfinite(result):
        raise ValueError("Hours must be finite")
    return result


def _transactions(ledger: dict) -> dict[str, dict]:
    result = {}
    start, end = _date(ledger["leave_year_start"]), _date(ledger["leave_year_end"])
    if start > end:
        raise ValueError("Leave-year bounds are reversed")
    for tx in ledger.get("transactions", []):
        key = tx.get("id")
        if not isinstance(key, str) or not key or key in result:
            raise ValueError("Transactions require unique nonempty IDs within their leave year")
        result[key] = tx
        if tx.get("void") or tx.get("direction") == "voided":
            continue
        day = _date(tx["date"])
        if not start <= day <= end and tx.get("source") != "expiration-engine":
            raise ValueError("Transaction date outside its leave year")
        if tx.get("category") not in TRANSACTION_CATEGORIES:
            raise ValueError("Unrecognized transaction category")
        if _hours(tx["hours"]) < 0:
            raise ValueError("Transaction hours must be nonnegative")
        direction_effect(tx.get("direction"))
        signed_balance_effect(tx)
    return result


def _periods(ledger: dict) -> list[tuple]:
    start, end = _date(ledger["leave_year_start"]), _date(ledger["leave_year_end"])
    periods = []
    previous = None
    for row in ledger["pay_periods"]:
        first = _date(row["start_date"])
        last = _date(row["end_date"])
        accrual = _date(row.get("accrual_date") or row["end_date"])
        pay_date = _date(pay_period_pay_date(row))
        number = row["pay_period_number"]
        if (
            not start <= first <= accrual <= last <= end
            or type(number) is not int
            or number != len(periods) + 1
            or (previous is not None and previous >= first)
        ):
            raise ValueError("Invalid pay-period schedule")
        previous = last
        periods.append((number, first, last, accrual, pay_date))
    if ledger.get("pay_period_count", len(periods)) != len(periods):
        raise ValueError("Pay-period count disagrees with stored schedule")
    return periods


def _basis(ledger: dict) -> dict:
    initialization = ledger.get("initialization", {})
    mode = initialization.get("mode") or initialization.get("method") or "manual"
    if mode not in {"manual", "carryover"}:
        raise ValueError("Unknown initialization mode")
    start, end = _date(ledger["leave_year_start"]), _date(ledger["leave_year_end"])
    opening_date = _date(initialization.get("baseline_as_of") or ledger["leave_year_start"])
    annual_rate = _hours(ledger.get("annual_leave_accrual_hours", 0))
    sick_rate = _hours(ledger.get("sick_leave_accrual_hours", 4))
    if not start <= opening_date <= end or min(annual_rate, sick_rate) < 0:
        raise ValueError("Invalid opening date or accrual rate")
    opening = {key: _hours(value) for key, value in ledger.get("starting_balances", {}).items()}
    opening_rows = {}
    dated_openings = []
    for tx in ledger.get("transactions", []):
        if tx.get("direction") == "starting_balance" and transaction_is_effective(tx):
            if tx["category"] not in opening:
                category, hours, tx_date = tx["category"], _hours(tx["hours"]), _date(tx["date"])
                opening_rows[category] = opening_rows.get(category, 0.0) + hours
                if tx_date != opening_date:
                    dated_openings.append((category, tx_date, hours))
    opening.update(opening_rows)
    changes = sorted(
        (
            row["category"],
            _date(row["effective_date"]),
            _hours(row["hours_per_pay_period"]),
        )
        for row in ledger.get("accrual_rate_changes", [])
    )
    if len(changes) != len({(category, day) for category, day, _ in changes}) or any(
        category not in {"annual", "sick"} or not start <= day <= end or rate < 0 for category, day, rate in changes
    ):
        raise ValueError("Invalid accrual rate history")
    return {
        "leave-year dates": (start, end),
        "opening date": opening_date,
        "opening balances": {key: value for key, value in opening.items() if value},
        "dated opening entries": sorted(dated_openings),
        "initialization mode": mode,
        "carryover ancestry": (initialization.get("source_leave_year"), initialization.get("source_fingerprint")),
        "pay-period schedule": _periods(ledger),
        "accrual rates": (annual_rate, sick_rate, changes),
        "carried entitlements": sorted(
            (
                tx.get("category", ""),
                str(tx.get("origin_leave_year", "")),
                str(tx.get("origin_transaction_id", "")),
                tx.get("original_earned_date") or "",
                tx.get("expiration_date") or "",
                _hours(tx["hours"]),
            )
            for tx in ledger["transactions"]
            if tx.get("carryover_generated") and tx.get("direction") == "earned"
        ),
    }


def _force_key(row: dict) -> tuple[str, str] | None:
    if row.get("direction") in FORCED_DIRECTIONS and transaction_is_effective(row):
        return row["date"], row["category"]
    return None


def _force_rows(ledger: dict) -> dict[tuple[str, str], list[dict]]:
    groups: dict[tuple[str, str], list[dict]] = {}
    for row in ledger.get("transactions", []):
        if (key := _force_key(row)) is not None:
            groups.setdefault(key, []).append(row)
    return groups


def _balance_at(ledger: Snapshot, key: tuple[str, str], *, without: str | None = None) -> float:
    day, category = key
    if without is not None:
        ledger = deepcopy(ledger)
        ledger["transactions"] = [item for item in ledger["transactions"] if item.get("id") != without]
    return calculate_balances(ledger, until_date=day).get(category, 0.0)


def _value_details(snapshot: Snapshot, rows: list[dict], key: tuple[str, str]) -> dict:
    result = {"ids": [row["id"] for row in rows], "adjustments": [signed_balance_effect(row) for row in rows]}
    if len(rows) == 1:
        try:
            result["balance"] = _balance_at(snapshot, key)
        except (ValueError, KeyError, TypeError):
            pass
    return result


def _resolve(files: dict[str, dict], year: int, cache: dict[int, Snapshot]) -> Snapshot:
    return resolve_snapshot_from(
        lambda requested: _ledger(files, requested), year, files.get("config.json", {}), cache=cache
    )


def _same_row(current: dict, incoming: dict) -> bool:
    ignored = {"created_at", "updated_at", "id"}
    return {key: val for key, val in current.items() if key not in ignored} == {
        key: val for key, val in incoming.items() if key not in ignored
    }


def _references_id(value, transaction_id: str) -> bool:
    if isinstance(value, dict):
        return any(_references_id(item, transaction_id) for field, item in value.items() if field != "id")
    if isinstance(value, list):
        return any(_references_id(item, transaction_id) for item in value)
    return isinstance(value, str) and value == transaction_id


def _force_diagnostic(plan: ImportPlan, code: str, year: int, key: tuple[str, str], **details) -> None:
    day, category = key
    message = f"Leave year {year}: {category} on {day} has competing or changed Force Balance adjustments."
    if "expected_balance" in details:
        message += (
            f" The balance would change from {details['expected_balance']:g} h to {details['proposed_balance']:g} h."
        )
    plan.block(
        code,
        message,
        leave_year=year,
        date=day,
        category=category,
        **details,
    )


def _same_basis(plan: ImportPlan, year: int, current: dict, incoming: dict) -> bool:
    current_basis, incoming_basis = _basis(current), _basis(incoming)
    differing = [field for field in current_basis if current_basis[field] != incoming_basis[field]]
    if differing:
        plan.block(
            "INCOMPATIBLE_LEDGER_BASIS",
            f"Leave year {year} has different " + ", ".join(differing) + ".",
            leave_year=year,
            fields=differing,
        )
    return not differing


def _merge_year(
    plan: ImportPlan,
    year: int,
    current_snapshot: Snapshot,
    incoming_snapshot: Snapshot,
    current: dict,
    incoming: dict,
    incoming_files: dict[str, dict],
) -> dict | None:
    current_rows, incoming_rows = _transactions(current), _transactions(incoming)
    current_forced, incoming_forced = _force_rows(current), _force_rows(incoming)
    deduplicated = set()
    for key in sorted(current_forced.keys() | incoming_forced.keys()):
        left, right = current_forced.get(key, []), incoming_forced.get(key, [])
        duplicate = (
            len(left) == len(right) == 1
            and _same_row(left[0], right[0])
            and isclose(
                _balance_at(current_snapshot, key), _balance_at(incoming_snapshot, key), abs_tol=EPSILON, rel_tol=0
            )
            and (left[0]["id"] == right[0]["id"] or not _references_id(incoming_files, right[0]["id"]))
        )
        if duplicate:
            deduplicated.add(right[0]["id"])
        elif (left and right) or len(left) > 1 or len(right) > 1:
            _force_diagnostic(
                plan,
                "FORCED_BALANCE_CONFLICT",
                year,
                key,
                current=_value_details(current_snapshot, left, key),
                incoming=_value_details(incoming_snapshot, right, key),
            )
    if plan.conflicts:
        return None

    added = []
    for transaction_id, row in incoming_rows.items():
        existing = current_rows.get(transaction_id)
        if transaction_id in deduplicated:
            continue
        if existing is not None:
            key = _force_key(existing) or _force_key(row)
            if key is not None and not _same_row(existing, row):
                _force_diagnostic(plan, "FORCED_BALANCE_CONFLICT", year, key, row_id=transaction_id)
            elif not _same_row(existing, row):
                plan.warnings.append({"code": "KEPT_CURRENT_TRANSACTION", "leave_year": year, "id": transaction_id})
            continue
        # The verified current accounting basis already represents these
        # structural records. Keep its existing identities and derived rows.
        if (
            row.get("source") == "auto_accrual"
            or row.get("carryover_generated")
            or row.get("direction") == "starting_balance"
        ):
            continue
        added.append(row)

    candidate = {**incoming, **current, "transactions": [*current_rows.values(), *added]}
    return candidate


def _validate_force_replay(
    plan: ImportPlan,
    year: int,
    candidate: Snapshot,
    originals: tuple[Snapshot, ...],
) -> None:
    for key, rows in _force_rows(candidate).items():
        if len(rows) != 1:
            _force_diagnostic(plan, "FORCED_BALANCE_CONFLICT", year, key)
            continue
        for original in originals:
            original_rows = _force_rows(original).get(key, [])
            if not original_rows:
                continue
            expected = _balance_at(original, key)
            actual = _balance_at(candidate, key)
            original_basis = _balance_at(original, key, without=original_rows[0]["id"])
            proposed_basis = _balance_at(candidate, key, without=rows[0]["id"])
            if not (
                isclose(expected, actual, rel_tol=0, abs_tol=EPSILON)
                and isclose(original_basis, proposed_basis, rel_tol=0, abs_tol=EPSILON)
            ):
                _force_diagnostic(
                    plan,
                    "FORCED_BALANCE_BASIS_CHANGED",
                    year,
                    key,
                    expected_balance=expected,
                    proposed_balance=actual,
                    row_id=rows[0]["id"],
                )
                break


def _affected_years(candidate: dict[str, dict], writes: dict[str, dict]) -> set[int]:
    affected = {int(name.split("/")[1][:-5]) for name in writes if name.startswith("leave_years/")}
    ledgers = {
        int(name.split("/")[1][:-5]): value for name, value in candidate.items() if name.startswith("leave_years/")
    }
    for year, value in sorted(ledgers.items()):
        setup = value.get("initialization", {})
        if "config.json" in writes or (
            isinstance(setup, dict)
            and isinstance(setup.get("source_leave_year"), int)
            and setup["source_leave_year"] in affected
        ):
            affected.add(year)
    return affected


def _validate_cache(cache: dict) -> None:
    rows = cache.get("holidays", [])
    if not isinstance(rows, list):
        raise ValueError("Cached holidays must be a list")
    for row in rows:
        for date_field in ("actual_date", "observed_date", "display_date"):
            if row.get(date_field) is not None:
                _date(row[date_field])


def _check_portable_paths(current: dict[str, dict], incoming: dict[str, dict]) -> None:
    seen = set()
    for path in sorted(current.keys() | incoming.keys()):
        folded = path.casefold()
        if folded in seen:
            raise AccountingError("INVALID_PATH", "Backup and datastore contain conflicting file name spellings")
        seen.add(folded)


def plan_import(
    current: dict[str, dict],
    archive: object,
    mode: str,
    *,
    current_identity: str,
    archive_identity: str,
) -> ImportPlan:
    """Classify and validate an import from immutable data. Callers publish its writes."""

    token = hashlib.sha256(canonical_json([mode, current_identity, archive_identity])).hexdigest()
    plan = ImportPlan(mode, token)
    try:
        if mode not in {"merge", "replace", "create"}:
            raise ValueError("Unsupported import mode")
        incoming, _ = normalize_archive(archive)
        _check_portable_paths(current, incoming)
        candidate = dict(current)
        origins = {**current, **incoming}
        current_cache: dict[int, Snapshot] = {}
        incoming_cache: dict[int, Snapshot] = {}
        for path, payload in sorted(incoming.items()):
            old = current.get(path)
            year = None
            if path.startswith("leave_years/"):
                year = int(path.split("/")[1][:-5])
                payload = _ledger(incoming, year)
                _transactions(payload)
                _basis(payload)
            if old is not None and mode == "create":
                plan.block("DESTINATION_EXISTS", f"Refusing to overwrite existing file: {path}")
                continue
            if old is not None and mode == "merge":
                if year is None:
                    plan.changes.append({"file": path, "action": "keep"})
                    continue
                old = _ledger(current, year)
                if not _same_basis(plan, year, old, payload):
                    continue
                payload = _merge_year(
                    plan,
                    year,
                    _resolve(current, year, current_cache),
                    _resolve(origins, year, incoming_cache),
                    old,
                    payload,
                    incoming,
                )
                if payload is None:
                    continue
            if old == payload and mode != "replace":
                plan.changes.append({"file": path, "action": "unchanged"})
                continue
            change = {"file": path, "action": "add" if old is None else mode}
            if year is not None:
                old_ids = {tx["id"] for tx in (old or {}).get("transactions", [])}
                new_ids = {tx["id"] for tx in payload.get("transactions", [])}
                change.update(
                    {"year": year, "added_records": len(new_ids - old_ids), "removed_records": len(old_ids - new_ids)}
                )
            plan.changes.append(change)
            plan.writes[path] = candidate[path] = deepcopy(payload)

        if not plan.conflicts:
            if "config.json" in plan.writes:
                Config.model_validate(candidate["config.json"])
                effective_rules(candidate["config.json"])
            for name, value in plan.writes.items():
                if name.startswith("holiday_cache/"):
                    _validate_cache(value)
            plan.affected_years = sorted(_affected_years(candidate, plan.writes))
            cache: dict[int, Snapshot] = {}
            for year in plan.affected_years:
                ledger = _ledger(candidate, year)
                _transactions(ledger)
                _basis(ledger)
                snapshot = _resolve(candidate, year, cache)
                validate_parental_state(snapshot, snapshot.config)
                state = synchronize_expirations(
                    deepcopy(snapshot), snapshot.config, as_of=_date(snapshot["leave_year_end"])
                )
                if any(warning["code"] == "CONFLICTING_CARRYOVER" for warning in state["warnings"]):
                    raise AccountingError("CONFLICTING_CARRYOVER", f"Unresolved carryover in leave year {year}")
                if mode == "merge" and _force_rows(snapshot):
                    key = f"leave_years/{year}.json"
                    sources = []
                    if key in current:
                        sources.append(_resolve(current, year, current_cache))
                    if key in incoming:
                        sources.append(_resolve(origins, year, incoming_cache))
                    _validate_force_replay(plan, year, snapshot, tuple(sources))
            canonical_json(plan.writes)
    except AccountingError as exc:
        plan.block(exc.code, str(exc))
    except ValueError as exc:
        code, _, detail = str(exc).partition(": ")
        if detail and code.isidentifier() and code.isupper():
            plan.block(code, detail)
        else:
            plan.block(
                "INVALID_LEDGER",
                "The backup or an affected ledger has invalid fields or an unreadable accounting basis.",
            )
    except (AttributeError, KeyError, TypeError):
        plan.block(
            "INVALID_LEDGER", "The backup or an affected ledger has invalid fields or an unreadable accounting basis."
        )
    if plan.conflicts:
        plan.writes.clear()
    return plan
