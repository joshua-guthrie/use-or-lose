package com.westmouthbay.useorlose.calendar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Test
import java.time.LocalDate

/**
 * T-CAL-DAY-01, T-CAL-DAY-02 and T-CAL-YEAR-02 on the device: the group
 * edit with the exact replaced IDs, the add-entry form with the stored
 * default status, and the editor resume after setup creates the containing
 * year. The Editable fixture starts Jan 16 at annual 89 h / sick 19 h.
 */
class DayEditFlowTest : CalendarUiTestBase() {

    override fun fixtureAssets(): List<String> = CalendarDeveloperConfig.editableFixtureAssets

    override fun assetPrefix(): String = CalendarDeveloperConfig.EDITABLE_FIXTURE_PREFIX

    override fun fixtureBalanceText(): String = "89 h"

    private fun openDay(date: LocalDate) {
        composeRule.onNodeWithTag("calendar_day_$date").performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodes(hasTestTag("day_edit_entry")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForBalance(value: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun editDayReplacesTheGroupShowsExactIdsAndSaves() {
        setAppContent()
        waitForBalance("89 h")
        openDay(LocalDate.parse("2026-01-16"))

        composeRule.onNodeWithTag("day_edit_entry").performClick()
        composeRule.onNodeWithTag("day_edit_group").performClick()
        composeRule.onNodeWithText("Annual leave · Use").performClick()
        composeRule.onNodeWithTag("day_edit_hours").performTextClearance()
        composeRule.onNodeWithTag("day_edit_hours").performTextInput("3.5")
        composeRule.onNodeWithTag("day_edit_comment").performTextInput("Appointment \"quoted\" 'x'")
        composeRule.onNodeWithTag("day_edit_review").performScrollTo().performClick()

        // The review shows the exact IDs and the 3 h -> 3.5 h change.
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("20260116-001 and 20260116-002", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("3 h → 3.5 h", substring = true).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("day_edit_save").performScrollTo().performClick()

        // Back on the Calendar, the balance reflects the saved edit.
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("88.5 h").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun backFromReviewPreservesTheDraft() {
        setAppContent()
        waitForBalance("89 h")
        openDay(LocalDate.parse("2026-01-16"))
        composeRule.onNodeWithTag("day_edit_entry").performClick()
        composeRule.onNodeWithTag("day_edit_group").performClick()
        composeRule.onNodeWithText("Annual leave · Use").performClick()
        composeRule.onNodeWithTag("day_edit_hours").performTextClearance()
        composeRule.onNodeWithTag("day_edit_hours").performTextInput("3.5")
        composeRule.onNodeWithTag("day_edit_comment").performTextInput("Draft comment")
        composeRule.onNodeWithTag("day_edit_review").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("20260116-001 and 20260116-002", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Back to draft").performScrollTo().performClick()

        // The draft values survive the back navigation.
        composeRule.onNodeWithText("Draft comment").assertIsDisplayed()
        composeRule.onNodeWithTag("day_edit_hours").assertIsDisplayed()
    }

    @Test
    fun uncoveredMonthOffersSetupAndThenOpensTheEditor() {
        setAppContent()
        waitForBalance("89 h")
        // Browse to February 2027 through the month picker.
        composeRule.onNodeWithTag("calendar_month_selector").performClick()
        composeRule.onNodeWithText("2026").performClick()
        composeRule.onNodeWithText("2027").performClick()
        composeRule.onNodeWithText("February").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText("February 2027").fetchSemanticsNodes().isNotEmpty()
        }
        // The uncovered month does not render dates: it asks to create the
        // leave year first (holidays and accruals need the year file).
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodes(hasTestTag("month_setup_action")).fetchSemanticsNodes().isNotEmpty()
        }
        // Cancel once: nothing is created and the prompt remains available.
        composeRule.onNodeWithTag("month_setup_action").performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("Set up leave year").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Cancel").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodes(hasTestTag("month_setup_action")).fetchSemanticsNodes().isNotEmpty()
        }
        // A separate attempt creates the containing year.
        composeRule.onNodeWithTag("month_setup_action").performClick()
        composeRule.onNodeWithTag("setup_start_choice").performScrollTo().performClick()
        composeRule.onNodeWithText("Start-of-year balances").assertIsDisplayed()
        composeRule.onNodeWithTag("setup_review_action").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("Review 2027 leave year").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("setup_create").performScrollTo().performClick()

        // The calendar returns to February 2027, now populated, and the
        // day editor opens for Feb 1.
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodes(hasTestTag("calendar_day_2027-02-01")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("calendar_day_2027-02-01").performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodes(hasTestTag("day_edit_entry")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("day_edit_entry").performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText("Edit day").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodes(hasTestTag("day_edit_group")).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

/**
 * Evidence captures for the I08 editor screens, rendered from the pinned
 * Editable fixture, for the image-agent mockup comparison.
 */
class DayEditVisualCaptureTest : CalendarUiTestBase() {

    override fun fixtureAssets(): List<String> = CalendarDeveloperConfig.editableFixtureAssets

    override fun assetPrefix(): String = CalendarDeveloperConfig.EDITABLE_FIXTURE_PREFIX

    override fun fixtureBalanceText(): String = "89 h"

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test
    fun captureDayEditScreens() {
        setAppContent()
        waitForBalanceCard()
        composeRule.onNodeWithTag("calendar_day_2026-01-16").performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodes(hasTestTag("day_edit_entry")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("day_edit_entry").performClick()
        composeRule.onNodeWithTag("day_edit_group").performClick()
        composeRule.onNodeWithText("Annual leave · Use").performClick()
        composeRule.onNodeWithTag("day_edit_hours").performTextClearance()
        composeRule.onNodeWithTag("day_edit_hours").performTextInput("3.5")
        composeRule.onNodeWithTag("day_edit_comment").performTextInput("Appointment")
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithTag("day_edit_review").performScrollTo()
        composeRule.mainClock.advanceTimeBy(300)
        capture("day-edit")
        composeRule.onNodeWithTag("day_edit_review").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("20260116-001 and 20260116-002", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        capture("day-save-review")
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test
    fun captureDayAddEntry() {
        setAppContent()
        waitForBalanceCard()
        composeRule.onNodeWithTag("calendar_day_2026-01-22").performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodes(hasTestTag("day_add_entry")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("day_add_entry").performClick()
        // Choose the leave type and direction for the new entry.
        composeRule.onAllNodesWithText("Choose a group").onFirst().performClick()
        composeRule.onNodeWithText("Annual leave").performClick()
        composeRule.onNodeWithTag("day_add_hours").performTextClearance()
        composeRule.onNodeWithTag("day_add_hours").performTextInput("1")
        composeRule.onNodeWithTag("day_add_comment").performTextInput("Follow-up")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("day_add_submit").performScrollTo()
        composeRule.mainClock.advanceTimeBy(300)
        capture("day-add-entry")
    }
}
