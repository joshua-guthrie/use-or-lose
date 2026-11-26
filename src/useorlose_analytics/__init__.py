"""Read-only seasonality, lifecycle, planning and historical usage analytics."""

from useorlose import __version__

from .analytics import analyze_leave_year

__all__ = ["__version__", "analyze_leave_year"]
