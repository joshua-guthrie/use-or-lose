"""Present balances, pay-period activity, daily activity, and month views."""

from __future__ import annotations

from datetime import date as _date
from pathlib import Path

import typer
from typer.models import OptionInfo

from ..cli_app import _print_json, app, console
from ..cli_helpers import load_read_snapshot as load_leave_year
from ..cli_helpers import parse_iso_date, resolve_leave_year_for_date
from ..config import get_default_data_dir
from ..ledger import (
    calculate_balances,
    calculate_pay_period_activity,
    ensure_automatic_accruals,
)
from ..payperiods import normalize_pay_period
from ..read_api import (
    _is_leave_year_end_keyword,
    _month_bounds,
    _names_from_rows,
)
from ..read_api import (
    dated_balance as read_dated_balance,
)
from ..read_api import (
    day_activity as read_day_activity,
)
from ..read_api import (
    month_view as read_month_view,
)
from ..read_api import (
    pay_period as read_pay_period,
)
from ..storage import load_json


def _show_accounting_warnings(leave_year: dict, json_output: bool) -> None:
    if not json_output:
        for warning in getattr(leave_year, "warnings", []):
            console.print(f"{warning['code']}: {warning.get('message', '')}")


def _holiday_names_by_date(year: int, data_dir: Path | None) -> dict[str, str]:
    cache_file = get_default_data_dir(data_dir) / "holiday_cache" / f"federal_holidays_{year}.json"
    if not cache_file.exists():
        return {}
    try:
        cache = load_json(cache_file)
    except Exception:
        return {}
    return _names_from_rows(cache.get("holidays", []))


def _resolve_balance_leave_year(
    year: int | None,
    as_of: str | None,
    data_dir: Path | None,
) -> tuple[int, dict]:
    if year is not None:
        return year, load_leave_year(year, data_dir)

    target = "today" if as_of is None or _is_leave_year_end_keyword(as_of) else as_of
    year, _ = resolve_leave_year_for_date(target, data_dir)
    return year, load_leave_year(year, data_dir)


@app.command()
def balance(
    year: int | None = typer.Option(None, help="Leave year. Defaults to the leave year containing --as-of or today."),
    as_of: str | None = typer.Option(
        None, help="Compute balances through this date YYYY-MM-DD, today, or leave-year-end."
    ),
    project: bool = typer.Option(
        False, help="Deprecated compatibility flag; projection is enabled by --project-to or --use-or-lose."
    ),
    project_to: str | None = typer.Option(
        None,
        help="Projection end date YYYY-MM-DD, today, or leave-year-end. Defaults to leave year end when projection is enabled.",
    ),
    use_or_lose: bool = typer.Option(
        False, help="Show projected annual carryover and use-or-lose amounts at year end."
    ),
    json_output: bool = typer.Option(False, "--json", help="Emit machine-readable JSON."),
    data_dir: Path | None = typer.Option(None, help="Data directory override."),
) -> None:
    if isinstance(year, OptionInfo):
        year = None
    if isinstance(as_of, OptionInfo):
        as_of = None
    if not isinstance(project, bool):
        project = False
    if isinstance(project_to, OptionInfo):
        project_to = None
    if not isinstance(use_or_lose, bool):
        use_or_lose = False
    if not isinstance(json_output, bool):
        json_output = False
    if isinstance(data_dir, OptionInfo):
        data_dir = None

    try:
        year, leave_year = _resolve_balance_leave_year(year, as_of, data_dir)
        _show_accounting_warnings(leave_year, json_output)
        local_date = _date.today().isoformat()
        result = read_dated_balance(
            {},
            {
                "year": year,
                "as_of": as_of,
                "project": project,
                "project_to": project_to,
                "use_or_lose": use_or_lose,
            },
            local_date=local_date,
            snapshot=leave_year,
        )
    except FileNotFoundError as exc:
        console.print(f"[red]ERROR:[/red] {exc}")
        raise typer.Exit(code=1)
    except ValueError as exc:
        if getattr(exc, "code", None):
            from .rollover import report_error

            report_error(exc, json_output)
        console.print(f"[red]ERROR:[/red] {exc}")
        raise typer.Exit(code=2)

    as_of = result["as_of"]
    projection_label = result["project_to"]
    include_projected = result["projected"]
    balances = result["balances"]
    added_accruals = result["automatic_accruals_posted"]
    accrual_through = result["automatic_accruals_posted_through"]
    parental = result["parental"]
    use_or_lose_data = result["use_or_lose"]

    if json_output:
        _print_json(result)
        return

    if include_projected:
        console.print(f"Projected balances for {year} as of {projection_label}:")
    else:
        console.print(f"Balances for {year} as of {as_of}:")

    for category, amount in sorted(balances.items()):
        console.print(
            f"  {category}: {amount:.2f}" if amount is not None else f"  {category}: N/A (history requires review)"
        )
    if parental:
        for warning in parental["warnings"]:
            console.print(f"{warning['code']}: {warning['message']}")
        fmla = parental["fmla"]
        remaining = "N/A" if fmla["remaining_hours"] is None else f"{fmla['remaining_hours']:.2f} h"
        console.print(f"FMLA recorded usage: {fmla['used_hours']:.2f} h; remaining capacity: {remaining}")
        if fmla["period"]:
            period = fmla["period"]
            console.print(
                f"FMLA period: {period['start_date']} through {period['last_eligible_usage_date']}"
                + (" (provisional)" if period["provisional"] else "")
            )

    if added_accruals:
        console.print(
            f"Computed {added_accruals} automatic annual/sick accrual transactions in memory through {accrual_through}."
        )

    if use_or_lose:
        console.print("")
        console.print(f"Carryover limit: {use_or_lose_data['carryover_limit']:.2f}")
        console.print(f"Projected annual carryover: {use_or_lose_data['annual_carryover']:.2f}")
        console.print(f"Projected use-or-lose: {use_or_lose_data['use_or_lose']:.2f}")


@app.command(name="use-or-lose")
@app.command(name="use-or-loose")
def use_or_lose(
    year: int | None = typer.Option(None, help="Leave year. Defaults to the current leave year."),
    json_output: bool = typer.Option(False, "--json", help="Emit machine-readable JSON."),
    data_dir: Path | None = typer.Option(None, help="Data directory override."),
) -> None:
    balance(
        year=year,
        as_of="leave-year-end",
        use_or_lose=True,
        json_output=json_output,
        data_dir=data_dir,
    )


@app.command(name="pay-period")
def pay_period_summary(
    year: int = typer.Option(..., help="Leave year."),
    date: str = typer.Option(..., help="Date inside the pay period YYYY-MM-DD or today."),
    daily: bool = typer.Option(False, help="Show activity for each day in the pay period."),
    json_output: bool = typer.Option(False, "--json", help="Emit machine-readable JSON."),
    data_dir: Path | None = typer.Option(None, help="Data directory override."),
) -> None:
    if not isinstance(daily, bool):
        daily = False
    if not isinstance(json_output, bool):
        json_output = False
    if isinstance(data_dir, OptionInfo):
        data_dir = None

    try:
        leave_year = load_leave_year(year, data_dir)
        _show_accounting_warnings(leave_year, json_output)
    except FileNotFoundError as exc:
        console.print(f"[red]ERROR:[/red] {exc}")
        raise typer.Exit(code=1)

    try:
        date = parse_iso_date(date).isoformat()
        result = read_pay_period(
            {},
            {"year": year, "date": date, "daily": daily},
            local_date=_date.today().isoformat(),
            snapshot=leave_year,
        )
    except ValueError as exc:
        if getattr(exc, "code", None):
            from .rollover import report_error

            report_error(exc, json_output)
        console.print(f"[red]ERROR:[/red] {exc}")
        raise typer.Exit(code=2)

    pay_period = result["pay_period"]
    activity = result["activity"]
    ending_balances = result["ending_balances"]
    added_accruals = result["automatic_accruals_posted"]
    daily_activity_rows = result["daily_activity"] or []

    if json_output:
        _print_json(result)
        return

    console.print(
        f"Pay period {pay_period.get('pay_period_number')} "
        f"({pay_period.get('start_date')} to {pay_period.get('end_date')})"
    )
    if added_accruals:
        console.print(f"Posted {added_accruals} automatic annual/sick accrual transactions for this pay period.")

    if daily:
        console.print("")
        console.print("Daily activity:")
        for row in daily_activity_rows:
            day = row["date"]
            day_activity = {key: value for key, value in row.items() if key != "date"}
            day_categories = sorted({*day_activity["earned"], *day_activity["used"], *day_activity["net"]})
            if day_categories:
                console.print(f"  {day}:")
                for category in day_categories:
                    earned = day_activity["earned"].get(category, 0.0)
                    used = day_activity["used"].get(category, 0.0)
                    net = day_activity["net"].get(category, 0.0)
                    console.print(f"    {category}: earned={earned:.2f} used={used:.2f} net={net:.2f}")
            else:
                console.print(f"  {day}: no activity")

    categories = sorted({*activity["earned"], *activity["used"], *activity["worked"], *activity["net"]})
    if not categories:
        console.print("No leave or overtime activity recorded for this pay period.")
        return

    console.print("")
    console.print("Pay period totals:")
    for category in categories:
        earned = activity["earned"].get(category, 0.0)
        used = activity["used"].get(category, 0.0)
        worked = activity["worked"].get(category, 0.0)
        net = activity["net"].get(category, 0.0)
        if category == "overtime":
            console.print(f"  {category}: worked={worked:.2f} net={net:.2f}")
        else:
            console.print(f"  {category}: earned={earned:.2f} used={used:.2f} net={net:.2f}")

    console.print("")
    console.print(f"Balances at end of pay period {pay_period.get('pay_period_number')}:")
    for category, amount in sorted(ending_balances.items()):
        if amount:
            console.print(f"  {category}: {amount:.2f}")


@app.command(name="pay-periods")
def pay_periods_summary(
    year: int = typer.Option(..., help="Leave year."),
    json_output: bool = typer.Option(False, "--json", help="Emit machine-readable JSON."),
    data_dir: Path | None = typer.Option(None, help="Data directory override."),
) -> None:
    if not isinstance(json_output, bool):
        json_output = False
    if isinstance(data_dir, OptionInfo):
        data_dir = None

    try:
        leave_year = load_leave_year(year, data_dir)
        _show_accounting_warnings(leave_year, json_output)
    except FileNotFoundError as exc:
        console.print(f"[red]ERROR:[/red] {exc}")
        raise typer.Exit(code=1)

    pay_periods = leave_year.get("pay_periods", [])
    if not pay_periods:
        console.print(f"No pay periods found for {year}.")
        raise typer.Exit(code=1)

    final_accrual_date = pay_periods[-1].get("accrual_date") or pay_periods[-1].get("end_date")
    try:
        added_accruals = ensure_automatic_accruals(leave_year, final_accrual_date)
    except ValueError as exc:
        if getattr(exc, "code", None):
            from .rollover import report_error

            report_error(exc, json_output)
        console.print(f"[red]ERROR:[/red] {exc}")
        raise typer.Exit(code=2)

    summaries = []
    if not json_output:
        console.print(f"Pay period summary for {year}:")
        if added_accruals:
            console.print(
                f"Posted {added_accruals} automatic annual/sick accrual transactions through {final_accrual_date}."
            )

    for pay_period in pay_periods:
        activity = calculate_pay_period_activity(leave_year, pay_period["start_date"])
        activity_output = dict(activity)
        activity_output["pay_period"] = normalize_pay_period(activity["pay_period"])
        balances = calculate_balances(leave_year, until_date=pay_period["end_date"])
        summaries.append(
            {
                "pay_period": normalize_pay_period(pay_period),
                "activity": activity_output,
                "ending_balances": dict(sorted(balances.items())),
            }
        )
        if json_output:
            continue
        console.print(
            f"Pay period {pay_period.get('pay_period_number')} "
            f"({pay_period.get('start_date')} to {pay_period.get('end_date')})"
        )
        categories = sorted({*activity["earned"], *activity["used"], *activity["worked"], *activity["net"]})
        if categories:
            for category in categories:
                earned = activity["earned"].get(category, 0.0)
                used = activity["used"].get(category, 0.0)
                worked = activity["worked"].get(category, 0.0)
                net = activity["net"].get(category, 0.0)
                if category == "overtime":
                    console.print(f"  {category}: worked={worked:.2f} net={net:.2f}")
                else:
                    console.print(f"  {category}: earned={earned:.2f} used={used:.2f} net={net:.2f}")
        else:
            console.print("  no activity")
        nonzero_balances = {category: amount for category, amount in sorted(balances.items()) if amount}
        balance_text = ", ".join(f"{category}={amount:.2f}" for category, amount in nonzero_balances.items())
        console.print(f"  ending balances: {balance_text or 'none'}")

    if json_output:
        _print_json(
            {
                "warnings": getattr(leave_year, "warnings", []),
                "year": year,
                "pay_periods": summaries,
                "automatic_accruals_posted": added_accruals,
                "automatic_accruals_posted_through": final_accrual_date,
            }
        )
        return


@app.command()
def month(
    year: int = typer.Option(..., help="Leave year."),
    month: int = typer.Option(..., help="Calendar month number, 1-12."),
    json_output: bool = typer.Option(False, "--json", help="Emit machine-readable JSON."),
    data_dir: Path | None = typer.Option(None, help="Data directory override."),
    calendar_year: int | None = typer.Option(
        None, help="Display this calendar year when it differs from the ledger year."
    ),
) -> None:
    if not isinstance(json_output, bool):
        json_output = False
    if isinstance(data_dir, OptionInfo):
        data_dir = None

    display_year = calendar_year if isinstance(calendar_year, int) else year
    try:
        _month_bounds(display_year, month)
    except ValueError as exc:
        if getattr(exc, "code", None):
            from .rollover import report_error

            report_error(exc, json_output)
        console.print(f"[red]ERROR:[/red] {exc}")
        raise typer.Exit(code=2)

    try:
        leave_year = load_leave_year(year, data_dir)
        _show_accounting_warnings(leave_year, json_output)
    except FileNotFoundError as exc:
        console.print(f"[red]ERROR:[/red] {exc}")
        raise typer.Exit(code=1)

    holidays = _holiday_names_by_date(display_year, data_dir)
    today = _date.today()
    try:
        result = read_month_view(
            {},
            {"year": year, "month": month, "calendar_year": calendar_year},
            local_date=today.isoformat(),
            snapshot=leave_year,
            holidays=holidays,
        )
    except ValueError as exc:
        if getattr(exc, "code", None):
            from .rollover import report_error

            report_error(exc, json_output)
        console.print(f"[red]ERROR:[/red] {exc}")
        raise typer.Exit(code=2)

    days = result["days"]
    calendar_start = result["calendar_start"]
    calendar_end = result["calendar_end"]
    added_accruals = result["automatic_accruals_posted"]

    if json_output:
        _print_json(result)
        return

    console.print(f"Month view for {display_year}-{month:02d} ({calendar_start} to {calendar_end})")
    if added_accruals:
        console.print(f"Posted {added_accruals} automatic annual/sick accrual transactions through {calendar_end}.")
    for day in days:
        if (
            not day["in_display_month"]
            and not day["entries"]
            and not day["holiday_name"]
            and not day["is_payday"]
            and not day["is_pay_period_end"]
        ):
            continue
        details = list(day["display_lines"])
        if day["holiday_name"]:
            details.append(str(day["holiday_name"]))
        if day["is_payday"]:
            details.append("Pay day")
        if day["is_pay_period_end"]:
            details.append("Pay period end")
        if day["is_today"]:
            details.append("Today")
        console.print(f"  {day['date']}: {', '.join(details) if details else 'no activity'}")


@app.command(name="activity")
def daily_activity(
    year: int = typer.Option(..., help="Leave year."),
    date: str = typer.Option(..., help="Date to query YYYY-MM-DD or today."),
    json_output: bool = typer.Option(False, "--json", help="Emit machine-readable JSON."),
    data_dir: Path | None = typer.Option(None, help="Data directory override."),
) -> None:
    if not isinstance(json_output, bool):
        json_output = False
    if isinstance(data_dir, OptionInfo):
        data_dir = None

    try:
        leave_year = load_leave_year(year, data_dir)
        _show_accounting_warnings(leave_year, json_output)
    except FileNotFoundError as exc:
        console.print(f"[red]ERROR:[/red] {exc}")
        raise typer.Exit(code=1)

    try:
        date = parse_iso_date(date).isoformat()
        result = read_day_activity(
            {},
            {"year": year, "date": date},
            local_date=_date.today().isoformat(),
            snapshot=leave_year,
        )
    except ValueError as exc:
        if getattr(exc, "code", None):
            from .rollover import report_error

            report_error(exc, json_output)
        console.print(f"[red]ERROR:[/red] {exc}")
        raise typer.Exit(code=2)

    activity = result["activity"]
    if not result["has_activity"]:
        if json_output:
            _print_json(result)
            return
        console.print(f"No leave activity recorded on {date} for {year}.")
        raise typer.Exit(code=0)

    if json_output:
        _print_json(result)
        return

    console.print(f"Leave activity for {date} ({year}):")
    for category in sorted({*activity["earned"], *activity["used"], *activity["net"]}):
        earned = activity["earned"].get(category, 0.0)
        used = activity["used"].get(category, 0.0)
        net = activity["net"].get(category, 0.0)
        console.print(f"  {category}: earned={earned:.2f} used={used:.2f} net={net:.2f}")


@app.command(name="compare-leave-balances")
def compare_leave_balances(
    category: str = typer.Option(..., help="Leave category to compare across all available leave years."),
    as_of: str = typer.Option("today", "--as-of", help="Comparison date YYYY-MM-DD or today."),
    json_output: bool = typer.Option(False, "--json", help="Emit machine-readable JSON."),
    data_dir: Path | None = typer.Option(None, help="Data directory override."),
) -> None:
    if isinstance(category, OptionInfo):
        raise typer.Exit(code=2)
    if isinstance(as_of, OptionInfo):
        as_of = "today"
    if not isinstance(json_output, bool):
        json_output = False
    if isinstance(data_dir, OptionInfo):
        data_dir = None

    from ..charting import COMPARISON_CATEGORY_LABELS, comparison_chart_points

    if category not in COMPARISON_CATEGORY_LABELS:
        console.print(
            f"[red]ERROR:[/red] Invalid category: {category}. Valid categories: {', '.join(COMPARISON_CATEGORY_LABELS)}"
        )
        raise typer.Exit(code=2)

    try:
        points, max_value, resolved_as_of = comparison_chart_points(category, as_of, data_dir)
    except FileNotFoundError as exc:
        console.print(f"[red]ERROR:[/red] {exc}")
        raise typer.Exit(code=1)
    except ValueError as exc:
        if getattr(exc, "code", None):
            from .rollover import report_error

            report_error(exc, json_output)
        console.print(f"[red]ERROR:[/red] {exc}")
        raise typer.Exit(code=2)

    title = COMPARISON_CATEGORY_LABELS[category]
    y_rounding = 50 if category in {"annual", "sick"} else 10
    y_max = int((float(max_value) + (49 if y_rounding == 50 else 9)) // y_rounding * y_rounding) if max_value else 10

    if json_output:
        _print_json(
            {
                "ok": True,
                "category": category,
                "title": title,
                "as_of": resolved_as_of,
                "y_axis": {"min": 0, "max": y_max},
                "point_count": len(points),
                "max_value_hours": float(max_value),
                "years": [point["year"] for point in points],
                "points": points,
            }
        )
        return

    console.print(f"{title} as of {resolved_as_of}:")
    for point in points:
        display_value = f"{float(point['value']):.2f}".rstrip("0").rstrip(".")
        console.print(f"  {point['year']}\t{display_value}")
