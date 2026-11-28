package com.westmouthbay.useorlose.calendar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.westmouthbay.useorlose.storage.StoreCoordinator
import com.westmouthbay.useorlose.storage.StorePaths
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T-YEAR-CARRY-01 on the device: the pinned Carryover fixture previews the
 * 2027 successor through the real bridge (annual 240 h, sick 600 h,
 * credit 24 h, reductions annual 40 h and credit 6 h), the review publishes
 * once, and the source ledger stays byte-identical.
 */
class CarryoverFlowTest : CalendarUiTestBase() {

    override fun fixtureAssets(): List<String> = CalendarDeveloperConfig.carryoverFixtureAssets

    override fun fixtureBalanceText(): String = "280 h"

    override fun assetPrefix(): String = CalendarDeveloperConfig.CARRYOVER_FIXTURE_PREFIX

    private val sourceAsset: String
        get() = InstrumentationRegistry.getInstrumentation().context.assets
            .open("carryover-2026/leave_years/2026.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    @Test
    fun carryoverReviewCreatesTheSuccessorAndPreservesTheSource() {
        setAppContent()
        waitForBalanceCard()

        // Navigate the balance picker to January 10, 2027 (uncovered).
        composeRule.onNodeWithTag("calendar_balance_card").performClick()
        composeRule.onNodeWithText("Choose date").performClick()
        waitForPickerGrid()
        repeat(12) {
            composeRule.onNodeWithContentDescription("Next month").performClick()
        }
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText("January 2027").fetchSemanticsNodes().isNotEmpty()
        }
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
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("Set up leave year").fetchSemanticsNodes().isNotEmpty()
        }

        // The adjacent 2026 source offers the carryover choice.
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodes(hasTestTag("setup_carryover_choice")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("setup_carryover_choice").performScrollTo().performClick()

        // The review renders the pinned preview values with full leave-type
        // names: annual 240 h, sick 600 h, credit 24 h.
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("Annual leave starting balance").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("240 h").assertIsDisplayed()
        composeRule.onNodeWithText("600 h").assertIsDisplayed()
        composeRule.onNodeWithText("24 h").assertIsDisplayed()
        composeRule.onNodeWithText("exceed the configured limits.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Annual leave 40 h", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Credit hours 6 h", substring = true).assertIsDisplayed()

        // The lots detail expands and reports no expiring lots.
        composeRule.onNodeWithTag("carryover_lots").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("No expiring lots are carried.").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("No warnings.").assertIsDisplayed()

        composeRule.onNodeWithTag("carryover_create").performScrollTo().performClick()

        // The Calendar resumes the balance lookup for the created year.
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("Jan 10, 2027").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("Annual").fetchSemanticsNodes().isNotEmpty()
        }

        // The published store contains the successor and the source ledger
        // remains byte-identical to the pinned fixture asset.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val coordinator = StoreCoordinator(StorePaths.forContext(context))
        val files = runBlocking {
            val opened = coordinator.open()
            (opened as com.westmouthbay.useorlose.storage.OpenResult.Ready).snapshot.files
        }
        org.junit.Assert.assertTrue(files.containsKey("leave_years/2027.json"))
        assertEquals(sourceAsset, files["leave_years/2026.json"])
    }
}

/**
 * Evidence captures for the I07 carryover review, rendered from the pinned
 * carryover fixture, for the image-agent mockup comparison.
 */
class CarryoverVisualCaptureTest : CalendarUiTestBase() {

    override fun fixtureAssets(): List<String> = CalendarDeveloperConfig.carryoverFixtureAssets

    override fun assetPrefix(): String = CalendarDeveloperConfig.CARRYOVER_FIXTURE_PREFIX

    override fun fixtureBalanceText(): String = "280 h"

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test
    fun captureCarryoverReview() {
        setAppContent()
        waitForBalanceCard()
        composeRule.onNodeWithTag("calendar_balance_card").performClick()
        composeRule.onNodeWithText("Choose date").performClick()
        waitForPickerGrid()
        repeat(12) {
            composeRule.onNodeWithContentDescription("Next month").performClick()
        }
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
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodes(hasTestTag("setup_carryover_choice")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("setup_carryover_choice").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("Annual leave starting balance").fetchSemanticsNodes().isNotEmpty()
        }
        capture("carryover-review")
        composeRule.onNodeWithTag("carryover_lots").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("No expiring lots are carried.").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.mainClock.advanceTimeBy(1_500)
        capture("carryover-review-lots")
        composeRule.onNodeWithTag("carryover_back").performScrollTo()
        composeRule.mainClock.advanceTimeBy(500)
        capture("carryover-review-lots-actions")
    }
}
