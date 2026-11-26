package com.westmouthbay.useorlose.core

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.westmouthbay.useorlose.calendar.CalendarDeveloperConfig
import com.westmouthbay.useorlose.storage.StorageSnapshot
import com.westmouthbay.useorlose.storage.StorePaths
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.file.Files
import java.time.LocalDate

/**
 * End-to-end adapter contract on the device: the typed CoreBridge over
 * `useorlose_probe` must reproduce the pinned goldens from the Calendar 2026
 * fixture. The snapshot is built directly from the same fixture asset bytes
 * the store importer uses, so this suite neither reads nor writes the
 * app-private store and cannot collide with the storage suites. The probe
 * itself stays covered by BridgeProbeTest.
 */
@RunWith(AndroidJUnit4::class)
class CoreBridgeTest {

    private val frozen = LocalDate.parse(CalendarDeveloperConfig.FIXTURE_LOCAL_DATE)

    @Before
    fun startPythonOnMainThread() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }
        }
    }

    private fun targetContext() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun assetsContext() = InstrumentationRegistry.getInstrumentation().context

    private fun asset(name: String): String =
        assetsContext().assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun assetSnapshot(): StorageSnapshot = StorageSnapshot(
        generationId = "asset-snapshot",
        files = CalendarDeveloperConfig.calendarFixtureAssets.associateWith { asset(it) },
    )

    private fun storeTree(): Map<String, String> {
        val dir = StorePaths.forContext(targetContext())
        if (!dir.exists()) {
            return emptyMap()
        }
        return Files.walk(dir.toPath())
            .filter { Files.isRegularFile(it) }
            .toList()
            .associate { it.toString() to it.toFile().readText(Charsets.UTF_8) }
    }

    private fun goldenBalances(name: String): Map<String, Double> {
        val golden = JSONObject(asset(name)).getJSONObject("balances")
        val result = mutableMapOf<String, Double>()
        for (key in golden.keys()) {
            result[key] = golden.getDouble(key)
        }
        return result
    }

    @Test
    fun datedBalanceMatchesPinnedGolden() = runBlocking {
        val snapshot = assetSnapshot()
        val before = storeTree()
        val bridge = CoreBridge()

        val result = bridge.datedBalance(
            snapshot,
            DatedBalanceRequest(asOf = LocalDate.parse("2026-01-16")),
            frozen,
        )

        val golden = goldenBalances("balance-2026-01-16.json")
        assertEquals(golden, result.balances)
        assertEquals(2026, result.year)
        assertEquals(LocalDate.parse("2026-01-16"), result.asOf)
        assertFalse(result.projected)
        assertNull(result.projectTo)
        assertEquals(before, storeTree())
    }

    @Test
    fun monthViewMatchesPinnedGolden() = runBlocking {
        val snapshot = assetSnapshot()
        val before = storeTree()
        val bridge = CoreBridge()

        val result = bridge.monthView(
            snapshot,
            MonthViewRequest(year = 2026, month = 1),
            frozen,
        )

        val golden = JSONObject(asset("month-2026-01.json"))
        assertEquals(frozen, result.today)
        assertEquals(golden.getJSONArray("days").length(), result.days.size)
        assertEquals(2026, result.year)
        assertEquals(1, result.month)
        assertEquals(LocalDate.parse("2025-12-28"), result.calendarStart)
        assertEquals(LocalDate.parse("2026-01-31"), result.calendarEnd)

        val day16 = result.days.first { it.date == LocalDate.parse("2026-01-16") }
        assertTrue(day16.isToday)
        assertTrue(day16.inDisplayMonth)
        assertEquals(listOf("A -2.0"), day16.displayLines)
        assertEquals("20260116-001", day16.entries.single().id)
        assertEquals(2.0, day16.entries.single().hours, 0.0)

        val day24 = result.days.first { it.date == LocalDate.parse("2026-01-24") }
        assertTrue(day24.isPayPeriodEnd)
        assertFalse(day24.isPayday)

        assertEquals(setOf(LocalDate.parse("2026-01-30")), result.payDates)
        // The facade lists ends of every period touching the month range;
        // the grid marker layer only draws days inside the visible range.
        assertEquals(
            setOf(LocalDate.parse("2026-01-24"), LocalDate.parse("2026-02-07")),
            result.payPeriodEndDates,
        )

        val period1 = result.payPeriods.first { it.end == LocalDate.parse("2026-01-24") }
        assertEquals(1, period1.number)
        assertEquals(LocalDate.parse("2026-01-11"), period1.start)
        assertEquals(LocalDate.parse("2026-01-30"), period1.payDate)
        assertEquals(6.0, period1.totals.getValue("annual").earned, 0.0)
        assertEquals(14.0, period1.totals.getValue("annual").used, 0.0)
        assertEquals(92.0, period1.endingBalances.getValue("annual"), 0.0)
        assertEquals(24.0, period1.endingBalances.getValue("sick"), 0.0)
        assertEquals(before, storeTree())
    }

    @Test
    fun january2027UsesThe2026LedgerAndShowsItsMarkers() = runBlocking {
        val snapshot = assetSnapshot()
        val before = storeTree()
        val bridge = CoreBridge()

        val result = bridge.monthView(
            snapshot,
            MonthViewRequest(year = 2026, month = 1, calendarYear = 2027),
            frozen,
        )

        assertEquals(2027, result.year)
        assertEquals(1, result.month)
        assertEquals(LocalDate.parse("2027-01-01"), result.monthStart)
        val jan9 = result.days.first { it.date == LocalDate.parse("2027-01-09") }
        assertTrue(jan9.isPayPeriodEnd)
        val jan15 = result.days.first { it.date == LocalDate.parse("2027-01-15") }
        assertTrue(jan15.isPayday)
        assertEquals(before, storeTree())
    }

    @Test
    fun payPeriodMatchesPinnedGolden() = runBlocking {
        val snapshot = assetSnapshot()
        val before = storeTree()
        val bridge = CoreBridge()

        val result = bridge.payPeriod(
            snapshot,
            DateRequest(LocalDate.parse("2026-01-24")),
            frozen,
        )

        assertEquals(2026, result.year)
        assertEquals(LocalDate.parse("2026-01-24"), result.date)
        assertEquals(1, result.period?.number)
        assertEquals(LocalDate.parse("2026-01-11"), result.period?.startDate)
        assertEquals(LocalDate.parse("2026-01-30"), result.period?.payDate)
        assertEquals(6.0, result.activity.getValue("annual").earned, 0.0)
        assertEquals(14.0, result.activity.getValue("annual").used, 0.0)
        assertEquals(4.0, result.activity.getValue("sick").earned, 0.0)
        assertEquals(0.0, result.activity.getValue("sick").used, 0.0)
        assertEquals(92.0, result.endingBalances.getValue("annual"), 0.0)
        assertEquals(24.0, result.endingBalances.getValue("sick"), 0.0)
        assertEquals(before, storeTree())
    }

    @Test
    fun dayActivityMatchesPinnedGolden() = runBlocking {
        val snapshot = assetSnapshot()
        val before = storeTree()
        val bridge = CoreBridge()

        val result = bridge.dayActivity(
            snapshot,
            DateRequest(LocalDate.parse("2026-01-16")),
            frozen,
        )

        assertTrue(result.hasActivity)
        assertEquals(2026, result.year)
        assertEquals(2.0, result.activity.getValue("annual").used, 0.0)
        assertEquals(before, storeTree())
    }

    @Test
    fun uncoveredDateMapsToStructuredError() = runBlocking {
        val snapshot = assetSnapshot()
        val before = storeTree()
        val bridge = CoreBridge()

        val failure = runCatching {
            bridge.datedBalance(
                snapshot,
                DatedBalanceRequest(asOf = LocalDate.parse("2019-01-01")),
                frozen,
            )
        }.exceptionOrNull()

        assertTrue("expected CoreError, got $failure", failure is CoreError.Read)
        failure as CoreError.Read
        assertEquals(CoreError.LEAVE_YEAR_NOT_FOUND, failure.code)
        assertEquals("as_of", failure.field)
        assertEquals(before, storeTree())
    }

    @Test
    fun reportsColdAndWarmTimings() = runBlocking {
        val snapshot = assetSnapshot()
        val bridge = CoreBridge()
        val request = DatedBalanceRequest(asOf = LocalDate.parse("2026-01-16"))

        val coldStart = System.nanoTime()
        bridge.datedBalance(snapshot, request, frozen)
        val coldMs = (System.nanoTime() - coldStart) / 1_000_000.0

        val warmSamples = DoubleArray(20)
        for (i in 0 until 20) {
            val start = System.nanoTime()
            bridge.datedBalance(snapshot, request, frozen)
            warmSamples[i] = (System.nanoTime() - start) / 1_000_000.0
        }
        warmSamples.sort()
        Log.i(
            "CoreBridge",
            String.format(
                "cold_ms=%.2f median_warm_ms=%.2f min_ms=%.2f max_ms=%.2f",
                coldMs,
                warmSamples[10],
                warmSamples[0],
                warmSamples[19],
            ),
        )
        assertTrue(coldMs > 0.0)
    }
}
