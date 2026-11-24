#!/usr/bin/env python3
"""Build the pinned Calendar 2026 Android fixtures and golden outputs."""

from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
from datetime import date as DateClass
from importlib.metadata import version
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SRC = ROOT / "src"
FIXTURE_ID = "calendar-2026"
FIXTURE_DIR = ROOT / "android" / "qa" / "fixtures" / FIXTURE_ID
GOLDEN_DIR = ROOT / "android" / "qa" / "golden" / FIXTURE_ID
STORAGE_DIR = ROOT / "android" / "qa" / "fixtures" / "storage"
FROZEN_DATE = "2026-01-16"
TIMEZONE = "America/New_York"
NORMALIZED_TIMESTAMP = "2026-01-16T00:00:00"
DESKTOP_SOURCE_REVISION = "7041ce9e9921dd4c407595ed3269de1aca43e699"
DESKTOP_REFERENCE_BUILD = "0.2.2.dev0+g7041ce9e"
STORED_FILES = (
    "config.json",
    "leave_years/2026.json",
    "holiday_cache/federal_holidays_2026.json",
)
EXPECTED_BALANCES = {
    "2026-01-13": {"annual": 92.0, "sick": 20.0},
    "2026-01-16": {"annual": 90.0, "sick": 20.0},
    "2026-01-22": {"annual": 86.0, "sick": 20.0},
    "2026-01-24": {"annual": 92.0, "sick": 24.0},
    "2026-01-25": {"annual": 89.0, "sick": 24.0},
    "2027-01-04": {"annual": 233.0, "sick": 120.0},
}
BALANCE_CASES = {
    "2026-01-13": "T-CAL-BAL-02",
    "2026-01-16": "T-CAL-BAL-01",
    "2026-01-22": "T-CAL-BAL-04",
    "2026-01-24": "T-CAL-BAL-03",
    "2026-01-25": "T-CAL-BAL-04",
    "2027-01-04": "T-CAL-BAL-07",
}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def git_head() -> str:
    result = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def cli_env() -> dict[str, str]:
    env = dict(os.environ)
    env["PYTHONPATH"] = str(SRC)
    return env


def run_cli(*args: str, data_dir: Path) -> None:
    subprocess.run(
        [sys.executable, "-m", "fedleave", *args, "--data-dir", str(data_dir)],
        cwd=ROOT,
        env=cli_env(),
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
        text=True,
    )


def build_temp_store() -> Path:
    data_dir = Path(tempfile.mkdtemp(prefix="fedleave-android-fixture-"))
    run_cli(
        "init",
        "--year",
        "2026",
        "--leave-year-start",
        "2026-01-11",
        "--pay-periods",
        "26",
        "--annual-accrual",
        "6",
        "--annual-start",
        "100",
        "--sick-start",
        "20",
        data_dir=data_dir,
    )
    entries = (
        ("2026-01-13", "annual", "used", "8", "approved"),
        ("2026-01-16", "annual", "used", "2", "approved"),
        ("2026-01-22", "annual", "used", "4", "planned"),
        ("2026-01-25", "annual", "used", "3", "planned"),
    )
    for day, category, direction, hours, status in entries:
        run_cli(
            "add",
            "--year",
            "2026",
            "--date",
            day,
            "--category",
            category,
            "--used",
            hours,
            "--status",
            status,
            data_dir=data_dir,
        )
    return data_dir


def normalize_store(data_dir: Path) -> None:
    for name in STORED_FILES:
        path = data_dir / name
        value = json.loads(path.read_text(encoding="utf-8"))
        if name == "leave_years/2026.json":
            for transaction in value["transactions"]:
                if transaction.get("source") == "manual":
                    transaction["created_at"] = NORMALIZED_TIMESTAMP
                    transaction["updated_at"] = NORMALIZED_TIMESTAMP
        if name == "holiday_cache/federal_holidays_2026.json":
            value["generated_at"] = NORMALIZED_TIMESTAMP
        write_json(path, value)


def copy_store(source: Path, destination: Path) -> None:
    if destination.exists():
        shutil.rmtree(destination)
    for name in STORED_FILES:
        target = destination / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source / name, target)


def tree_hashes(base: Path) -> dict[str, str]:
    return {path.relative_to(base).as_posix(): sha256(path) for path in sorted(base.rglob("*")) if path.is_file()}


def tree_digest(files: dict[str, str]) -> str:
    payload = "".join(f"{name}\0{digest}\n" for name, digest in sorted(files.items()))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


class FrozenDate(DateClass):
    @classmethod
    def today(cls) -> "FrozenDate":
        return cls.fromisoformat(FROZEN_DATE)


def invoke_json(application, runner, *args: str, data_dir: Path) -> dict:
    result = runner.invoke(application, [*args, "--json", "--data-dir", str(data_dir)])
    if result.exit_code != 0:
        raise RuntimeError(f"command failed ({result.exit_code}): {args}\n{result.output}")
    return json.loads(result.stdout)


def assert_expected(case_id: str, actual: object, expected: object) -> None:
    if actual != expected:
        raise AssertionError(f"{case_id}: expected {expected!r}, observed {actual!r}")


def write_goldens(data_dir: Path) -> list[dict]:
    import typer.testing

    import fedleave.commands.balances as balances
    from fedleave.cli_app import app

    balances._date = FrozenDate
    runner = typer.testing.CliRunner()
    cases = []

    for day, case_id in BALANCE_CASES.items():
        payload = invoke_json(app, runner, "balance", "--year", "2026", "--as-of", day, data_dir=data_dir)
        for category, expected in EXPECTED_BALANCES[day].items():
            assert_expected(f"{case_id} {category}", payload["balances"][category], expected)
        relative = f"balance-{day}.json"
        write_json(GOLDEN_DIR / relative, payload)
        cases.append(
            {
                "case_id": case_id,
                "operation": "dated_balance",
                "request": {"as_of": day, "local_date": FROZEN_DATE},
                "expected_file": f"android/qa/golden/{FIXTURE_ID}/{relative}",
                "expected": {
                    "year": 2026,
                    "as_of": day,
                    "balances": EXPECTED_BALANCES[day],
                },
                "stored_state": "unchanged",
            }
        )

    period = invoke_json(app, runner, "pay-period", "--year", "2026", "--date", "2026-01-24", data_dir=data_dir)
    assert_expected("period earned annual", period["activity"]["earned"]["annual"], 6.0)
    assert_expected("period used annual", period["activity"]["used"]["annual"], 14.0)
    assert_expected("period ending annual", period["ending_balances"]["annual"], 92.0)
    assert_expected("period ending sick", period["ending_balances"]["sick"], 24.0)
    write_json(GOLDEN_DIR / "pay-period-2026-01-24.json", period)
    cases.append(
        {
            "case_id": "T-CAL-BAL-03",
            "operation": "pay_period_detail",
            "request": {"date": "2026-01-24", "local_date": FROZEN_DATE},
            "expected_file": f"android/qa/golden/{FIXTURE_ID}/pay-period-2026-01-24.json",
            "expected": {
                "period": {"start_date": "2026-01-11", "end_date": "2026-01-24", "pay_date": "2026-01-30"},
                "activity": {"earned": {"annual": 6.0, "sick": 4.0}, "used": {"annual": 14.0, "sick": 0.0}},
                "ending_balances": {"annual": 92.0, "sick": 24.0},
            },
            "stored_state": "unchanged",
        }
    )

    activity = invoke_json(app, runner, "activity", "--year", "2026", "--date", "2026-01-16", data_dir=data_dir)
    assert_expected("day used annual", activity["activity"]["used"]["annual"], 2.0)
    write_json(GOLDEN_DIR / "activity-2026-01-16.json", activity)
    cases.append(
        {
            "case_id": "T-CAL-NAV-01",
            "operation": "day_activity",
            "request": {"date": "2026-01-16", "local_date": FROZEN_DATE},
            "expected_file": f"android/qa/golden/{FIXTURE_ID}/activity-2026-01-16.json",
            "expected": {
                "has_activity": True,
                "activity": {"used": {"annual": 2.0}},
                "physical_absence_hours": 2.0,
            },
            "stored_state": "unchanged",
        }
    )

    month = invoke_json(app, runner, "month", "--year", "2026", "--month", "1", data_dir=data_dir)
    assert_expected("month today", month["today"], FROZEN_DATE)
    assert_expected("month today annual", month["balance_as_of_today"]["balances"]["annual"], 90.0)
    assert_expected("month today sick", month["balance_as_of_today"]["balances"]["sick"], 20.0)
    assert_expected("month today marker", [day["date"] for day in month["days"] if day["is_today"]], [FROZEN_DATE])
    write_json(GOLDEN_DIR / "month-2026-01.json", month)
    cases.append(
        {
            "case_id": "T-CAL-NAV-01",
            "operation": "month_view",
            "request": {"year": 2026, "month": 1, "local_date": FROZEN_DATE},
            "expected_file": f"android/qa/golden/{FIXTURE_ID}/month-2026-01.json",
            "expected": {
                "today": FROZEN_DATE,
                "balance_as_of_today": {"annual": 90.0, "sick": 20.0},
                "is_today": [FROZEN_DATE],
            },
            "stored_state": "unchanged",
        }
    )
    return cases


def make_variant(source: Path, name: str, mutate) -> None:
    destination = STORAGE_DIR / name
    if destination.exists():
        shutil.rmtree(destination)
    shutil.copytree(source, destination)
    mutate(destination)


def mutate_utf8(store: Path) -> None:
    (store / "leave_years" / "2026.json").write_bytes(b'{"schema_version": 1, "leave_year": 2026, "x": "\xff"}\n')


def mutate_future_schema(store: Path) -> None:
    path = store / "leave_years" / "2026.json"
    value = json.loads(path.read_text(encoding="utf-8"))
    value["schema_version"] = 2
    write_json(path, value)


def mutate_invalid_values(store: Path) -> None:
    path = store / "leave_years" / "2026.json"
    value = json.loads(path.read_text(encoding="utf-8"))
    for transaction in value["transactions"]:
        if transaction.get("source") == "manual":
            transaction["hours"] = "four"
            break
    write_json(path, value)


def mutate_duplicate_ids(store: Path) -> None:
    path = store / "leave_years" / "2026.json"
    value = json.loads(path.read_text(encoding="utf-8"))
    manual = next(tx for tx in value["transactions"] if tx.get("source") == "manual")
    value["transactions"].append(dict(manual))
    write_json(path, value)


def mutate_missing_ledger(store: Path) -> None:
    (store / "leave_years" / "2026.json").unlink()


def mutate_malformed_json(store: Path) -> None:
    path = store / "leave_years" / "2026.json"
    path.write_bytes(path.read_bytes()[:120])


def build_storage_fixtures(source: Path) -> None:
    if STORAGE_DIR.exists():
        shutil.rmtree(STORAGE_DIR)
    variants = {
        "invalid-utf8": mutate_utf8,
        "future-schema": mutate_future_schema,
        "invalid-values": mutate_invalid_values,
        "duplicate-ids": mutate_duplicate_ids,
        "missing-ledger": mutate_missing_ledger,
        "malformed-json": mutate_malformed_json,
    }
    cases = []
    for name, mutate in variants.items():
        make_variant(source, name, mutate)
        cases.append(
            {
                "case_id": f"STORE-INVALID-{name.upper().replace('-', '_')}",
                "path": f"android/qa/fixtures/storage/{name}",
                "defect": name,
                "expected_stage": "decode" if name == "invalid-utf8" else "validation",
                "expected": "Reject without replacing the active generation; the prior complete store remains readable.",
                "stored_state": "unchanged",
            }
        )
    write_json(
        STORAGE_DIR / "manifest.json",
        {
            "schema_version": 1,
            "fixture_id": "storage-invalid",
            "base_fixture": FIXTURE_ID,
            "generated_by": "android/qa/fixtures/build_calendar_fixture.py",
            "generator_source_revision": git_head(),
            "frozen_local_date": FROZEN_DATE,
            "timezone": TIMEZONE,
            "cases": cases,
            "notes": [
                "Each variant is a complete copy of the Calendar 2026 store with one defect.",
                "I04 must reject decode, schema, shape, value, uniqueness and completeness defects before publication.",
                "Cross-year and archive semantic validation remains with the restore milestones.",
            ],
        },
    )


def main() -> None:
    sys.path.insert(0, str(SRC))
    temp_store = build_temp_store()
    try:
        normalize_store(temp_store)
        copy_store(temp_store, FIXTURE_DIR)
        golden_cases = write_goldens(FIXTURE_DIR)
        files = tree_hashes(FIXTURE_DIR)
        write_json(
            ROOT / "android" / "qa" / "fixtures" / f"{FIXTURE_ID}.manifest.json",
            {
                "schema_version": 1,
                "fixture_id": FIXTURE_ID,
                "description": "Pinned synthetic Calendar balance fixture for Android bridge, core and store milestones.",
                "generated_by": "android/qa/fixtures/build_calendar_fixture.py",
                "desktop_reference": {
                    "published_build": DESKTOP_REFERENCE_BUILD,
                    "source_revision": DESKTOP_SOURCE_REVISION,
                    "generator_source_revision": git_head(),
                },
                "generator_environment": {
                    "python": sys.version.split()[0],
                    "fedleave": version("fedleave"),
                    "pydantic": version("pydantic"),
                    "holidays": version("holidays"),
                },
                "frozen_local_date": FROZEN_DATE,
                "timezone": TIMEZONE,
                "store": {
                    "path": f"android/qa/fixtures/{FIXTURE_ID}",
                    "files": files,
                    "tree_digest": tree_digest(files),
                    "notes": [
                        "Manual transaction and holiday-cache timestamps are normalized to the frozen local date.",
                        "Automatic accrual rows keep their date-derived audit timestamps.",
                        "Local lock files and backup directories are excluded.",
                    ],
                },
                "cases": golden_cases,
            },
        )
        build_storage_fixtures(FIXTURE_DIR)
        print(f"fixture: {FIXTURE_DIR}")
        print(f"golden:  {GOLDEN_DIR}")
        print(f"storage: {STORAGE_DIR}")
        print(f"tree:    {tree_digest(files)}")
    finally:
        shutil.rmtree(temp_store, ignore_errors=True)


if __name__ == "__main__":
    main()
