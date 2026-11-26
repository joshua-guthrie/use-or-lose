"""Accounting and publishing regressions for issue #152."""

import json
from copy import deepcopy
from pathlib import Path

import pytest
from typer.testing import CliRunner

from leave_year_support import save, store, tree, tx
from useorlose.cli import app
from useorlose.cross_year import manual_ledger
from useorlose.import_storage import JOURNAL, publish_import
from useorlose.importing import ImportPlan, plan_import
from useorlose.storage import atomic_write_json, load_json

YEAR = "leave_years/2026.json"


def cli(base, *args, success=True):
    result = CliRunner().invoke(app, [*args, "--data-dir", str(base), "--json"])
    assert (result.exit_code == 0) == success, result.output
    return json.loads(result.output) if success else result


def _ledger():
    return manual_ledger(2026, "2026-01-11", 0, {"annual": 20}, pay_periods=26)


def _plan(current, incoming):
    return plan_import(
        {YEAR: current},
        {"schema_version": 1, "leave_years": {"2026": incoming}},
        "merge",
        current_identity="current",
        archive_identity="backup",
    )


@pytest.mark.parametrize("field", ["opening", "schedule", "accrual", "rate history", "baseline", "lineage"])
def test_incompatible_year_basis_blocks_entire_candidate(field):
    current = _ledger()
    incoming = deepcopy(current)
    if field == "opening":
        incoming["starting_balances"]["annual"] = 24
    elif field == "schedule":
        incoming = manual_ledger(2026, "2026-01-04", 0, {"annual": 20}, pay_periods=26)
    elif field == "accrual":
        incoming["annual_leave_accrual_hours"] = 2
    elif field == "rate history":
        incoming["accrual_rate_changes"] = [
            {"category": "annual", "effective_date": "2026-01-24", "hours_per_pay_period": 2}
        ]
    elif field == "baseline":
        incoming["initialization"]["baseline_as_of"] = "2026-02-01"
    else:
        incoming["initialization"] = {"mode": "carryover", "method": "carryover", "source_leave_year": 2025}
    plan = _plan(current, incoming)
    assert not plan.writes
    assert plan.conflicts[0]["code"] == "INCOMPATIBLE_LEDGER_BASIS"


def _adjustment(row_id, hours=15, day="2026-02-01"):
    return {
        **tx(row_id, day, "annual", "forced_decrease", hours),
        "source": "forced-balance",
        "status": "reconciled",
    }


@pytest.mark.parametrize("id_matches", [True, False])
def test_equivalent_forces_keep_existing_identity(id_matches):
    current = _ledger()
    incoming = deepcopy(current)
    current["transactions"].append(_adjustment("current"))
    incoming["transactions"].append(_adjustment("current" if id_matches else "backup"))
    before_current, before_incoming = deepcopy(current), deepcopy(incoming)
    plan = _plan(current, incoming)
    assert plan.summary()["status"] == "ready"
    assert not plan.writes
    assert (current, incoming) == (before_current, before_incoming)


def test_equivalent_force_with_a_referenced_incoming_id_blocks():
    current = _ledger()
    incoming = deepcopy(current)
    current["transactions"].append(_adjustment("current"))
    incoming["transactions"].extend(
        [_adjustment("backup"), tx("record", "2026-02-02", "sick", "used", 1, linked_transaction_id="backup")]
    )
    plan = _plan(current, incoming)
    assert plan.conflicts[0]["code"] == "FORCED_BALANCE_CONFLICT"
    assert not plan.writes


def test_changed_ordinary_entry_cannot_change_a_reviewed_force_balance():
    current = _ledger()
    incoming = deepcopy(current)
    current["transactions"].append(_adjustment("current"))
    incoming["transactions"].append(tx("earlier", "2026-01-30", "annual", "used", 2))
    result = _plan(current, incoming)
    assert not result.writes
    conflict = result.conflicts[0]
    assert conflict["code"] == "FORCED_BALANCE_BASIS_CHANGED"
    assert (conflict["expected_balance"], conflict["proposed_balance"]) == (5, 3)


def test_distinct_dates_are_compatible_when_an_earlier_force_is_shared():
    current = _ledger()
    current["transactions"].append(_adjustment("old"))
    incoming = deepcopy(current)
    incoming["transactions"].append(_adjustment("later", 1, "2026-02-02"))
    plan = _plan(current, incoming)
    assert not plan.conflicts
    assert [row["id"] for row in plan.writes[YEAR]["transactions"][-2:]] == ["old", "later"]


def test_inactive_force_and_unrelated_negative_balance_are_mergeable():
    current = _ledger()
    incoming = deepcopy(current)
    current["transactions"].append({**_adjustment("inactive"), "status": "cancelled"})
    incoming["transactions"].extend([_adjustment("backup"), tx("negative", "2026-02-02", "annual", "used", 6)])
    plan = _plan(current, incoming)
    assert not plan.conflicts
    assert [row["id"] for row in plan.writes[YEAR]["transactions"][-3:]] == ["inactive", "backup", "negative"]


def test_equivalent_legacy_schedule_and_mirrored_opening_keep_current_authority():
    current = _ledger()
    current["transactions"].append(tx("opening", "2026-01-11", "annual", "starting_balance", 20))
    incoming = deepcopy(current)
    incoming.pop("initialization")
    incoming["transactions"].pop()
    incoming["transactions"].append(tx("used", "2026-02-02", "annual", "used", 1, extra_field={"preserve": "yes"}))
    for row in incoming["pay_periods"]:
        row.pop("pay_date")
    plan = _plan(current, incoming)
    assert not plan.conflicts
    result = plan.writes[YEAR]
    assert result["transactions"][-2:] == [current["transactions"][-1], incoming["transactions"][-1]]


@pytest.mark.parametrize("changed", ["active", "archive"])
def test_review_is_bound_to_both_exact_inputs(tmp_path: Path, changed: str):
    base = tmp_path / "store"
    ledger, _ = store(base)
    incoming = deepcopy(ledger)
    incoming["transactions"].append(tx("new", "2026-02-02", "annual", "used", 1))
    archive = tmp_path / "backup.json"
    atomic_write_json(archive, incoming)
    arguments = ["import-data", "--input", str(archive), "--merge"]
    before = tree(base)
    preview = cli(base, *arguments, "--dry-run")
    assert preview["status"] == "ready" and tree(base) == before

    path = base / YEAR if changed == "active" else archive
    path.write_bytes(path.read_bytes() + b"\n")
    after = tree(base)
    result = cli(base, *arguments, "--expected-fingerprint", preview["preview_fingerprint"], success=False)
    assert "STALE_IMPORT_PREVIEW" in result.output
    assert tree(base) == after


@pytest.mark.parametrize("interruption", [True, False])
def test_failed_import_restores_byte_exact_files_and_removes_new_members(tmp_path, monkeypatch, interruption):
    base = tmp_path / "store"
    ledger, _ = store(base)
    original_bytes = (base / YEAR).read_bytes()
    added = manual_ledger(2027, "2027-01-10", 0, {}, pay_periods=26)
    plan = ImportPlan("replace", "token", writes={"leave_years/2027.json": added, YEAR: {**ledger, "new": "value"}})
    real_write = atomic_write_json

    def fail_on_last_write(path, payload, **kwargs):
        if path.name == "2026.json":
            if interruption:
                raise KeyboardInterrupt
            raise OSError("simulated write failure")
        real_write(path, payload, **kwargs)

    with monkeypatch.context() as scoped:
        scoped.setattr("useorlose.import_storage.atomic_write_json", fail_on_last_write)
        with pytest.raises(KeyboardInterrupt if interruption else ValueError):
            publish_import(base, plan)
    if interruption:
        assert (base / JOURNAL).exists()
        assert (base / "leave_years/2027.json").exists()
        blocked = cli(base, "years", success=False)
        assert "RECOVERY_REQUIRED" in blocked.output
        result = cli(base, "import-data", "--recover")
        assert result["status"] == "recovered"
    assert (base / YEAR).read_bytes() == original_bytes
    assert not (base / "leave_years/2027.json").exists()
    assert not (base / JOURNAL).exists()


def test_replacing_source_validates_already_stored_linked_successors(tmp_path):
    base = tmp_path / "store"
    ledger, _ = store(base, transactions=[tx("lot", "2026-06-01", "comp", "earned", 8, expiration_date="2027-06-01")])
    cli(base, "init", "--year", "2027", "--from-year", "2026")
    cli(base, "add", "--date", "2027-02-01", "--category", "comp", "--used", "6")
    archive = tmp_path / "backup.json"
    ledger["transactions"][0]["hours"] = 4
    atomic_write_json(archive, ledger)
    before = tree(base)
    result = cli(base, "import-data", "--input", str(archive), "--overwrite", success=False)
    assert "CONFLICTING_CARRYOVER" in result.output
    assert tree(base) == before


def test_reviewed_replace_backs_up_included_year_and_preserves_omitted_year(tmp_path):
    base = tmp_path / "store"
    ledger, _ = store(base)
    other = manual_ledger(2027, "2027-01-10", 0, {}, pay_periods=26)
    save(base, 2027, other)
    omitted_bytes = (base / "leave_years/2027.json").read_bytes()
    original_bytes = (base / YEAR).read_bytes()
    archive = tmp_path / "backup.json"
    atomic_write_json(archive, {**ledger, "starting_balances": {"annual": 8}})
    archive_bytes = archive.read_bytes()
    args = ["import-data", "--input", str(archive), "--overwrite"]
    preview = cli(base, *args, "--dry-run")
    cli(base, *args, "--expected-fingerprint", preview["preview_fingerprint"])
    assert load_json(base / YEAR)["starting_balances"]["annual"] == 8
    assert [path.read_bytes() for path in (base / "backups").glob("2026.json.*.bak")] == [original_bytes]
    assert (base / "leave_years/2027.json").read_bytes() == omitted_bytes
    assert archive.read_bytes() == archive_bytes
