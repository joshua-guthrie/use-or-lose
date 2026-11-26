"""Expose the Use or Lose version and embedded source-build identity."""

from __future__ import annotations

import os

__base_version__ = "1.1.0"
__version__ = os.environ.get("USE_OR_LOSE_BUILD_VERSION", "").strip() or __base_version__
__build_commit__ = os.environ.get("USE_OR_LOSE_SOURCE_COMMIT", "").strip().lower()

__all__ = ["__base_version__", "__build_commit__", "__version__"]
