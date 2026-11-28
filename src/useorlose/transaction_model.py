"""The validated transaction model, pure standard library.

Owns the transaction-row shape and validation so the shared accounting
core stays importable on Linux, Windows and the Android runtime without
compiled dependencies. The validation rules, message text and dump shape
are unchanged from the previous pydantic implementation.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from math import isfinite

from .categories import CATEGORY_POLICIES
from .pure_models import FieldValidationError, PureModel, model_dump
from .transaction_effects import TRANSACTION_DIRECTIONS as EFFECT_DIRECTIONS
from .transaction_effects import TRANSACTION_STATUSES as EFFECT_STATUSES

TRANSACTION_CATEGORIES = list(CATEGORY_POLICIES)
TRANSACTION_DIRECTIONS = list(EFFECT_DIRECTIONS)
TRANSACTION_STATUSES = list(EFFECT_STATUSES)


@dataclass(init=False)
class Transaction(PureModel):
    """A validated, persistable event that changes or describes leave hours.

    Validation runs at construction, providing one boundary for category,
    direction, status, date and hour rules. Messages match the pydantic
    implementation byte-for-byte so CLI output is unchanged.
    """

    __extra__ = "allow"

    id: str
    date: str
    category: str
    direction: str
    hours: float
    description: str = ""
    status: str = "planned"
    source: str = "manual"
    created_at: str = field(default_factory=lambda: datetime.now().isoformat())
    updated_at: str = field(default_factory=lambda: datetime.now().isoformat())
    expiration_date: str | None = None
    expiration_pay_period: int | None = None
    earned_transaction_id: str | None = None
    expiration_extension_reason: str | None = None
    expiration_extended_at: str | None = None

    def __post_init__(self) -> None:
        # Lazy import mirrors the previous validator's local import and
        # avoids a module cycle (ledger imports this module).
        from .ledger import _parse_iso_date

        if self.category not in TRANSACTION_CATEGORIES:
            raise FieldValidationError(
                "category",
                f"Value error, Invalid category: {self.category}. "
                f"Valid categories: {', '.join(TRANSACTION_CATEGORIES)}.",
            )
        if self.direction not in TRANSACTION_DIRECTIONS:
            raise FieldValidationError(
                "direction",
                f"Value error, Invalid direction: {self.direction}. "
                f"Valid directions: {', '.join(TRANSACTION_DIRECTIONS)}.",
            )
        if self.status not in TRANSACTION_STATUSES:
            raise FieldValidationError(
                "status",
                f"Value error, Invalid status: {self.status}. "
                f"Valid statuses: {', '.join(TRANSACTION_STATUSES)}.",
            )
        # The previous model coerced numeric input to float; the CLI always
        # passes floats, and invalid values surface through the same rules.
        try:
            hours = float(self.hours)
        except (TypeError, ValueError):
            hours = float("nan")
        if not isfinite(hours):
            raise FieldValidationError("hours", "Value error, Invalid hours: must be a finite number")
        if hours < 0:
            raise FieldValidationError(
                "hours",
                "Value error, Invalid hours: must be zero or positive. Example: --used 4.0",
            )
        object.__setattr__(self, "hours", hours)

        try:
            normalized = _parse_iso_date(self.date).isoformat()
        except ValueError:
            raise FieldValidationError(
                "date",
                f"Value error, Invalid date: {self.date}. "
                "Use YYYY-MM-DD, for example 2026-01-11.",
            ) from None
        object.__setattr__(self, "date", normalized)

        if self.category == "fmla" and self.direction != "used":
            raise ValueError("Value error, FMLA_USAGE_ONLY: FMLA has used transactions only")
        if self.category == "ppl" and self.direction not in {"earned", "used"}:
            if not (self.direction == "expired" and self.source == "expiration-engine"):
                raise ValueError(
                    "Value error, INVALID_PPL_DIRECTION: PPL accepts earned and used transactions only"
                )

    @classmethod
    def model_validate(cls, value: dict) -> "Transaction":
        """Validate and construct from a dict (the pydantic classmethod shape)."""
        if not isinstance(value, dict):
            raise ValueError("Input should be a valid dictionary")
        return cls(**value)

    def model_dump(self) -> dict:
        """The pydantic-style dump of every field (plus any extras)."""
        return model_dump(self)
