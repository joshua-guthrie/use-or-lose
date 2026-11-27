package com.westmouthbay.useorlose.calendar

import com.westmouthbay.useorlose.core.TimeSource

/**
 * Debug-build developer configuration for Calendar tests and fixtures:
 * the frozen local date and the pinned calendar fixture asset list. Device
 * tests import the store through
 * `com.westmouthbay.useorlose.storage.DeveloperFixture` and freeze the clock
 * with [fixtureTimeSource].
 */
object CalendarDeveloperConfig {

    const val FIXTURE_LOCAL_DATE = "2026-01-16"

    val fixtureTimeSource: TimeSource = TimeSource.fixed(FIXTURE_LOCAL_DATE)

    /** Asset names of the pinned Calendar 2026 fixture (androidTest asset root). */
    val calendarFixtureAssets: List<String> = listOf(
        "config.json",
        "leave_years/2026.json",
        "holiday_cache/federal_holidays_2026.json",
    )

    /**
     * Asset names of the pinned Carryover source fixture (asset root
     * `qa/fixtures/carryover-fixture`, prefix `carryover-2026/`).
     */
    val carryoverFixtureAssets: List<String> = listOf(
        "carryover-2026/config.json",
        "carryover-2026/leave_years/2026.json",
        "carryover-2026/holiday_cache/federal_holidays_2026.json",
    )

    const val CARRYOVER_FIXTURE_PREFIX = "carryover-2026/"

    /**
     * Asset names of the pinned marker overlap fixture (asset root
     * `qa/fixtures/marker-fixture`, prefix `calendar-markers/`).
     */
    val markerFixtureAssets: List<String> = listOf(
        "calendar-markers/config.json",
        "calendar-markers/leave_years/2026.json",
        "calendar-markers/holiday_cache/federal_holidays_2026.json",
        "calendar-markers/holiday_cache/federal_holidays_2027.json",
    )

    const val MARKER_FIXTURE_PREFIX = "calendar-markers/"

    const val MARKER_FIXTURE_DIGEST = "d60bd8d05b849fcc305b3801ce9af183e0dbfbdc1b9e7d5c1419b78d08d57cbb"
}
