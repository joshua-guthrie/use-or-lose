package com.westmouthbay.fedleave.calendar

import com.westmouthbay.fedleave.core.TimeSource

/**
 * Debug-build developer configuration for Calendar tests and fixtures:
 * the frozen local date and the pinned calendar fixture asset list. Device
 * tests import the store through
 * `com.westmouthbay.fedleave.storage.DeveloperFixture` and freeze the clock
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
}
