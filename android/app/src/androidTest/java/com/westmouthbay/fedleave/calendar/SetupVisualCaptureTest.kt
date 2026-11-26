package com.westmouthbay.fedleave.calendar

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.westmouthbay.fedleave.core.AppDependencies
import com.westmouthbay.fedleave.core.CoreBridge
import com.westmouthbay.fedleave.core.TimeSource
import com.westmouthbay.fedleave.storage.StoreCoordinator
import com.westmouthbay.fedleave.storage.StorePaths
import org.junit.Test

/**
 * Evidence captures for the I06 setup screens, rendered from an empty store
 * with the frozen clock, for the image-agent mockup comparison.
 */
class SetupVisualCaptureTest : CalendarUiTestBase() {

    override val importFixtureStore: Boolean = false

    private fun waitForWelcome() {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("Welcome to Use or Lose").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun captureWelcome() {
        setAppContent()
        waitForWelcome()
        capture("setup-welcome")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun captureSetupMethod() {
        setAppContent()
        waitForWelcome()
        composeRule.onNodeWithTag("welcome_setup").performClick()
        composeRule.onNodeWithTag("setup_start_date").performTextInput("2026-01-11")
        capture("setup-method")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun captureSetupStartForm() {
        setAppContent()
        waitForWelcome()
        composeRule.onNodeWithTag("welcome_setup").performClick()
        composeRule.onNodeWithTag("setup_start_date").performTextInput("2026-01-11")
        composeRule.onNodeWithTag("setup_start_choice").performClick()
        composeRule.onNodeWithTag("setup_opening_annual").performTextClearance()
        composeRule.onNodeWithTag("setup_opening_annual").performTextInput("100")
        composeRule.onNodeWithTag("setup_opening_sick").performTextClearance()
        composeRule.onNodeWithTag("setup_opening_sick").performTextInput("20")
        composeRule.onNodeWithTag("setup_opening_sick").performImeAction()
        capture("setup-start-form")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun captureSetupTodayAndReview() {
        dependencies = AppDependencies(
            bridge = CoreBridge(com.chaquo.python.Python.getInstance()),
            store = StoreCoordinator(
                StorePaths.forContext(
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext,
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
        composeRule.onNodeWithTag("setup_today_annual").performTextClearance()
        composeRule.onNodeWithTag("setup_today_annual").performTextInput("80")
        composeRule.onNodeWithTag("setup_today_sick").performTextClearance()
        composeRule.onNodeWithTag("setup_today_sick").performTextInput("24")
        composeRule.onNodeWithTag("setup_today_credit").performTextClearance()
        composeRule.onNodeWithTag("setup_today_credit").performTextInput("2.25")
        composeRule.onNodeWithTag("setup_today_credit").performImeAction()
        capture("setup-today-form")

        composeRule.onNodeWithTag("setup_review_action").performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("Review 2026 leave year").fetchSemanticsNodes().isNotEmpty()
        }
        capture("setup-today-review")
    }
}
