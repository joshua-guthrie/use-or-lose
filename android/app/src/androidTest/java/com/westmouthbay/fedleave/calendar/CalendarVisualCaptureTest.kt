package com.westmouthbay.fedleave.calendar

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.westmouthbay.fedleave.ui.FedLeaveApp
import com.westmouthbay.fedleave.ui.theme.FedLeaveTheme
import org.junit.Test

/**
 * Evidence captures for the I05-02 visual loop. Each state renders from the
 * pinned fixture and frozen clock, then saves a PNG under the app files dir
 * (`qa/I05-02/`) for `adb pull` and the image-agent mockup comparison.
 */
class CalendarVisualCaptureTest : CalendarUiTestBase() {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun captureCalendarMainLight() {
        setAppContent()
        waitForBalanceCard()
        composeRule.onNodeWithText("A \u22128").assertExists()
        capture("calendar-main")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun captureCalendarMenuOpen() {
        setAppContent()
        waitForBalanceCard()
        composeRule.onNodeWithTag("calendar_menu_button").performClick()
        composeRule.onNodeWithText("Today").assertExists()
        capture("calendar-menu")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun captureCalendarMonthPicker() {
        setAppContent()
        waitForBalanceCard()
        composeRule.onNodeWithTag("calendar_month_selector").performClick()
        composeRule.onNodeWithText("Choose month").assertExists()
        capture("calendar-month-picker")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun captureCalendarDayRoute() {
        setAppContent()
        waitForBalanceCard()
        composeRule.onNodeWithTag("calendar_day_2026-01-16").performClick()
        composeRule.onNodeWithText("Edit day").assertIsDisplayed()
        capture("calendar-day-2026-01-16")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun captureCalendarMainDark() {
        composeRule.setContent {
            FedLeaveTheme(darkTheme = true) {
                FedLeaveApp(dependencies)
            }
        }
        waitForBalanceCard()
        capture("calendar-main-dark")
    }

    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun captureCalendarMainLargeText() {
        composeRule.setContent {
            val current = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(current.density, fontScale = 2f),
            ) {
                FedLeaveTheme {
                    FedLeaveApp(dependencies)
                }
            }
        }
        waitForBalanceCard()
        capture("calendar-main-large-text")
    }
}

/**
 * Marker-fixture captures: the January 2027 collision grid and the day route
 * that names all four events.
 */
class CalendarMarkersCaptureTest : CalendarUiTestBase() {

    override fun fixtureAssets(): List<String> = CalendarDeveloperConfig.markerFixtureAssets

    override fun assetPrefix(): String = CalendarDeveloperConfig.MARKER_FIXTURE_PREFIX

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun captureJanuary2027Collision() {
        setAppContent()
        waitForBalanceCard()
        composeRule.onNodeWithTag("calendar_month_selector").performClick()
        composeRule.onNodeWithText("Calendar year").performClick()
        composeRule.onNodeWithText("2027").performClick()
        composeRule.onNodeWithText("January").performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText("January 2027").fetchSemanticsNodes().isNotEmpty()
        }
        capture("calendar-markers-jan-2027")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun captureJanuary9DayRoute() {
        setAppContent()
        waitForBalanceCard()
        composeRule.onNodeWithTag("calendar_month_selector").performClick()
        composeRule.onNodeWithText("Calendar year").performClick()
        composeRule.onNodeWithText("2027").performClick()
        composeRule.onNodeWithText("January").performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText("January 2027").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("calendar_day_2027-01-09").performClick()
        composeRule.onNodeWithText("Saturday, Jan 9, 2027").assertIsDisplayed()
        capture("calendar-markers-day-2027-01-09")
    }
}
