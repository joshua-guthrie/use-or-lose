package com.westmouthbay.fedleave.calendar

import android.util.Log
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test

class SemanticsDumpTest : CalendarUiTestBase() {

    @Test
    fun diagnoseMenuDismissal() {
        setAppContent()
        waitForBalanceCard()
        composeRule.onNodeWithTag("calendar_menu_button").performClick()
        composeRule.onNodeWithText("Today").assertExists()
        Log.i("DIAG", "menu open")

        // Where is the weekday header in raw coordinates?
        val header = composeRule.onNodeWithTag("calendar_weekday_header").fetchSemanticsNode()
        Log.i("DIAG", "header boundsInRoot=${header.boundsInRoot}")

        val center = header.boundsInRoot.center
        tapScreenAt(center.x, center.y)
        Log.i("DIAG", "tapped at ${center.x}, ${center.y}")
        Thread.sleep(2000)
        composeRule.waitForIdle()
        val todayCount = composeRule.onAllNodesWithText("Today").fetchSemanticsNodes().size
        Log.i("DIAG", "after tap: Today nodes=$todayCount")
    }
}
