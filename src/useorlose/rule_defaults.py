"""Canonical default category rules, kept pydantic-free.

Both the desktop configuration model and the platform-independent read paths
derive their defaults from this table, so the phone bridge can resolve policy
without importing pydantic.
"""

from __future__ import annotations

DEFAULT_RULES: dict[str, dict] = {
    "annual": {
        "carryover_limit_hours": 240.0,
        "track_use_or_lose": True,
        "allow_restored_leave": True,
    },
    "sick": {
        "carryover_limit_hours": None,
        "expires": False,
    },
    "comp": {
        "expires": True,
        "expiration_pay_periods_after_earned": 26,
        "expiration_action": "warn",
        "allow_payout_tracking": True,
        "allow_forfeiture_tracking": True,
        "allow_extension": True,
        "reminder_pay_periods": [1, 3, 6, 12],
    },
    "travel_comp": {
        "expires": True,
        "expiration_pay_periods_after_earned": 26,
        "expiration_action": "forfeit",
        "allow_extension": True,
        "extension_pay_periods": 26,
        "reminder_pay_periods": [1, 3, 6, 12],
    },
    "credit": {
        "enabled": True,
        "max_carryover_hours": 24.0,
        "max_earn_per_day_hours": None,
        "max_earn_per_pay_period_hours": None,
        "max_balance_hours": 24.0,
        "usable_when": "next_day",
        "same_day_use_allowed": False,
        "same_pay_period_use_allowed": True,
        "expires": False,
        "forfeit_excess_at_pay_period_rollover": True,
        "requires_flexible_work_schedule": True,
    },
    "time_off_award": {
        "expires": False,
        "expiration_days_after_earned": None,
        "agency_policy_required": True,
        "expiration_action": "forfeit",
        "allow_extension": True,
        "reminder_pay_periods": [1, 3, 6, 12],
    },
    "restored_annual": {
        "expires": True,
        "expiration_leave_years_after_restored": 2,
        "expiration_action": "forfeit",
        "allow_extension": True,
        "reminder_pay_periods": [1, 3, 6, 12],
    },
    "religious_comp": {
        "expires": False,
        "agency_policy_required": True,
    },
    "ppl": {"expires": True, "expiration_action": "expire", "allow_extension": False},
    "fmla": {"carryover_mode": "usage_tracking", "weekly_hours": None, "history_complete_from": None},
}
