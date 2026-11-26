from __future__ import annotations

import tomllib
from pathlib import Path

import annual_leave_chart
import comp_time_chart
import credit_hours_chart
import sick_leave_chart
import time_off_award_chart
import travel_comp_chart
import useorlose
import useorlose_analytics
import useorlose_gui
import useorlose_month_report_graphic
import yearly_leave_comparison_chart
from useorlose.cli_app import HELP_TEXT
from useorlose.project_info import OFFICIAL_PROJECT_URL, PROJECT_AUTHOR

ROOT = Path(__file__).resolve().parents[1]


def test_cli_help_identifies_official_website_and_author() -> None:
    assert OFFICIAL_PROJECT_URL in HELP_TEXT
    assert PROJECT_AUTHOR in HELP_TEXT


def test_package_metadata_identifies_official_website_and_author_without_email() -> None:
    metadata = tomllib.loads((ROOT / "pyproject.toml").read_text(encoding="utf-8"))["project"]

    assert metadata["authors"] == [{"name": PROJECT_AUTHOR}]
    assert metadata["urls"]["Homepage"] == OFFICIAL_PROJECT_URL
    assert metadata["urls"]["Documentation"] == OFFICIAL_PROJECT_URL
    assert metadata["urls"]["Support"] == OFFICIAL_PROJECT_URL


def test_every_application_uses_the_authoritative_project_version() -> None:
    declared_version = tomllib.loads((ROOT / "pyproject.toml").read_text(encoding="utf-8"))["project"]["version"]
    packages = [
        useorlose,
        useorlose_gui,
        useorlose_analytics,
        annual_leave_chart,
        sick_leave_chart,
        credit_hours_chart,
        comp_time_chart,
        travel_comp_chart,
        time_off_award_chart,
        yearly_leave_comparison_chart,
        useorlose_month_report_graphic,
    ]

    assert declared_version == "1.1.0"
    assert useorlose.__base_version__ == declared_version
    assert useorlose.__build_commit__ == ""
    assert {package.__version__ for package in packages} == {declared_version}
