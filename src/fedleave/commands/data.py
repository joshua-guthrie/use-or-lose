"""Initialize, validate, import, export, and describe Use or Lose data stores."""

from __future__ import annotations

import hashlib
import json
import sys
from datetime import datetime as _datetime
from pathlib import Path

import typer
from typer.models import OptionInfo

from ..cli_app import _print_json, app, console
from ..cli_helpers import load_leave_year, parse_iso_date
from ..config import get_default_data_dir, load_config
from ..cross_year import AccountingError
from ..import_storage import JOURNAL, import_path, publish_import, read_store, recover_import
from ..importing import ImportPlan, plan_import
from ..ledger import apply_fixes_to_leave_year, validate_leave_year
from ..locking import datastore_lock, datastore_read_lock
from ..storage import atomic_write_json, ensure_data_dir, load_json, write_json
from ..transaction_effects import transaction_is_effective
from ..wms_import import (
    WmsImportError,
    build_leave_year_skeleton,
    build_transactions_from_report,
    parse_wms_http_leave_report,
    report_transaction_keys,
)


def _nonzero_number(value: object) -> bool:
    try:
        return abs(float(value)) > 1e-9
    except (TypeError, ValueError):
        return False


@app.command(name="years")
def years(
    json_output: bool = typer.Option(False, "--json", help="Emit machine-readable JSON."),
    data_dir: Path | None = typer.Option(None, help="Data directory override."),
) -> None:
    """List backend-recognized leave years and visible leave categories."""
    if not isinstance(json_output, bool):
        json_output = False
    if isinstance(data_dir, OptionInfo):
        data_dir = None

    year_dir = get_default_data_dir(data_dir) / "leave_years"
    records: list[dict[str, object]] = []
    warnings: list[str] = []
    visible_categories: set[str] = set()

    if year_dir.exists():
        for path in sorted(year_dir.glob("*.json")):
            if not path.stem.isdigit():
                continue
            year = int(path.stem)
            try:
                leave_year = load_json(path)
            except (OSError, ValueError, json.JSONDecodeError) as exc:
                records.append(
                    {
                        "leave_year": year,
                        "start_date": None,
                        "end_date": None,
                        "valid": False,
                    }
                )
                warnings.append(f"{path.name}: {exc}")
                continue
            if not isinstance(leave_year, dict):
                records.append(
                    {
                        "leave_year": year,
                        "start_date": None,
                        "end_date": None,
                        "valid": False,
                    }
                )
                warnings.append(f"{path.name}: expected a JSON object")
                continue

            start_date = leave_year.get("leave_year_start")
            end_date = leave_year.get("leave_year_end")
            try:
                start_date = parse_iso_date(str(start_date)).isoformat()
                end_date = parse_iso_date(str(end_date)).isoformat()
                valid = int(leave_year.get("leave_year", year)) == year and start_date <= end_date
            except (TypeError, ValueError):
                valid = False
            if not valid:
                warnings.append(f"{path.name}: invalid leave-year metadata")
            record = {
                "leave_year": year,
                "start_date": start_date,
                "end_date": end_date,
                "valid": valid,
            }
            from ..payperiods import validate_pay_periods

            try:
                validate_pay_periods(leave_year)
                record["carryover_eligible"] = valid
            except (ValueError, KeyError, TypeError) as exc:
                record["carryover_eligible"] = False
                record["carryover_error"] = str(exc)
            records.append(record)

            for field in ("starting_balances", "carryover_from_previous_year"):
                values = leave_year.get(field, {})
                if isinstance(values, dict):
                    visible_categories.update(
                        str(category) for category, value in values.items() if _nonzero_number(value)
                    )
            for transaction in leave_year.get("transactions", []):
                if not isinstance(transaction, dict):
                    continue
                try:
                    effective = transaction_is_effective(transaction)
                except ValueError as exc:
                    record["valid"] = False
                    warnings.append(f"{path.name}: {exc}")
                    continue
                if effective and _nonzero_number(transaction.get("hours")):
                    visible_categories.add(str(transaction.get("category", "")))

    payload = {
        "years": records,
        "visible_categories": sorted(category for category in visible_categories if category),
        "warnings": warnings,
    }
    if json_output:
        _print_json(payload)
        return
    if not records:
        console.print("No leave years found.")
        return
    for record in records:
        validity = "valid" if record["valid"] else "invalid"
        console.print(
            f"{record['leave_year']}: {record['start_date'] or 'unknown'} to "
            f"{record['end_date'] or 'unknown'} ({validity})"
        )


@app.command()
def init(
    year: int = typer.Option(..., help="New leave year (create only)."),
    leave_year_start: str | None = typer.Option(
        None, help="Manual start date; carryover derives the adjacent source end + 1 day."
    ),
    annual_accrual: float | None = typer.Option(None, help="Manual annual accrual hours per period (default 6)."),
    annual_start: float | None = typer.Option(None, help="Manual starting annual hours."),
    sick_start: float | None = typer.Option(None, help="Manual starting sick hours."),
    comp_start: float | None = typer.Option(None, help="Manual starting comp hours."),
    credit_start: float | None = typer.Option(None, help="Manual starting credit hours."),
    travel_comp_start: float | None = typer.Option(None, help="Manual starting travel comp hours."),
    time_off_award_start: float | None = typer.Option(None, help="Manual starting time-off award hours."),
    religious_comp_start: float | None = typer.Option(None, help="Manual starting religious comp hours."),
    restored_annual_start: float | None = typer.Option(None, help="Manual starting restored annual hours."),
    holiday_source: str = typer.Option("python_holidays", help="Holiday source: python_holidays or opm_ics."),
    holiday_ics_url: str = typer.Option(
        "https://www.opm.gov/policy-data-oversight/pay-leave/federal-holidays/holidays.ics",
        help="Holiday calendar URL.",
    ),
    data_dir: Path | None = typer.Option(None, help="Data directory override."),
    manual: bool = typer.Option(False, help="Independent manual baseline; this is the legacy default."),
    from_year: int | None = typer.Option(
        None, help="Explicit adjacent predecessor; mutually exclusive with manual balances."
    ),
    preview: bool = typer.Option(False, help="Read-only creation preview; creates no directories or files."),
    json_output: bool = typer.Option(False, "--json", help="Machine-readable preview or creation result."),
    pay_periods: int | None = typer.Option(None, help="Explicit agency schedule of 26 or 27 pay periods."),
    expected_fingerprint: str | None = typer.Option(None, help="Reject apply if the reviewed preview changed."),
) -> None:
    """Create with Manual Starting Balances or Carry Over From Previous Leave Year.

    Default manual init never imports predecessor history and emits no generic
    no-carryover warning. Both modes refuse an existing destination.
    """
    from ..config import console as config_console
    from ..config import init_config
    from ..cross_year import AccountingError, fingerprint, manual_ledger
    from .rollover import create_from_previous, report_error

    balances = {
        "annual": annual_start,
        "sick": sick_start,
        "comp": comp_start,
        "credit": credit_start,
        "travel_comp": travel_comp_start,
        "time_off_award": time_off_award_start,
        "religious_comp": religious_comp_start,
        "restored_annual": restored_annual_start,
    }
    base = get_default_data_dir(data_dir)
    if from_year is not None:
        if manual or any(value is not None for value in balances.values()) or annual_accrual is not None:
            report_error(
                AccountingError(
                    "MIXED_INITIALIZATION_MODES",
                    "--from-year cannot be combined with --manual, starting balances (including zero), or manual accrual",
                ),
                json_output,
            )
        start = parse_iso_date(leave_year_start).isoformat() if leave_year_start else None
        create_from_previous(
            from_year,
            year,
            base,
            preview=preview,
            json_output=json_output,
            start=start,
            pay_periods=pay_periods,
            expected=expected_fingerprint,
        )
        return
    try:
        if not leave_year_start:
            raise AccountingError("MISSING_START_DATE", "Manual mode requires --leave-year-start")
        start = parse_iso_date(leave_year_start).isoformat()
        entered = {category: 0.0 if amount is None else amount for category, amount in balances.items()}
        accrual = 6.0 if annual_accrual is None else annual_accrual
        ledger = manual_ledger(
            year,
            start,
            accrual,
            entered,
            pay_periods=pay_periods,
            config=load_config(base) if (base / "config.json").exists() else {},
        )
        path = base / "leave_years" / f"{year}.json"
        if path.exists() or path.is_symlink():
            raise AccountingError("DESTINATION_EXISTS", str(path))
        token = fingerprint({"ledger": ledger, "config": load_config(base) if (base / "config.json").exists() else {}})
        if expected_fingerprint is not None and expected_fingerprint != token:
            raise AccountingError("STALE_PREVIEW", "Manual inputs or policy changed; regenerate preview")
        result = {
            "action": "preview" if preview else "applied",
            "initialization_mode": "manual",
            "source_leave_year": None,
            "leave_year": year,
            "leave_year_start": start,
            "leave_year_end": ledger["leave_year_end"],
            "pay_period_count": ledger["pay_period_count"],
            "opening_balances": entered,
            "preview_fingerprint": token,
            "warnings": [],
            "created_file": None,
        }
        if not preview:
            quiet = config_console.quiet
            config_console.quiet = json_output
            try:
                init_config(
                    year,
                    start,
                    accrual,
                    entered,
                    data_dir=base,
                    holiday_source=holiday_source,
                    holiday_ics_url=holiday_ics_url,
                    pay_period_count=ledger["pay_period_count"],
                )
            finally:
                config_console.quiet = quiet
            result["created_file"] = str(path)
    except (ValueError, OSError, TypeError) as exc:
        report_error(exc, json_output)
    if json_output:
        _print_json(result)
    elif preview:
        console.print(result)


def _read_json_files_by_stem(directory: Path) -> dict[str, dict]:
    if not directory.exists():
        return {}
    result = {}
    for path in sorted(directory.iterdir()):
        if not path.is_file() or path.suffix != ".json":
            continue
        data = load_json(path)
        result[path.stem] = data
    return result


@app.command("export-data")
def export_data(
    output: Path = typer.Option(..., help="Output JSON archive path."),
    data_dir: Path | None = typer.Option(None, help="Data directory override."),
) -> None:
    """Export fedleave data to a portable JSON archive."""
    if isinstance(data_dir, OptionInfo):
        data_dir = None

    base = get_default_data_dir(data_dir)
    if not base.exists():
        console.print(f"[red]ERROR:[/red] Data directory not found: {base}")
        raise typer.Exit(code=1)

    archive = {
        "schema_version": 1,
        "exported_at": _datetime.now().isoformat(),
        "source_data_dir": str(base),
        "config": load_json(base / "config.json") if (base / "config.json").exists() else None,
        "leave_years": _read_json_files_by_stem(base / "leave_years"),
        "holiday_cache": _read_json_files_by_stem(base / "holiday_cache"),
    }

    output.parent.mkdir(parents=True, exist_ok=True)
    atomic_write_json(output, archive)
    console.print(f"Exported fedleave data to {output}")


def _build_import_plan(base: Path, source: Path, mode: str) -> ImportPlan:
    raw = source.read_bytes()
    archive = json.loads(raw.decode("utf-8"))
    current, identity = read_store(base)
    plan = plan_import(
        current, archive, mode, current_identity=identity, archive_identity=hashlib.sha256(raw).hexdigest()
    )
    for key in plan.writes:
        if import_path(base, key).resolve() == source.resolve():
            raise AccountingError("INVALID_PATH", "Import would replace its source backup")
    return plan


def _report_import(plan: ImportPlan, json_output: bool, *, preview: bool) -> None:
    if json_output:
        _print_json(
            {**plan.summary(), "action": "preview" if preview else "applied" if not plan.conflicts else "blocked"}
        )
    else:
        for finding in plan.conflicts:
            details = []
            for origin in ("current", "incoming"):
                if side := finding.get(origin):
                    amounts = ", ".join(f"{amount:+g} h" for amount in side["adjustments"])
                    balance = f"{side['balance']:g} h" if "balance" in side else "balance unavailable"
                    details.append(f"{origin} {balance}, adjustments {amounts or 'none'}, IDs {', '.join(side['ids'])}")
            suffix = (" " + "; ".join(details)) if details else ""
            console.print(f"{finding['code']}: {finding['message']}{suffix}", markup=False, soft_wrap=True)
        for warning in plan.warnings:
            console.print(
                f"KEPT_CURRENT_TRANSACTION: kept {warning['leave_year']}:{warning['id']}",
                markup=False,
                soft_wrap=True,
            )
        if not plan.conflicts and preview:
            for change in plan.changes:
                console.print(f"{change['action']}: {change['file']}", markup=False)
            console.print(f"Preview fingerprint: {plan.preview_fingerprint}")
        elif not plan.conflicts:
            console.print("Imported Use or Lose data." if plan.writes else "Use or Lose data is already up to date.")

    if plan.conflicts:
        raise typer.Exit(2)


@app.command("import-data")
def import_data(
    input: Path | None = typer.Option(None, help="Input JSON archive path (required unless recovering)."),
    overwrite: bool = typer.Option(False, help="Replace included files, creating backups first."),
    merge: bool = typer.Option(False, help="Merge compatible leave years; stop on accounting conflicts."),
    data_dir: Path | None = typer.Option(None, help="Data directory override."),
    dry_run: bool = typer.Option(False, help="Review a complete candidate without writing files."),
    json_output: bool = typer.Option(False, "--json", help="Emit a machine-readable preview or result."),
    expected_fingerprint: str | None = typer.Option(None, help="Reject apply if the reviewed inputs changed."),
    recover: bool = typer.Option(False, help="Restore the files from an interrupted import."),
) -> None:
    """Import a schema-v1 JSON archive or a single leave-year backup."""
    overwrite, merge, dry_run, json_output, recover = (
        value is True for value in (overwrite, merge, dry_run, json_output, recover)
    )
    input = input if isinstance(input, Path) else None
    expected_fingerprint = expected_fingerprint if isinstance(expected_fingerprint, str) else None
    base = get_default_data_dir(None if isinstance(data_dir, OptionInfo) else data_dir)
    mode = "replace" if overwrite else "merge" if merge else "create"
    plan = ImportPlan(mode, "")
    try:
        if overwrite and merge:
            raise AccountingError("INVALID_OPTIONS", "Choose either --merge or --overwrite")
        if recover:
            if input is not None or merge or overwrite or dry_run or expected_fingerprint:
                raise AccountingError("INVALID_OPTIONS", "--recover is a separate action")
            journal = base / JOURNAL
            if journal.exists() or journal.is_symlink():
                with datastore_lock(base):
                    result = recover_import(base)
            else:
                result = {"status": "unchanged", "restored_files": []}
            if json_output:
                _print_json(result)
            else:
                console.print(
                    "Previous datastore restored." if result["status"] == "recovered" else "No import to recover."
                )
            return
        if input is None:
            raise AccountingError("INVALID_OPTIONS", "--input is required")
        if not input.is_file():
            plan.block("ARCHIVE_NOT_FOUND", "The selected backup could not be opened.")
            if json_output:
                _print_json(plan.summary())
            else:
                console.print(plan.conflicts[0]["message"])
            raise typer.Exit(1)
        with datastore_read_lock(base):
            plan = _build_import_plan(base, input, mode)
        if expected_fingerprint is not None and expected_fingerprint != plan.preview_fingerprint:
            raise AccountingError("STALE_IMPORT_PREVIEW", "The backup or current datastore changed. Review again.")
        if dry_run or plan.conflicts or not plan.writes:
            _report_import(plan, json_output, preview=dry_run)
            return
        with datastore_lock(base):
            plan = _build_import_plan(base, input, mode)
            if expected_fingerprint is not None and expected_fingerprint != plan.preview_fingerprint:
                raise AccountingError("STALE_IMPORT_PREVIEW", "The backup or current datastore changed. Review again.")
            if not plan.conflicts:
                publish_import(base, plan)
    except (json.JSONDecodeError, UnicodeError):
        plan.block("INVALID_JSON", "A backup or datastore file is not readable UTF-8 JSON.")
        if json_output:
            _print_json(plan.summary())
        else:
            console.print(plan.conflicts[-1]["message"])
        raise typer.Exit(3) from None
    except (AccountingError, OSError, ValueError) as exc:
        if isinstance(exc, AccountingError):
            plan.block(exc.code, str(exc).removeprefix(f"{exc.code}: "))
        else:
            plan.block("IMPORT_FAILED", "Could not read or write the backup or datastore.")
    _report_import(plan, json_output, preview=dry_run)


@app.command("import-wms-http")
def import_wms_http(
    input: Path = typer.Option(..., help="Input WMS clocking HTML report path."),
    data_dir: Path | None = typer.Option(None, help="Data directory override."),
) -> None:
    """Import a FRC-E WMS HTTP leave report from HTML."""
    if isinstance(data_dir, OptionInfo):
        data_dir = None

    if not input.exists():
        console.print(f"[red]ERROR:[/red] Import report not found: {input}")
        raise typer.Exit(code=1)

    try:
        report = parse_wms_http_leave_report(input.read_text(encoding="utf-8"))
    except UnicodeDecodeError as exc:
        error = WmsImportError(f"Could not read report as UTF-8 HTML: {exc}")
        console.print(f"[red]ERROR:[/red]\n{error.support_report(input)}")
        raise typer.Exit(code=2)
    except WmsImportError as exc:
        console.print(f"[red]ERROR:[/red]\n{exc.support_report(input)}")
        raise typer.Exit(code=2)

    base = get_default_data_dir(data_dir)
    ensure_data_dir(base)
    year_path = base / "leave_years" / f"{report.leave_year}.json"

    if year_path.exists():
        leave_year = load_leave_year(report.leave_year, data_dir)
    else:
        try:
            config = load_config(base)
            annual_accrual = float(config.get("defaults", {}).get("annual_leave_accrual_hours", 6.0))
        except FileNotFoundError:
            annual_accrual = 6.0
        leave_year = build_leave_year_skeleton(report, annual_accrual)

    transactions = leave_year.setdefault("transactions", [])
    removed_keys = report_transaction_keys(report)
    if removed_keys:
        transactions[:] = [
            transaction
            for transaction in transactions
            if (
                str(transaction.get("date", "")),
                str(transaction.get("category", "")),
                str(transaction.get("direction", "")),
            )
            not in removed_keys
        ]

    existing_ids = [str(transaction.get("id", "")) for transaction in transactions]
    try:
        imported_transactions, _ = build_transactions_from_report(report, existing_ids)
    except ValueError as exc:
        console.print(f"[red]ERROR:[/red] {exc}")
        raise typer.Exit(code=2)

    transactions.extend(imported_transactions)
    year_path.parent.mkdir(parents=True, exist_ok=True)
    if year_path.exists():
        write_json(year_path, leave_year)
    else:
        atomic_write_json(year_path, leave_year)

    console.print(
        f"Imported {len(imported_transactions)} WMS leave transaction(s) from {input.name} "
        f"into leave year {report.leave_year}."
    )
    if report.ignored_rows:
        console.print(f"Ignored {report.ignored_rows} non-leave or regular-time row(s).")


@app.command(name="validate")
def validate(
    data_dir: Path | None = typer.Option(None, help="Data directory override."),
    apply: bool = typer.Option(False, help="Apply automatic fixes where possible."),
    json_output: bool = typer.Option(False, "--json", help="Emit machine-readable JSON."),
) -> None:
    """Validate leave year JSON files in the data directory.

    With `--apply` the command will write back normalized dates for transactions when safe.
    Noninteractive input reports issues without prompting; use `--apply` to fix them.
    """
    from ..storage import write_json

    if not isinstance(apply, bool):
        apply = False
    if not isinstance(json_output, bool):
        json_output = False
    if isinstance(data_dir, OptionInfo):
        data_dir = None

    base = get_default_data_dir(data_dir)
    year_dir = base / "leave_years"
    if not year_dir.exists():
        console.print(f"No leave_years directory found in {base}")
        raise typer.Exit(code=1)

    any_issues = False
    results = []
    for pj in sorted(year_dir.iterdir()):
        if pj.suffix != ".json":
            continue
        ly = load_json(pj)
        issues = validate_leave_year(ly)
        if not issues and (
            any(row.get("category") in {"ppl", "fmla"} for row in ly.get("transactions", []))
            or any(ly.get("starting_balances", {}).get(category) for category in ("ppl", "fmla"))
        ):
            from ..cross_year import resolve_snapshot
            from ..parental import validate_parental_state

            try:
                resolved = resolve_snapshot(base, int(pj.stem))
                validate_parental_state(resolved, resolved.config)
            except (ValueError, FileNotFoundError) as exc:
                issues.append({"type": "parental", "path": "transactions", "message": str(exc)})
        if not issues:
            results.append({"file": pj.name, "year": int(pj.stem), "ok": True, "issues": [], "applied": False})
            if not json_output:
                console.print(f"{pj.name}: OK")
            continue

        any_issues = True
        result = {"file": pj.name, "year": int(pj.stem), "ok": False, "issues": issues, "applied": False}
        if not json_output:
            console.print(f"{pj.name}: {len(issues)} issues found")
            for iss in issues:
                console.print(f"  - {iss.get('path')}: {iss.get('message')}")
        # Piped/stdin-closed callers (including MCP) explicitly select --apply.
        # Preserve per-file confirmation for ordinary interactive CLI sessions.
        should_apply = apply or (
            not json_output and sys.stdin.isatty() and typer.confirm(f"Apply suggested fixes to {pj.name}?")
        )
        if should_apply:
            fixed = apply_fixes_to_leave_year(ly, issues)
            try:
                write_json(pj, fixed)
                result["applied"] = True
                if not json_output:
                    console.print(f"  Applied fixes to {pj.name}")
            except Exception as exc:
                result["write_error"] = str(exc)
                if not json_output:
                    console.print(f"  Failed to write fixes: {exc}")
        results.append(result)

    if any_issues:
        if json_output:
            _print_json({"ok": False, "results": results})
        raise SystemExit(2)
    if json_output:
        _print_json({"ok": True, "results": results})
        return
    console.print("Validation completed: no issues found")


@app.command("snapshot")
def snapshot_command(
    year: int = typer.Option(..., help="Leave year to resolve with its explicit carryover ancestry."),
    json_output: bool = typer.Option(False, "--json"),
    data_dir: Path | None = typer.Option(None),
) -> None:
    """Read-only accounting snapshot for charts, analytics, and integrations."""
    from ..cli_helpers import read_snapshot_payload
    from .rollover import report_error

    try:
        result = read_snapshot_payload(year, data_dir)
    except (ValueError, OSError, TypeError, KeyError) as exc:
        report_error(exc, json_output)
    if json_output:
        _print_json(result)
    else:
        console.print(result)
