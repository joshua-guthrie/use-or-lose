package com.westmouthbay.useorlose.calendar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test

/**
 * Marker semantics with the pinned fixtures: the four glyphs in the January
 * 2026 grid, the January 9, 2027 collision on the dedicated marker fixture
 * (synthetic holiday, stored zero payday offset), the day-route event rows,
 * and the display-flag injection.
 */
class CalendarMarkersTest : CalendarUiTestBase() {

    override fun fixtureAssets(): List<String> = CalendarDeveloperConfig.markerFixtureAssets

    override fun assetPrefix(): String = CalendarDeveloperConfig.MARKER_FIXTURE_PREFIX

    private fun openJanuary2027() {
        composeRule.onNodeWithTag("calendar_month_selector").performClick()
        composeRule.onNodeWithText("Calendar year").performClick()
        composeRule.onNodeWithText("2027").performClick()
        composeRule.onNodeWithText("January").performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText("January 2027").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun january2026GridShowsTheMarkerFixtureSchedule() {
        setAppContent()
        waitForBalanceCard()
        // The marker fixture bakes the zero payday offset into the stored
        // pay dates: January 24, 2026 is both payday and pay-period end.
        composeRule
            .onNodeWithContentDescription("Thursday, January 1, 2026. Holiday, New Year's Day")
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Monday, January 19, 2026. Holiday, Martin Luther King Jr. Day")
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(
                "Saturday, January 24, 2026. Payday. Pay-period end, Period 1. " +
                    "6 hours annual leave earned. 4 hours sick leave earned",
            )
            .assertIsDisplayed()
    }

    @Test
    fun january9_2027CarriesAllFourEventsTogether() {
        setAppContent()
        waitForBalanceCard()
        openJanuary2027()
        composeRule
            .onNodeWithContentDescription(
                "Saturday, January 9, 2027. Holiday, Overlap Test Day. Payday. " +
                    "Pay-period end, Period 26. Leave-year end, Leave year 2026. " +
                    "6 hours annual leave earned. 4 hours sick leave earned",
            )
            .assertIsDisplayed()
    }

    @Test
    fun dayRouteNamesTheJanuary9EventsAndLeavesTheCutoffUntouched() {
        setAppContent()
        waitForBalanceCard()
        openJanuary2027()
        composeRule.onNodeWithTag("calendar_day_2027-01-09").performClick()
        composeRule.onNodeWithText("Edit day").assertIsDisplayed()
        composeRule.onNodeWithText("Saturday, Jan 9, 2027").assertIsDisplayed()
        composeRule.onNodeWithText("Holiday").assertIsDisplayed()
        composeRule.onNodeWithText("Overlap Test Day").assertIsDisplayed()
        composeRule.onNodeWithText("Payday").assertIsDisplayed()
        composeRule.onNodeWithText("Pay-period end").assertIsDisplayed()
        composeRule.onNodeWithText("Period 26").assertIsDisplayed()
        composeRule.onNodeWithText("Leave-year end").assertIsDisplayed()
        composeRule.onNodeWithText("Leave year 2026").assertIsDisplayed()
        composeRule.onNodeWithText("Annual leave").assertIsDisplayed()
        composeRule.onNodeWithText("6 h earned").assertIsDisplayed()
        composeRule.onNodeWithText("Sick leave").assertIsDisplayed()
        composeRule.onNodeWithText("4 h earned").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back to Calendar").performClick()
        composeRule.onNodeWithText("January 2027").assertIsDisplayed()
        composeRule.onNodeWithText("90 h").assertIsDisplayed()
    }
}

/**
 * Display-flag injection: with holidays hidden, the main Calendar speaks no
 * holiday but keeps the pay-period end; with period ends hidden in the
 * Calendar, the balance picker still identifies them.
 */
class CalendarMarkerFlagsTest : CalendarUiTestBase() {

    override fun displaySettings(): CalendarDisplaySettings =
        CalendarDisplaySettings(showHolidays = false)

    @Test
    fun hiddenHolidayFlagRemovesTheHolidayDescriptionButKeepsPeriodEnds() {
        setAppContent()
        waitForBalanceCard()
        composeRule
            .onNodeWithContentDescription("Thursday, January 1, 2026")
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Saturday, January 24, 2026. Pay-period end, Period 1. 6 hours annual leave earned. 4 hours sick leave earned")
            .assertIsDisplayed()
    }
}

/**
 * The picker always identifies pay-period ends, even when the Calendar
 * hides them (T-CAL-MARK-01).
 */
class CalendarPickerFlagsTest : CalendarUiTestBase() {

    override fun displaySettings(): CalendarDisplaySettings =
        CalendarDisplaySettings(showPayPeriodEnds = false)

    @Test
    fun pickerStillMarksPeriodEndsWhenTheCalendarHidesThem() {
        setAppContent()
        waitForBalanceCard()
        // The main Calendar hides the period-end marker on Jan 24.
        composeRule
            .onNodeWithContentDescription(
                "Saturday, January 24, 2026. 6 hours annual leave earned. 4 hours sick leave earned",
            )
            .assertIsDisplayed()
        // The balance picker still announces it.
        composeRule.onNodeWithTag("calendar_balance_card").performClick()
        composeRule.onNodeWithText("Choose date").performClick()
        waitForPickerGrid()
        composeRule
            .onNodeWithContentDescription(
                "Saturday, January 24, 2026. Pay-period end, Period 1",
            )
            .assertIsDisplayed()
    }
}
