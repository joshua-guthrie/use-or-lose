"""Configure and launch the shared renderer for travel compensatory time."""

from __future__ import annotations

from useorlose.charting import LeaveChartSpec, run_chart_app

SPEC = LeaveChartSpec(
    app_name="TravelCompChartForTheYear",
    title="Travel Comp",
    category="travel_comp",
    product="travel-comp-chart-png",
)


def main() -> None:
    run_chart_app(SPEC)
