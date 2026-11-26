package com.westmouthbay.useorlose

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.westmouthbay.useorlose.storage.StorePaths
import org.junit.Rule
import org.junit.Test

class LaunchSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesAndShowsTheAppEntry() {
        // Other suites leave the store in arbitrary states; reset it so the
        // smoke test exercises a clean launch (first use shows the welcome).
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        StorePaths.forContext(context).deleteRecursively()

        // The app opens to either the Calendar or the first-use welcome.
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodes(hasTestTag("calendar_screen"))
                .fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodes(hasTestTag("welcome_setup"))
                    .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
