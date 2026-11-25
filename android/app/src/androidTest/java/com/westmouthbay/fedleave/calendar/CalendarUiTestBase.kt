package com.westmouthbay.fedleave.calendar

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.westmouthbay.fedleave.core.AppDependencies
import com.westmouthbay.fedleave.core.CoreBridge
import com.westmouthbay.fedleave.storage.DeveloperFixture
import com.westmouthbay.fedleave.storage.StoreCoordinator
import com.westmouthbay.fedleave.storage.StorePaths
import com.westmouthbay.fedleave.ui.FedLeaveApp
import com.westmouthbay.fedleave.ui.theme.FedLeaveTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import java.io.File
import java.io.FileOutputStream

/**
 * Shared harness for the I05-02 Calendar UI suites: a fresh fixture store,
 * the frozen clock, Python started on the main thread, and the app content.
 * The store directory is reset before every test so suites stay
 * order-independent.
 */
abstract class CalendarUiTestBase {

    @get:Rule
    val composeRule = createComposeRule()

    protected lateinit var dependencies: AppDependencies

    protected open fun fixtureAssets(): List<String> = CalendarDeveloperConfig.calendarFixtureAssets

    protected open fun assetPrefix(): String = ""

    protected open fun displaySettings(): CalendarDisplaySettings = CalendarDisplaySettings()

    /** Subclasses can inject a fake bridge for layout and failure states. */
    protected open fun createDependencies(context: android.content.Context): AppDependencies =
        AppDependencies(
            bridge = CoreBridge(Python.getInstance()),
            store = StoreCoordinator(StorePaths.forContext(context)),
            timeSource = CalendarDeveloperConfig.fixtureTimeSource,
            displaySettings = displaySettings(),
        )

    @Before
    open fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }
        }
        StorePaths.forContext(context).deleteRecursively()
        val coordinator = StoreCoordinator(StorePaths.forContext(context))
        runBlocking {
            DeveloperFixture.importNamedAssets(
                InstrumentationRegistry.getInstrumentation().context,
                coordinator,
                fixtureAssets(),
                assetPrefix(),
            )
        }
        dependencies = createDependencies(context)
    }

    protected fun setAppContent() {
        composeRule.setContent {
            FedLeaveTheme {
                FedLeaveApp(dependencies)
            }
        }
    }

    /** Wait until the Calendar balance card has loaded the fixture values. */
    protected fun waitForBalanceCard() {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText("90 h").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Wait until the balance date picker's month grid has rendered. */
    protected fun waitForPickerGrid() {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodes(
                hasTestTag("balance_picker_day_2026-01-01"),
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Save the full window (all windows, including open popups) as an
     * evidence PNG under the app's files dir; requires
     * `android.injected.androidTest.leaveApksInstalledAfterRun=true` to pull
     * afterward with adb.
     */
    protected fun capture(name: String) {
        dismissAnrDialogs()
        val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        } else {
            null
        }
        checkNotNull(bitmap) { "UiAutomation screenshot unavailable" }
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "qa/I05-02",
        )
        directory.mkdirs()
        FileOutputStream(File(directory, "$name.png")).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }

    /**
     * A real window-level tap at raw screen coordinates. Injected touches
     * from the compose test framework do not cross into popup windows, so
     * outside-tap dismissal of dropdowns needs UiAutomation input.
     */
    protected fun tapScreenAt(x: Float, y: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        val down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, x, y, 0)
        val up = android.view.MotionEvent.obtain(now, now + 16, android.view.MotionEvent.ACTION_UP, x, y, 0)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.injectInputEvent(down, true)
        instrumentation.uiAutomation.injectInputEvent(up, true)
        down.recycle()
        up.recycle()
    }

    /** A real KEYCODE_BACK at the window level (dismisses popups first). */
    protected fun pressBack() {
        val now = android.os.SystemClock.uptimeMillis()
        val down = android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK, 0)
        val up = android.view.KeyEvent(now, now + 16, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK, 0)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.injectInputEvent(down, true)
        instrumentation.uiAutomation.injectInputEvent(up, true)
    }

    /**
     * The RAM-constrained emulator intermittently shows "System UI isn't
     * responding" ANR dialogs; they are modal and swallow injected taps and
     * ruin captures. Dismiss them (tap Wait) before any window-level input.
     */
    protected fun dismissAnrDialogs() {
        val device = androidx.test.uiautomator.UiDevice
            .getInstance(InstrumentationRegistry.getInstrumentation())
        val wait = device.findObject(androidx.test.uiautomator.By.text("Wait"))
        if (wait != null) {
            runCatching { wait.click() }
        }
    }
}
