"""Pure standard-library model helpers replacing pydantic's small surface.

This module provides the two behaviors the project's models used pydantic
for: recursive ``model_dump()`` serialization and construction-time
validation with explicitly-provided-field tracking. It depends only on the
standard library, so the shared accounting core stays importable on
Linux, Windows and the Android runtime.
"""

from __future__ import annotations

from dataclasses import MISSING, Field, fields as dataclass_fields, is_dataclass
from datetime import datetime
from pathlib import Path
from typing import Any


class FieldValidationError(ValueError):
    """A single-field validation failure carrying the field name.

    The CLI formats these as ``"<field>: <message>"`` exactly as the
    previous pydantic error reformatting did.
    """

    def __init__(self, field: str, message: str):
        super().__init__(message)
        self.field = field
        self.message = message


def model_dump(value: Any) -> Any:
    """Recursive pydantic-style dump: models become dicts, Path becomes str.

    Tuples stay tuples (pydantic's ``mode="python"`` behavior); JSON
    serialization converts them afterwards exactly as before.
    """
    if isinstance(value, PureModel):
        declared = value.__dataclass_fields__
        result: dict[str, Any] = {
            name: model_dump(getattr(value, name)) for name in declared
        }
        for name, item in vars(value).items():
            if name not in declared and name != "fields_set":
                result[name] = model_dump(item)
        return result
    if is_dataclass(value) and not isinstance(value, type):
        return {
            field.name: model_dump(getattr(value, field.name))
            for field in dataclass_fields(value)
        }
    if isinstance(value, Path):
        return str(value)
    if isinstance(value, datetime):
        return value.isoformat()
    if isinstance(value, dict):
        return {key: model_dump(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [model_dump(item) for item in value]
    return value


class PureModel:
    """Base class for pure models.

    Subclasses are declared with ``@dataclass(init=False)`` so this base
    owns construction. It records the explicitly supplied fields (for
    ``extra="forbid"`` and ``fields_set`` semantics), applies defaults, and
    then runs the subclass ``__post_init__`` validators. ``__extra__`` is
    ``"allow"`` (unknown keyword arguments are kept as attributes and
    included in ``model_dump()``) or ``"forbid"`` (unknown keywords raise).
    """

    __extra__: str = "allow"

    def __init__(self, **kwargs: Any) -> None:
        declared: dict[str, Field] = self.__dataclass_fields__  # type: ignore[attr-defined]
        provided = set(kwargs)
        if self.__extra__ == "forbid":
            unknown = provided - set(declared)
            if unknown:
                raise ValueError(f"Unexpected fields: {', '.join(sorted(unknown))}")
        for name, field in declared.items():
            if name in kwargs:
                object.__setattr__(self, name, kwargs[name])
                continue
            if field.default is not MISSING:
                object.__setattr__(self, name, field.default)
                continue
            if field.default_factory is not MISSING:  # type: ignore[attr-defined]
                object.__setattr__(self, name, field.default_factory())  # type: ignore[misc]
                continue
            raise TypeError(f"__init__() missing 1 required positional argument: '{name}'")
        if self.__extra__ == "allow":
            for name in sorted(provided - set(declared)):
                object.__setattr__(self, name, kwargs[name])
        object.__setattr__(self, "fields_set", provided & set(declared))
        if hasattr(self, "__post_init__"):
            self.__post_init__()  # type: ignore[attr-defined]
