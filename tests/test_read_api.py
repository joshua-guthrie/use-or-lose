"""Golden and contract tests for the versioned read facade."""

from __future__ import annotations

import json
from copy import deepcopy
from pathlib import Path

import pytest

from fedleave.read_api import (
    ReadApiError,
    dated_balance,
    day_activity,
    month_view,
    pay_period,
    read,
)

ROOT = Path(__file__).resolve().parent.parent
FIXTURE = ROOT / "android" / "qa" / "fixtures" / "calendar-2026"
GOLDEN = ROOT / "android" / "qa" / "golden" / "calendar-2026"
LOCAL_DATE = "2026-01-16"


def _load(relative: str) -> dict:
    return json.loads((FIXTURE / relative).read_text(encoding="utf-8"))


def _golden(name: str) -> dict:
    return json.loads((GOLDEN / name).read_text(encoding="utf-8"))


@pytest.fixture()
def files() -> dict[str, dict]:
    return {
        "config.json": _load("config.json"),
        "leave_years/2026.json": _load("leave_years/2026.json"),
        "holiday_cache/federal_holidays_2026.json": _load("holiday_cache/federal_holidays_2026.json"),
    }


def _assert_unchanged(files: dict[str, dict]) -> None:
    expected = {
        "config.json": _load("config.json"),
        "leave_years/2026.json": _load("leave_years/2026.json"),
        "holiday_cache/federal_holidays_2026.json": _load("holiday_cache/federal_holidays_2026.json"),
    }
    assert files == expected


@pytest.mark.parametrize(
    ("as_of", "golden_name"),
    [
        ("2026-01-13", "balance-2026-01-13.json"),
        ("2026-01-16", "balance-2026-01-16.json"),
        ("2026-01-22", "balance-2026-01-22.json"),
        ("2026-01-24", "balance-2026-01-24.json"),
        ("2026-01-25", "balance-2026-01-25.json"),
        ("2027-01-04", "balance-2027-01-04.json"),
    ],
)
def test_dated_balance_matches_golden(files, as_of, golden_name) -> None:
    before = deepcopy(files)
    result = dated_balance(files, {"as_of": as_of}, local_date=LOCAL_DATE)
    assert result == _golden(golden_name)
    _assert_unchanged(files)
    assert before == files


def test_pay_period_matches_golden(files) -> None:
    result = pay_period(files, {"date": "2026-01-24"}, local_date=LOCAL_DATE)
    assert result == _golden("pay-period-2026-01-24.json")
    _assert_unchanged(files)


def test_pay_period_includes_daily_rows_on_request(files) -> None:
    result = pay_period(files, {"date": "2026-01-24", "daily": True}, local_date=LOCAL_DATE)
    rows = result["daily_activity"]
    assert rows is not None
    assert len(rows) == 14
    january_16 = next(row for row in rows if row["date"] == "2026-01-16")
    assert january_16["used"]["annual"] == 2.0
    _assert_unchanged(files)


def test_day_activity_matches_golden(files) -> None:
    result = day_activity(files, {"date": "2026-01-16"}, local_date=LOCAL_DATE)
    assert result == _golden("activity-2026-01-16.json")
    _assert_unchanged(files)


def test_month_view_matches_golden(files) -> None:
    result = month_view(files, {"year": 2026, "month": 1}, local_date=LOCAL_DATE)
    assert result == _golden("month-2026-01.json")
    _assert_unchanged(files)


def test_read_dispatch_returns_versioned_envelope(files) -> None:
    envelope = read(files, {"operation": "dated_balance", "as_of": "2026-01-16"}, local_date=LOCAL_DATE)
    assert envelope["schema_version"] == 1
    assert envelope["ok"] is True
    assert envelope["result"] == _golden("balance-2026-01-16.json")


def test_read_unknown_operation(files) -> None:
    with pytest.raises(ReadApiError) as info:
        read(files, {"operation": "forecast"}, local_date=LOCAL_DATE)
    assert info.value.code == "UNKNOWN_OPERATION"
    assert info.value.field == "operation"


def test_missing_config_is_a_typed_error(files) -> None:
    broken = {key: value for key, value in files.items() if key != "config.json"}
    with pytest.raises(ReadApiError) as info:
        dated_balance(broken, {"as_of": "2026-01-16"}, local_date=LOCAL_DATE)
    assert info.value.code == "MISSING_CONFIG"
    assert info.value.field == "config.json"


def test_uncovered_date_is_a_typed_error(files) -> None:
    with pytest.raises(ReadApiError) as info:
        dated_balance(files, {"as_of": "2025-06-01"}, local_date=LOCAL_DATE)
    assert info.value.code == "LEAVE_YEAR_NOT_FOUND"


def test_invalid_date_and_month_are_typed_errors(files) -> None:
    with pytest.raises(ReadApiError) as info:
        dated_balance(files, {"as_of": "not-a-date"}, local_date=LOCAL_DATE)
    assert info.value.code == "INVALID_DATE"

    with pytest.raises(ReadApiError) as info:
        month_view(files, {"year": 2026, "month": 13}, local_date=LOCAL_DATE)
    assert info.value.code == "INVALID_MONTH"


def test_malformed_snapshot_is_a_typed_error() -> None:
    with pytest.raises(ReadApiError) as info:
        dated_balance(
            {"config.json": {}, "leave_years/2026.json": "not-an-object"},
            {"as_of": "2026-01-16"},
            local_date=LOCAL_DATE,
        )
    assert info.value.code == "INVALID_SNAPSHOT"


def test_reads_are_pure_across_repeated_calls(files) -> None:
    first = dated_balance(files, {"as_of": "2026-01-16"}, local_date=LOCAL_DATE)
    second = dated_balance(files, {"as_of": "2026-01-16"}, local_date=LOCAL_DATE)
    assert first == second == _golden("balance-2026-01-16.json")
    _assert_unchanged(files)
