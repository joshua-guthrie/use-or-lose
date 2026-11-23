import json
from pathlib import Path

import pytest
import typer
from typer.testing import CliRunner

from fedleave.cli import export_data, import_data
from fedleave.cli_app import app
from fedleave.config import init_config
from fedleave.cross_year import manual_ledger


def _init_data_dir(data_dir: Path) -> None:
    init_config(
        year=2026,
        leave_year_start="2026-01-11",
        annual_accrual=6.0,
        starting_balances={
            "annual": 120.0,
            "sick": 180.0,
            "comp": 0.0,
            "credit": 0.0,
            "travel_comp": 0.0,
            "time_off_award": 0.0,
            "religious_comp": 0.0,
            "restored_annual": 0.0,
        },
        data_dir=data_dir,
    )


def test_export_import_round_trip(tmp_path: Path):
    source = tmp_path / "source"
    target = tmp_path / "target"
    archive = tmp_path / "backup.json"
    _init_data_dir(source)

    export_data(output=archive, data_dir=source)
    import_data(input=archive, data_dir=target)

    assert json.loads((target / "config.json").read_text(encoding="utf-8")) == json.loads(
        (source / "config.json").read_text(encoding="utf-8")
    )
    assert json.loads((target / "leave_years" / "2026.json").read_text(encoding="utf-8")) == json.loads(
        (source / "leave_years" / "2026.json").read_text(encoding="utf-8")
    )
    assert (target / "holiday_cache" / "federal_holidays_2026.json").exists()


def test_import_refuses_overwrite_without_flag(tmp_path: Path):
    source = tmp_path / "source"
    target = tmp_path / "target"
    archive = tmp_path / "backup.json"
    _init_data_dir(source)
    _init_data_dir(target)
    export_data(output=archive, data_dir=source)

    with pytest.raises(typer.Exit):
        import_data(input=archive, data_dir=target)


def test_import_merge_adds_missing_transactions_and_keeps_current_conflicts(tmp_path: Path):
    target = tmp_path / "target"
    archive = tmp_path / "backup.json"
    _init_data_dir(target)
    year_path = target / "leave_years" / "2026.json"
    current = json.loads(year_path.read_text(encoding="utf-8"))
    current["transactions"] = [
        {
            "id": "20260701-001",
            "date": "2026-07-01",
            "category": "annual",
            "direction": "used",
            "hours": 1,
            "status": "approved",
            "description": "Current version",
        }
    ]
    year_path.write_text(json.dumps(current), encoding="utf-8")

    imported = dict(current)
    imported["transactions"] = [
        {
            **current["transactions"][0],
            "description": "Imported version",
        },
        {
            **current["transactions"][0],
            "id": "20260702-001",
            "date": "2026-07-02",
            "description": "Imported missing transaction",
        },
    ]
    archive.write_text(json.dumps(imported), encoding="utf-8")

    import_data(input=archive, merge=True, data_dir=target)

    merged = json.loads(year_path.read_text(encoding="utf-8"))
    assert merged["transactions"] == [current["transactions"][0], imported["transactions"][1]]
    backups = list((target / "backups").glob("2026.json.*.bak"))
    assert len(backups) == 1


def _force_balance_fixture(path: Path, year: int = 2026, start: str = "2026-01-11") -> None:
    ledger_path = path / "leave_years" / f"{year}.json"
    ledger_path.parent.mkdir(parents=True, exist_ok=True)
    ledger = manual_ledger(year, start, 0, {"annual": 20, "sick": 20}, pay_periods=26)
    ledger_path.write_text(json.dumps(ledger), encoding="utf-8")


def _invoke(runner: CliRunner, store: Path, *arguments: str):
    result = runner.invoke(app, [*arguments, "--data-dir", str(store)])
    assert result.exit_code == 0, result.output
    return result


def _balances_on_february_first(runner: CliRunner, store: Path) -> dict:
    result = _invoke(runner, store, "balance", "--year", "2026", "--as-of", "2026-02-01", "--json")
    return json.loads(result.output)["balances"]


def _snapshot_files(store: Path) -> dict[Path, bytes]:
    return {path.relative_to(store): path.read_bytes() for path in store.rglob("*") if path.is_file()}


@pytest.mark.parametrize("distinct_ids", [True, False], ids=["different-ids", "same-id"])
@pytest.mark.parametrize("legacy", [False, True], ids=["archive", "legacy-year"])
def test_import_merge_blocks_competing_forced_balances(tmp_path: Path, distinct_ids: bool, legacy: bool):
    runner = CliRunner()
    current = tmp_path / "current"
    incoming = tmp_path / "incoming"
    archive = tmp_path / "backup.json"
    for store in (current, incoming):
        _force_balance_fixture(store)

    if distinct_ids:
        _invoke(
            runner,
            incoming,
            "add",
            "--date",
            "2026-02-01",
            "--category",
            "sick",
            "--used",
            "1",
            "--status",
            "approved",
        )

    force_args = ("force-balance", "--date", "2026-02-01", "--category", "annual", "--comment", "Synthetic target")
    _invoke(runner, current, *force_args, "--hours", "5")
    _invoke(runner, incoming, *force_args, "--hours", "8")
    assert _balances_on_february_first(runner, current)["annual"] == 5
    assert _balances_on_february_first(runner, incoming)["annual"] == 8

    if legacy:
        archive.write_bytes((incoming / "leave_years" / "2026.json").read_bytes())
    else:
        # A conflict must abort publication of earlier files in the same archive.
        _force_balance_fixture(incoming, 2025, "2025-01-12")
        cache = incoming / "holiday_cache" / "federal_holidays_2026.json"
        cache.parent.mkdir()
        cache.write_text("{}", encoding="utf-8")
        _invoke(runner, incoming, "export-data", "--output", str(archive))
    original_archive = archive.read_bytes()
    original_store = _snapshot_files(current)

    args = ["import-data", "--merge", "--input", str(archive), "--data-dir", str(current)]
    preview = runner.invoke(app, [*args, "--dry-run", "--json"])
    assert preview.exit_code == 2, preview.output
    report = json.loads(preview.output)
    assert report["status"] == "blocked"
    conflict = report["conflicts"][0]
    assert (conflict["code"], conflict["date"], conflict["category"]) == (
        "FORCED_BALANCE_CONFLICT",
        "2026-02-01",
        "annual",
    )
    assert conflict["current"] == {"balance": 5.0, "adjustments": [-15.0], "ids": ["20260201-001"]}
    assert conflict["incoming"] == {
        "balance": 8.0,
        "adjustments": [-12.0],
        "ids": ["20260201-002" if distinct_ids else "20260201-001"],
    }
    assert _snapshot_files(current) == original_store
    result = runner.invoke(app, args)
    assert archive.read_bytes() == original_archive
    annual = _balances_on_february_first(runner, current)["annual"]

    assert result.exit_code != 0, result.output
    assert "Imported " not in result.output
    assert "FORCED_BALANCE_CONFLICT" in result.output
    assert "2026-02-01" in result.output and "annual" in result.output.lower()
    assert annual == 5
    assert _snapshot_files(current) == original_store


def test_import_merge_accepts_forced_balances_for_independent_categories(tmp_path: Path):
    runner = CliRunner()
    current = tmp_path / "current"
    incoming = tmp_path / "incoming"
    archive = tmp_path / "backup.json"
    for store in (current, incoming):
        _force_balance_fixture(store)

    date_args = ("--date", "2026-02-01")
    _invoke(runner, current, "force-balance", *date_args, "--category", "annual", "--hours", "5", "--comment", "Annual")
    # Give the imported sick adjustment a different ID from the current annual adjustment.
    _invoke(runner, incoming, "add", *date_args, "--category", "comp", "--earned", "1", "--status", "approved")
    _invoke(runner, incoming, "force-balance", *date_args, "--category", "sick", "--hours", "8", "--comment", "Sick")
    _invoke(runner, incoming, "void", "--id", "20260201-001")
    _invoke(runner, incoming, "export-data", "--output", str(archive))

    for _ in range(2):
        _invoke(runner, current, "import-data", "--merge", "--input", str(archive))
        balances = _balances_on_february_first(runner, current)
        assert (balances["annual"], balances["sick"]) == (5, 8)


def test_import_rejects_merge_and_overwrite_together(tmp_path: Path):
    archive = tmp_path / "backup.json"
    archive.write_text(
        json.dumps({"schema_version": 1, "leave_years": {}, "holiday_cache": {}}),
        encoding="utf-8",
    )

    with pytest.raises(typer.Exit) as excinfo:
        import_data(input=archive, merge=True, overwrite=True, data_dir=tmp_path / "target")

    assert excinfo.value.exit_code == 2


def test_import_accepts_single_leave_year_backup(tmp_path: Path):
    target = tmp_path / "target"
    archive = tmp_path / "single-leave-year-backup.json"
    legacy_leave_year = {
        "schema_version": 1,
        "leave_year": 2026,
        "leave_year_start": "2026-01-11",
        "leave_year_end": "2027-01-09",
        "pay_period_count": 1,
        "annual_leave_accrual_hours": 6.0,
        "sick_leave_accrual_hours": 4.0,
        "starting_balances": {"annual": 120.0, "sick": 180.0},
        "carryover_from_previous_year": {"annual": 120.0, "sick": 180.0},
        "transactions": [
            {
                "id": "20260124-001",
                "date": "2026-01-24",
                "category": "annual",
                "direction": "earned",
                "hours": 6.0,
                "description": "Automatic annual leave accrual",
                "status": "reconciled",
                "source": "auto_accrual",
            }
        ],
        "pay_periods": [
            {
                "pay_period_number": 1,
                "start_date": "2026-01-11",
                "end_date": "2026-01-24",
                "accrual_date": "2026-01-24",
            }
        ],
        "holidays": [],
    }
    archive.write_text(json.dumps(legacy_leave_year), encoding="utf-8")

    import_data(input=archive, data_dir=target)

    imported = json.loads((target / "leave_years" / "2026.json").read_text(encoding="utf-8"))
    assert imported == legacy_leave_year
    assert not (target / "config.json").exists()


def test_import_removes_legacy_transaction_history(tmp_path: Path):
    target = tmp_path / "target"
    archive = tmp_path / "legacy.json"
    active = {
        "id": "active",
        "date": "2026-01-12",
        "category": "annual",
        "direction": "used",
        "hours": 1.0,
        "status": "approved",
    }
    ledger = manual_ledger(2026, "2026-01-11", 0, {"annual": 20}, pay_periods=26)
    legacy = {
        **ledger,
        "transactions": [
            {**active, "void": False, "void_reason": None, "reconcile_history": []},
            {"id": "obsolete", "void": True, "void_reason": "replaced"},
        ],
        "starting_balance_history": [{"old_hours": 100.0, "new_hours": 120.0}],
    }
    archive.write_text(json.dumps(legacy), encoding="utf-8")

    import_data(input=archive, data_dir=target)

    imported = json.loads((target / "leave_years" / "2026.json").read_text(encoding="utf-8"))
    assert imported["transactions"] == [active]
    assert "starting_balance_history" not in imported


def test_import_preserves_existing_current_year_file(tmp_path: Path):
    target = tmp_path / "target"
    archive = tmp_path / "previous-year.json"
    current_year = manual_ledger(2026, "2026-01-11", 6, {"annual": 120.0, "sick": 180.0}, pay_periods=26)
    previous_year = manual_ledger(2025, "2025-01-12", 6, {"annual": 100.0, "sick": 160.0}, pay_periods=26)
    target_years = target / "leave_years"
    target_years.mkdir(parents=True)
    (target_years / "2026.json").write_text(json.dumps(current_year), encoding="utf-8")
    archive.write_text(
        json.dumps({"schema_version": 1, "leave_years": {"2025": previous_year}, "holiday_cache": {}}), encoding="utf-8"
    )

    import_data(input=archive, data_dir=target)

    assert json.loads((target / "leave_years" / "2026.json").read_text(encoding="utf-8")) == current_year
    assert json.loads((target / "leave_years" / "2025.json").read_text(encoding="utf-8")) == previous_year


def test_import_validates_the_entire_archive_before_writing(tmp_path: Path):
    target = tmp_path / "target"
    archive = tmp_path / "invalid.json"
    archive.write_text(
        json.dumps(
            {
                "schema_version": 1,
                "config": {"schema_version": 1, "user": {"display_name": "Imported"}},
                "leave_years": {
                    "2026": {
                        "schema_version": 1,
                        "leave_year": 2026,
                        "leave_year_start": "2026-01-11",
                        "leave_year_end": "2027-01-09",
                        "pay_period_count": 1,
                        "annual_leave_accrual_hours": 6.0,
                        "sick_leave_accrual_hours": 4.0,
                        "starting_balances": {"annual": 0.0, "sick": 0.0},
                        "carryover_from_previous_year": {"annual": 0.0, "sick": 0.0},
                        "transactions": [],
                        "pay_periods": [],
                        "holidays": [],
                    }
                },
                "holiday_cache": {"bad/name": {"year": 2026}},
            }
        ),
        encoding="utf-8",
    )

    with pytest.raises(typer.Exit) as excinfo:
        import_data(input=archive, data_dir=target)

    assert excinfo.value.exit_code == 2
    assert not target.exists()
