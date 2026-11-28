"""The pure standard-library models: identical validation messages, dump
shapes and defaults to the previous implementation."""

import json
import math
from pathlib import Path

import pytest

from useorlose.ledger import create_transaction
from useorlose.transaction_model import Transaction

GOLDEN_DIR = Path(__file__).parent / "fixtures"


@pytest.mark.parametrize(
    "kwargs,expected",
    [
        (
            {"date": "2026-01-13", "category": "bogus", "direction": "used", "hours": 4},
            "category: Value error, Invalid category: bogus. Valid categories: annual, sick, credit, "
            "comp, travel_comp, restored_annual, time_off_award, ppl, fmla, religious_comp, overtime, "
            "admin, lwop, military, court, excused, holiday, flex, other.",
        ),
        (
            {"date": "2026-01-13", "category": "fmla", "direction": "earned", "hours": 4},
            "Value error, FMLA_USAGE_ONLY: FMLA has used transactions only",
        ),
        (
            {"date": "2026-01-13", "category": "ppl", "direction": "worked", "hours": 4},
            "Value error, INVALID_PPL_DIRECTION: PPL accepts earned and used transactions only",
        ),
        (
            {"date": "2026-01-13", "category": "annual", "direction": "used", "hours": -4},
            "hours: Value error, Invalid hours: must be zero or positive. Example: --used 4.0",
        ),
        (
            {"date": "2026-01-13", "category": "annual", "direction": "used", "hours": 4, "status": "bogus"},
            "status: Value error, Invalid status: bogus. Valid statuses: planned, requested, approved, "
            "denied, worked, submitted, certified, reconciled, cancelled.",
        ),
        (
            {"date": "2026-01-13", "category": "annual", "direction": "bad", "hours": 4},
            "direction: Value error, Invalid direction: bad. Valid directions: earned, used, worked, "
            "adjusted, expired, forfeited, paid_out, starting_balance, restored, corrected, reconciled, "
            "forced_increase, forced_decrease.",
        ),
        (
            {"date": "2026-01-13", "category": "annual", "direction": "used", "hours": math.inf},
            "hours: Value error, Invalid hours: must be a finite number",
        ),
    ],
)
def test_transaction_validation_messages_match_the_previous_behavior(kwargs, expected):
    with pytest.raises(ValueError) as excinfo:
        create_transaction(**kwargs)
    assert str(excinfo.value) == expected


def test_transaction_dump_has_all_declared_fields_and_none_defaults():
    row = create_transaction(
        date="2026-01-13",
        category="annual",
        direction="used",
        hours=4,
        description="Appointment",
        existing_ids=[],
    )
    dumped = row.model_dump()
    assert sorted(dumped) == sorted(
        [
            "id",
            "date",
            "category",
            "direction",
            "hours",
            "description",
            "status",
            "source",
            "created_at",
            "updated_at",
            "expiration_date",
            "expiration_pay_period",
            "earned_transaction_id",
            "expiration_extension_reason",
            "expiration_extended_at",
        ]
    )
    assert dumped["hours"] == 4.0
    assert dumped["status"] == "planned"
    assert dumped["source"] == "manual"
    assert dumped["expiration_date"] is None


def test_transaction_normalizes_dates_and_preserves_extra_fields():
    row = Transaction(
        id="20260105-001",
        date="2026-1-5",
        category="annual",
        direction="used",
        hours=1,
        custom_extra="keep",
    )
    dumped = row.model_dump()
    assert dumped["date"] == "2026-01-05"
    assert dumped["custom_extra"] == "keep"


def test_transaction_id_advances_past_existing_ids():
    first = create_transaction(date="2026-01-13", category="annual", direction="used", hours=1, existing_ids=[])
    second = create_transaction(
        date="2026-01-13",
        category="annual",
        direction="used",
        hours=1,
        existing_ids=[first.id],
    )
    assert first.id == "20260113-001"
    assert second.id == "20260113-002"


def test_policy_error_message_for_full_access_restrictions_is_unchanged():
    from useorlose.mcp.policy import Policy

    with pytest.raises(ValueError) as excinfo:
        Policy(mode="full_access", allowed_tools=("balance",))
    assert str(excinfo.value) == "full_access is incompatible with restrictive fields: allowed_tools"


def test_config_model_dump_matches_the_recorded_golden():
    from useorlose.config import Config

    dumped = Config().model_dump()
    golden = json.loads((GOLDEN_DIR / "config-golden.json").read_text(encoding="utf-8"))
    assert dumped == golden
