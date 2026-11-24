package com.westmouthbay.fedleave

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class LaunchSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesAndShowsTheCalendarScreen() {
        composeRule.onNodeWithTag("calendar_screen").assertIsDisplayed()
    }
}
