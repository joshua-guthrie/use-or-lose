package com.westmouthbay.fedleave.core

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.westmouthbay.fedleave.calendar.CalendarDeveloperConfig
import com.westmouthbay.fedleave.storage.StorageSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * The creation bridge on the device: the manual flow matches the desktop
 * CLI reference and the Today flow matches the T-YEAR-TODAY-01 acceptance
 * arithmetic, verified by reading the created candidate through the typed
 * read adapter.
 */
@RunWith(AndroidJUnit4::class)
class CreationBridgeTest {

    @Before
    fun startPython() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }
        }
    }

    @Test
    fun manualCreationMatchesTheDesktopReference() = runBlocking {
        val bridge = CreationBridge()
        val result = bridge.previewManual(
            ManualCreateRequest(
                year = 2027,
                leaveYearStart = LocalDate.parse("2027-01-10"),
                payPeriods = 26,
                annualAccrual = 1.25,
                sickAccrual = 4.0,
                balances = mapOf("travel_comp" to 40.0),
                configJson = null,
                timezone = "America/New_York",
            ),
        )
        val preview = result.preview
        assertEquals(2027, preview.year)
        assertEquals(LocalDate.parse("2027-01-10"), preview.start)
        assertEquals(LocalDate.parse("2028-01-08"), preview.end)
        assertEquals(26, preview.payPeriodCount)
        assertEquals(LocalDate.parse("2027-01-10"), preview.firstPayPeriod)
        assertEquals(1.25, preview.annualAccrual, 0.0)
        assertEquals(4.0, preview.sickAccrual, 0.0)
        assertEquals(40.0, preview.openings["travel_comp"]!!, 0.0)
        assertTrue(result.files.containsKey("config.json"))
        assertTrue(result.files.containsKey("leave_years/2027.json"))

        // The created year reads back through the read adapter.
        val snapshot = StorageSnapshot("created", result.files)
        val read = CoreBridge().datedBalance(
            snapshot,
            DatedBalanceRequest(asOf = LocalDate.parse("2027-01-20")),
            LocalDate.parse("2027-01-20"),
        )
        assertEquals(40.0, read.balances["travel_comp"]!!, 0.0)
        assertEquals(0.0, read.balances["annual"]!!, 0.0)
    }

    @Test
    fun todayCreationMatchesTheAcceptanceArithmetic() = runBlocking {
        val bridge = CreationBridge()
        val result = bridge.previewToday(
            TodayCreateRequest(
                year = 2026,
                leaveYearStart = LocalDate.parse("2026-01-11"),
                payPeriods = 26,
                annualAccrual = 6.0,
                sickAccrual = 4.0,
                today = LocalDate.parse("2026-02-10"),
                targets = mapOf("annual" to 80.0, "sick" to 24.0, "credit" to 2.25),
                configJson = null,
                timezone = "America/New_York",
            ),
        )
        assertEquals(LocalDate.parse("2026-02-21"), result.preview.nextAccrual?.date)
        assertEquals(6.0, result.preview.nextAccrual?.annual!!, 0.0)

        val snapshot = StorageSnapshot("created-today", result.files)
        val read = CoreBridge()
        val jan24 = read.datedBalance(
            snapshot,
            DatedBalanceRequest(asOf = LocalDate.parse("2026-01-24")),
            LocalDate.parse("2026-02-10"),
        )
        assertEquals(0.0, jan24.balances["annual"]!!, 0.0)
        assertEquals(0.0, jan24.balances["sick"]!!, 0.0)
        val feb10 = read.datedBalance(
            snapshot,
            DatedBalanceRequest(asOf = LocalDate.parse("2026-02-10")),
            LocalDate.parse("2026-02-10"),
        )
        assertEquals(80.0, feb10.balances["annual"]!!, 0.0)
        assertEquals(24.0, feb10.balances["sick"]!!, 0.0)
        assertEquals(2.25, feb10.balances["credit"]!!, 0.0)
        val feb21 = read.datedBalance(
            snapshot,
            DatedBalanceRequest(asOf = LocalDate.parse("2026-02-21")),
            LocalDate.parse("2026-02-10"),
        )
        assertEquals(86.0, feb21.balances["annual"]!!, 0.0)
        assertEquals(28.0, feb21.balances["sick"]!!, 0.0)
    }

    @Test
    fun invalidRequestsMapToStructuredErrors() = runBlocking {
        val bridge = CreationBridge()
        val failure = runCatching {
            bridge.previewManual(
                ManualCreateRequest(
                    year = 0,
                    leaveYearStart = LocalDate.parse("2027-01-10"),
                    payPeriods = 26,
                    annualAccrual = 1.25,
                    sickAccrual = 4.0,
                    balances = emptyMap(),
                    configJson = null,
                    timezone = "America/New_York",
                ),
            )
        }.exceptionOrNull()
        assertTrue("expected CoreError, got $failure", failure is CoreError.Read)
        assertEquals("INVALID_YEAR", (failure as CoreError.Read).code)
    }

    @Test
    fun creationModuleDoesNotLoadTheDesktopCliStack() {
        runBlocking {
        val python = Python.getInstance()
        val loaded = python.getModule("fedleave_creation").callAttr("loaded_modules").toString()
        val names = org.json.JSONArray(loaded)
        val top = (0 until names.length()).map { names.getString(it) }.toSet()
        for (name in listOf("typer", "rich", "mcp", "PySide6", "matplotlib", "pydantic", "PIL")) {
            assertFalse("$name must not load", name in top)
        }
        Log.i("CreationBridge", "loaded modules: ${top.sorted()}")
        }
    }
}
