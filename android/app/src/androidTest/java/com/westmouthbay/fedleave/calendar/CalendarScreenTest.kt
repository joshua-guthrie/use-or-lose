package com.westmouthbay.fedleave.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.westmouthbay.fedleave.ui.FedLeaveApp
import com.westmouthbay.fedleave.ui.theme.FedLeaveTheme
import org.junit.Test

/**
 * The Calendar main screen against the pinned fixture and frozen clock:
 * balance card, month row, grid markers and activity, the full-width day
 * list fallback, Menu > Today and the month picker.
 */
class CalendarScreenTest : CalendarUiTestBase() {

    @Test
    fun cardShowsTodayHeaderAndFixtureBalances() {
        setAppContent()
        waitForBalanceCard()
        composeRule.onNodeWithTag("calendar_screen").assertIsDisplayed()
        composeRule.onNodeWithText("As of today \u00b7 Jan 16, 2026").assertIsDisplayed()
        composeRule.onNodeWithText("Annual").assertIsDisplayed()
        composeRule.onNodeWithText("90 h").assertIsDisplayed()
        composeRule.onNodeWithText("Sick").assertIsDisplayed()
        composeRule.onNodeWithText("20 h").assertIsDisplayed()
    }

    @Test
    fun monthRowShowsJanuary2026() {
        setAppContent()
        waitForBalanceCard()
        composeRule.onNodeWithText("January 2026").assertIsDisplayed()
    }

    @Test
    fun gridShowsMarkersAndActivityLines() {
        setAppContent()
        waitForBalanceCard()
        composeRule.onNodeWithText("A \u22128").assertIsDisplayed()
        composeRule.onNodeWithText("A \u22122").assertIsDisplayed()
        composeRule.onNodeWithText("A \u22124").assertIsDisplayed()
        composeRule.onNodeWithText("A +6").assertIsDisplayed()
        composeRule.onNodeWithText("S +4").assertIsDisplayed()
        composeRule.onNodeWithText("A \u22123").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Thursday, January 1, 2026. Holiday, New Year's Day")
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Monday, January 19, 2026. Holiday, Martin Luther King Jr. Day")
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Saturday, January 24, 2026. Pay-period end, Period 1. 6 hours annual leave earned. 4 hours sick leave earned")
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Friday, January 30, 2026. Payday")
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Friday, January 16, 2026. 2 hours annual leave used")
            .assertIsDisplayed()
    }

    @Test
    fun tappingADayOpensTheReadOnlyDayRouteAndBackPreservesCutoff() {
        setAppContent()
        waitForBalanceCard()
        composeRule.onNodeWithTag("calendar_day_2026-01-16").performClick()
        composeRule.onNodeWithText("Edit day").assertIsDisplayed()
        composeRule.onNodeWithText("Friday, Jan 16, 2026").assertIsDisplayed()
        composeRule.onNodeWithText("Leave activity").assertIsDisplayed()
        composeRule.onNodeWithText("Annual leave").assertIsDisplayed()
        composeRule.onNodeWithText("2 h used").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back to Calendar").performClick()
        composeRule.onNodeWithText("As of today \u00b7 Jan 16, 2026").assertIsDisplayed()
        composeRule.onNodeWithText("90 h").assertIsDisplayed()
    }

    @Test
    fun emptyDayShowsNoEntries() {
        setAppContent()
        waitForBalanceCard()
        composeRule.onNodeWithTag("calendar_day_2026-01-21").performClick()
        composeRule.onNodeWithText("Edit day").assertIsDisplayed()
        composeRule.onNodeWithText("Wednesday, Jan 21, 2026").assertIsDisplayed()
        composeRule.onNodeWithText("No entries").assertIsDisplayed()
    }

    @Test
    fun monthPickerSelectsAMonthAndPreservesTheCutoff() {
        setAppContent()
        waitForBalanceCard()
        composeRule.onNodeWithTag("calendar_month_selector").performClick()
        composeRule.onNodeWithText("Choose month").assertIsDisplayed()
        composeRule.onNodeWithText("February").performClick()
        composeRule.onNodeWithText("February 2026").assertIsDisplayed()
        composeRule.onNodeWithText("90 h").assertIsDisplayed()
    }

    @Test
    fun menuDismissalPreservesStateAndTodayResets() {
        setAppContent()
        waitForBalanceCard()
        // Browse to February first.
        composeRule.onNodeWithTag("calendar_month_selector").performClick()
        composeRule.onNodeWithText("February").performClick()
        composeRule.onNodeWithText("February 2026").assertIsDisplayed()

        // Open the menu; only Today is delivered in I05.
        composeRule.onNodeWithTag("calendar_menu_button").performClick()
        composeRule.onNodeWithText("Today").assertIsDisplayed()

        // Dismiss with a real outside tap on the non-interactive weekday
        // header area: month and cutoff are preserved. The RAM-constrained
        // emulator may show ANR dialogs that swallow input; clear them first.
        dismissAnrDialogs()
        tapScreenAt(540f, 907f)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Today").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("February 2026").assertIsDisplayed()
        composeRule.onNodeWithText("90 h").assertIsDisplayed()

        // Dismiss with system Back as well, then use Today.
        composeRule.onNodeWithTag("calendar_menu_button").performClick()
        composeRule.onNodeWithText("Today").assertIsDisplayed()
        dismissAnrDialogs()
        pressBack()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Today").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("February 2026").assertIsDisplayed()

        // Today resets the month and the cutoff.
        composeRule.onNodeWithTag("calendar_menu_button").performClick()
        composeRule.onNodeWithText("Today").performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText("January 2026").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("90 h").assertIsDisplayed()
    }

    @Test
    fun bottomTabsOpenPlaceholdersAndReturnToCalendar() {
        setAppContent()
        waitForBalanceCard()
        composeRule.onNodeWithTag("tab_reports").performClick()
        composeRule.onNodeWithText("Reports \u00b7 Coming in a later build").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_settings").performClick()
        composeRule.onNodeWithText("Settings \u00b7 Coming in a later build").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_calendar").performClick()
        composeRule.onNodeWithText("As of today \u00b7 Jan 16, 2026").assertIsDisplayed()
    }

    @Test
    fun narrowWidthFallsBackToTheDayList() {
        composeRule.setContent {
            FedLeaveTheme {
                Box(Modifier.width(300.dp)) {
                    FedLeaveApp(dependencies)
                }
            }
        }
        waitForBalanceCard()
        composeRule.onNodeWithText("Thursday, January 1").assertIsDisplayed()
        // Each row spells out its schedule events: January 1 carries the
        // holiday, January 21 has no entries.
        composeRule.onNodeWithText("Holiday, New Year's Day \u203A").assertIsDisplayed()
        composeRule.onAllNodesWithText("No entries \u203A").onFirst().assertIsDisplayed()
        composeRule
            .onNodeWithTag("calendar_day_list")
            .performScrollToNode(hasTestTag("calendar_day_2026-01-24"))
        composeRule
            .onNodeWithText("Pay-period end, Period 1", substring = true)
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag("calendar_day_list")
            .performScrollToNode(hasTestTag("calendar_day_2026-01-22"))
        composeRule.onNodeWithTag("calendar_day_2026-01-22").performClick()
        composeRule.onNodeWithText("Edit day").assertIsDisplayed()
        composeRule.onNodeWithText("Thursday, Jan 22, 2026").assertIsDisplayed()
        composeRule.onNodeWithText("Annual leave").assertIsDisplayed()
        composeRule.onNodeWithText("4 h used").assertIsDisplayed()
    }
}
