package com.westmouthbay.fedleave.calendar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Test

/**
 * The balance detail and date selection flows against the pinned fixture
 * and frozen clock: the card tap, the full list, the date picker with its
 * shortcuts, confirmation, cancellation, projections and the period detail.
 */
class BalanceScreenTest : CalendarUiTestBase() {

    private fun openBalances() {
        waitForBalanceCard()
        composeRule.onNodeWithTag("calendar_balance_card").performClick()
        composeRule.onNodeWithText("Balances & date").assertIsDisplayed()
    }

    @Test
    fun cardOpensTheFullListForToday() {
        setAppContent()
        openBalances()
        composeRule.onNodeWithText("As of today \u00b7 January 16, 2026").assertIsDisplayed()
        composeRule.onNodeWithText("2 balances").assertIsDisplayed()
        composeRule.onNodeWithText("Annual leave").assertIsDisplayed()
        composeRule.onNodeWithText("90 h").assertIsDisplayed()
        composeRule.onNodeWithText("Sick leave").assertIsDisplayed()
        composeRule.onNodeWithText("20 h").assertIsDisplayed()
        composeRule.onNodeWithText("Back to Calendar").performClick()
        composeRule.onNodeWithText("As of today \u00b7 Jan 16, 2026").assertIsDisplayed()
    }

    @Test
    fun pickerShowsShortcutsAndConfirmsAProjectedDateWithPeriodDetail() {
        setAppContent()
        openBalances()
        composeRule.onNodeWithText("Choose date").performClick()
        waitForPickerGrid()
        composeRule.onNodeWithText("Balance date").assertIsDisplayed()
        composeRule.onNodeWithText("Currently showing Jan 16, 2026").assertIsDisplayed()
        composeRule.onNodeWithText("Nearby pay-period ends").assertIsDisplayed()
        composeRule.onNodeWithText("Period 1").assertIsDisplayed()
        composeRule.onNodeWithText("Sat, Jan 24").assertIsDisplayed()
        composeRule.onNodeWithText("Period 2").assertIsDisplayed()
        composeRule.onNodeWithText("Sat, Feb 7").assertIsDisplayed()

        composeRule.onNodeWithTag("balance_picker_day_2026-01-24").performClick()
        composeRule.onNodeWithText("Show January 24").performClick()

        composeRule.onNodeWithText("Projected balance \u00b7 Saturday, Jan 24, 2026").assertIsDisplayed()
        composeRule.onNodeWithText("92 h").assertIsDisplayed()
        composeRule.onNodeWithText("24 h").assertIsDisplayed()

        // The cutoff is a period end: the collapsed card expands to the
        // period detail with the closing totals.
        composeRule.onNodeWithText("Pay period 1 \u00b7 Details closed").assertIsDisplayed()
        composeRule.onNodeWithTag("balance_period_card").performClick()
        composeRule.onNodeWithText("Jan 11\u201324 \u00b7 Pay date Jan 30.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("6 earned \u00b7 14 used").assertIsDisplayed()
        composeRule.onNodeWithText("4 earned \u00b7 0 used").assertIsDisplayed()
    }

    @Test
    fun cancelPreservesTheCommittedDate() {
        setAppContent()
        openBalances()
        composeRule.onNodeWithText("Choose date").performClick()
        waitForPickerGrid()
        composeRule.onNodeWithTag("balance_picker_day_2026-01-13").performClick()
        composeRule.onNodeWithText("Show January 13").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        // Back on Balances & date with the committed Today cutoff intact.
        composeRule.onNodeWithText("As of today \u00b7 January 16, 2026").assertIsDisplayed()
        composeRule.onNodeWithText("90 h").assertIsDisplayed()
    }

    @Test
    fun historicalDateShowsDatedResultWithoutProjection() {
        setAppContent()
        openBalances()
        composeRule.onNodeWithText("Choose date").performClick()
        waitForPickerGrid()
        composeRule.onNodeWithTag("balance_picker_day_2026-01-13").performClick()
        composeRule.onNodeWithText("Show January 13").performClick()
        composeRule.onNodeWithText("As of Tuesday, January 13, 2026").assertIsDisplayed()
        composeRule.onNodeWithText("92 h").assertIsDisplayed()
        composeRule.onNodeWithText("20 h").assertIsDisplayed()
        composeRule
            .onAllNodesWithText("Projected balance", substring = true)
            .fetchSemanticsNodes().isEmpty()
    }

    @Test
    fun projectedDateShowsTheExplanation() {
        setAppContent()
        openBalances()
        composeRule.onNodeWithText("Choose date").performClick()
        waitForPickerGrid()
        composeRule.onNodeWithTag("balance_picker_day_2026-01-22").performClick()
        composeRule.onNodeWithText("Show January 22").performClick()
        composeRule.onNodeWithText("Projected balance \u00b7 Thursday, Jan 22, 2026").assertIsDisplayed()
        composeRule.onNodeWithText("Includes dated leave and accruals through January 22.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("86 h").assertIsDisplayed()
    }

    @Test
    fun backFromBalancesReturnsToTheCalendarWithTheCutoff() {
        setAppContent()
        openBalances()
        composeRule.onNodeWithContentDescription("Back to Calendar").performClick()
        composeRule.onNodeWithText("As of today \u00b7 Jan 16, 2026").assertIsDisplayed()
    }
}
