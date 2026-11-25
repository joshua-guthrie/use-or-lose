package com.westmouthbay.fedleave.calendar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.westmouthbay.fedleave.core.AppDependencies
import com.westmouthbay.fedleave.core.CoreBridge
import com.westmouthbay.fedleave.core.TimeSource
import com.westmouthbay.fedleave.storage.StoreCoordinator
import com.westmouthbay.fedleave.storage.StorePaths
import org.junit.Test

/**
 * First-use setup from an empty store: the welcome, the manual creation,
 * cancellation, and the Today balance choice with the T-YEAR-TODAY-01
 * arithmetic read back through the real bridge.
 */
class SetupFlowTest : CalendarUiTestBase() {

    override val importFixtureStore: Boolean = false

    private fun fillField(tag: String, text: String) {
        composeRule.onNodeWithTag(tag).performTextClearance()
        composeRule.onNodeWithTag(tag).performTextInput(text)
    }

    private fun waitForWelcome() {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("Welcome to FedLeave").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun firstUseWelcomeCreatesAManualYearAndOpensTheCalendar() {
        setAppContent()
        waitForWelcome()
        composeRule.onNodeWithTag("welcome_setup").performClick()
        composeRule.onNodeWithText("Set up leave year").assertIsDisplayed()
        // The welcome proposal defaults to today's year with no predecessor.
        composeRule.onNodeWithTag("setup_start_date").performTextInput("2026-01-11")
        composeRule.onNodeWithTag("setup_start_choice").performClick()
        composeRule.onNodeWithText("Start-of-year balances").assertIsDisplayed()
        fillField("setup_opening_annual", "100")
        fillField("setup_opening_sick", "20")
        composeRule.onNodeWithTag("setup_review_action").performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("Review 2026 leave year").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("setup_create").performClick()
        // The Calendar opens on the new year with the entered openings.
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("100 h").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("20 h").assertIsDisplayed()
    }

    @Test
    fun cancelOnTheMethodStepWritesNothingAndReturns() {
        setAppContent()
        waitForWelcome()
        composeRule.onNodeWithTag("welcome_setup").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Welcome to FedLeave").assertIsDisplayed()
        // The store is still empty: reopening setup works.
        composeRule.onNodeWithTag("welcome_setup").performClick()
        composeRule.onNodeWithText("Set up leave year").assertIsDisplayed()
    }

    @Test
    fun todayChoiceCreatesTheYearWithTodayTargets() {
        // Freeze the local day to February 10, 2026 for the Today flow.
        dependencies = AppDependencies(
            bridge = CoreBridge(com.chaquo.python.Python.getInstance()),
            store = StoreCoordinator(
                StorePaths.forContext(
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                        .targetContext,
                ),
            ),
            timeSource = TimeSource.fixed("2026-02-10"),
            displaySettings = CalendarDisplaySettings(),
        )
        setAppContent()
        waitForWelcome()
        composeRule.onNodeWithTag("welcome_setup").performClick()
        composeRule.onNodeWithTag("setup_start_date").performTextInput("2026-01-11")
        composeRule.onNodeWithTag("setup_today_choice").performClick()
        composeRule.onNodeWithText("Balances as of Today").assertIsDisplayed()
        fillField("setup_today_annual", "80")
        fillField("setup_today_sick", "24")
        fillField("setup_today_credit", "2.25")
        composeRule.onNodeWithTag("setup_review_action").performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("Review 2026 leave year").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("setup_create").performClick()
        // The Calendar card shows today's targets.
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("80 h").fetchSemanticsNodes().isNotEmpty()
        }
        // The next accrual posts: February 21 shows 86/28.
        composeRule.onNodeWithTag("calendar_balance_card").performClick()
        composeRule.onNodeWithText("Choose date").performClick()
        waitForPickerGrid("2026-02-01")
        composeRule.onNodeWithTag("balance_picker_day_2026-02-21").performScrollTo().performClick()
        composeRule.onNodeWithText("Show February 21").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("86 h").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("28 h").assertIsDisplayed()
    }
}

/**
 * The uncovered-date flow with the pinned fixture: January 10, 2027 lies
 * beyond the 2026 year, the unavailable panel offers setup, the adjacent
 * successor is proposed, and creating it resumes the balance lookup.
 */
class SetupResumeTest : CalendarUiTestBase() {

    @Test
    fun uncoveredDateOffersSetupAndResumesTheLookup() {
        setAppContent()
        waitForBalanceCard()
        composeRule.onNodeWithTag("calendar_balance_card").performClick()
        composeRule.onNodeWithText("Choose date").performClick()
        waitForPickerGrid()
        // Navigate the picker forward twelve months to January 2027.
        repeat(12) {
            composeRule.onNodeWithContentDescription("Next month").performClick()
        }
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText("January 2027").fetchSemanticsNodes().isNotEmpty()
        }
        // The month grid loads asynchronously after the last month change.
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodes(hasTestTag("balance_picker_day_2027-01-10"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("balance_picker_day_2027-01-10").performScrollTo().performClick()
        composeRule.onNodeWithText("Show January 10").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("Balance unavailable").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("balances_setup_action").performClick()
        // The adjacent successor is proposed: 2027 starting January 10.
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("Set up leave year").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("setup_start_choice").performClick()
        composeRule.onNodeWithText("Start-of-year balances").assertIsDisplayed()
        composeRule.onNodeWithTag("setup_review_action").performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("Review 2027 leave year").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("setup_create").performClick()
        // The Calendar resumes the balance lookup for January 10, 2027 on
        // the created year (card header shows the committed cutoff; the
        // values render once the resumed read lands).
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("Jan 10, 2027").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("Annual").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Annual").assertIsDisplayed()
    }
}
