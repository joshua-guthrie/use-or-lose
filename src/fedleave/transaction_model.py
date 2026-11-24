"""The validated transaction model, split out of ``ledger.py``.

This module owns the pydantic model so that the read-only balance and activity
calculations in ``ledger.py`` stay importable without pydantic. Android bridge
code imports the read functions without pulling pydantic into the app.
"""

from __future__ import annotations

from datetime import datetime
from math import isfinite

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from .categories import CATEGORY_POLICIES
from .transaction_effects import TRANSACTION_DIRECTIONS as EFFECT_DIRECTIONS
from .transaction_effects import TRANSACTION_STATUSES as EFFECT_STATUSES

TRANSACTION_CATEGORIES = list(CATEGORY_POLICIES)
TRANSACTION_DIRECTIONS = list(EFFECT_DIRECTIONS)
TRANSACTION_STATUSES = list(EFFECT_STATUSES)


class Transaction(BaseModel):
    """A validated, persistable event that changes or describes leave hours.

    Pydantic validators run when an instance is created, providing one boundary
    for category, direction, status, date, and hour validation.
    """

    model_config = ConfigDict(extra="allow")

    id: str
    date: str
    category: str
    direction: str
    hours: float
    description: str = ""
    status: str = "planned"
    source: str = "manual"
    created_at: str = Field(default_factory=lambda: datetime.now().isoformat())
    updated_at: str = Field(default_factory=lambda: datetime.now().isoformat())
    expiration_date: str | None = None
    expiration_pay_period: int | None = None
    earned_transaction_id: str | None = None
    expiration_extension_reason: str | None = None
    expiration_extended_at: str | None = None

    @model_validator(mode="after")
    def validate_parental_direction(self):
        if self.category == "fmla" and self.direction != "used":
            raise ValueError("FMLA_USAGE_ONLY: FMLA has used transactions only")
        if self.category == "ppl" and self.direction not in {"earned", "used"}:
            if not (self.direction == "expired" and self.source == "expiration-engine"):
                raise ValueError("INVALID_PPL_DIRECTION: PPL accepts earned and used transactions only")
        return self

    @field_validator("category")
    def validate_category(cls, value: str) -> str:
        if value not in TRANSACTION_CATEGORIES:
            raise ValueError(f"Invalid category: {value}. Valid categories: {', '.join(TRANSACTION_CATEGORIES)}.")
        return value

    @field_validator("direction")
    def validate_direction(cls, value: str) -> str:
        if value not in TRANSACTION_DIRECTIONS:
            raise ValueError(f"Invalid direction: {value}. Valid directions: {', '.join(TRANSACTION_DIRECTIONS)}.")
        return value

    @field_validator("status")
    def validate_status(cls, value: str) -> str:
        if value not in TRANSACTION_STATUSES:
            raise ValueError(f"Invalid status: {value}. Valid statuses: {', '.join(TRANSACTION_STATUSES)}.")
        return value

    @field_validator("hours")
    def validate_hours(cls, value: float) -> float:
        if not isfinite(value):
            raise ValueError("Invalid hours: must be a finite number")
        if value < 0:
            raise ValueError("Invalid hours: must be zero or positive. Example: --used 4.0")
        return value

    @field_validator("date")
    def validate_date(cls, value: str) -> str:
        from .ledger import _parse_iso_date

        try:
            normalized = _parse_iso_date(value).isoformat()
        except ValueError:
            raise ValueError(f"Invalid date: {value}. Use YYYY-MM-DD, for example 2026-01-11.")
        return normalized
