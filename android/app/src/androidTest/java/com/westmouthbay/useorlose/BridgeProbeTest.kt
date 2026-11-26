package com.westmouthbay.useorlose

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BridgeProbeTest {

    @Before
    fun startPythonOnMainThread() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var startupMs: Double? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            if (!Python.isStarted()) {
                val start = System.nanoTime()
                Python.start(AndroidPlatform(context))
                startupMs = (System.nanoTime() - start) / 1_000_000.0
            }
        }
        if (startupMs != null) {
            Log.i("BridgeProbe", String.format("python_start_ms=%.2f", startupMs))
        }
    }

    private fun probe(): com.chaquo.python.PyObject =
        Python.getInstance().getModule("useorlose_probe")

    private fun asset(name: String): String =
        InstrumentationRegistry.getInstrumentation().context.assets.open(name)
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun call(request: JSONObject): JSONObject {
        val payload = JSONObject()
            .put("schema_version", 1)
            .put("local_date", "2026-01-16")
            .put(
                "files",
                JSONObject()
                    .put("config.json", asset("config.json"))
                    .put("leave_years/2026.json", asset("leave_years/2026.json"))
                    .put(
                        "holiday_cache/federal_holidays_2026.json",
                        asset("holiday_cache/federal_holidays_2026.json"),
                    ),
            )
            .put("request", request)
        val response = JSONObject(probe().callAttr("call", payload.toString()).toString())
        assertTrue(response.getString("ok"), response.getBoolean("ok"))
        return response.getJSONObject("result")
    }

    @Test
    fun datedBalanceMatchesPinnedGolden() {
        val result = call(JSONObject().put("operation", "dated_balance").put("as_of", "2026-01-16"))
        val balances = result.getJSONObject("balances")
        val golden = JSONObject(asset("balance-2026-01-16.json")).getJSONObject("balances")
        assertEquals(golden.length(), balances.length())
        val keys = golden.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            assertEquals(golden.getDouble(key), balances.getDouble(key), 0.0)
        }
    }

    @Test
    fun payPeriodMatchesPinnedGolden() {
        val result = call(JSONObject().put("operation", "pay_period").put("date", "2026-01-24"))
        val golden = JSONObject(asset("pay-period-2026-01-24.json"))
        assertEquals(92.0, result.getJSONObject("ending_balances").getDouble("annual"), 0.0)
        assertEquals(
            golden.getJSONObject("activity").getJSONObject("used").getDouble("annual"),
            result.getJSONObject("activity").getJSONObject("used").getDouble("annual"),
            0.0,
        )
    }

    @Test
    fun dayActivityMatchesPinnedGolden() {
        val result = call(JSONObject().put("operation", "day_activity").put("date", "2026-01-16"))
        assertTrue(result.getBoolean("has_activity"))
        assertEquals(2.0, result.getJSONObject("activity").getJSONObject("used").getDouble("annual"), 0.0)
    }

    @Test
    fun monthViewMatchesPinnedGolden() {
        val result = call(JSONObject().put("operation", "month_view").put("year", 2026).put("month", 1))
        val golden = JSONObject(asset("month-2026-01.json"))
        assertEquals("2026-01-16", result.getString("today"))
        assertEquals(
            golden.getJSONObject("balance_as_of_today").getJSONObject("balances").getDouble("annual"),
            result.getJSONObject("balance_as_of_today").getJSONObject("balances").getDouble("annual"),
            0.0,
        )
        assertEquals(golden.getJSONArray("days").length(), result.getJSONArray("days").length())
    }

    @Test
    fun rejectsMalformedRequestsWithoutLoadingDesktopModules() {
        val probe = probe()
        val bad = JSONObject(probe.callAttr("call", "not-json").toString())
        assertFalse(bad.getBoolean("ok"))
        assertEquals("INVALID_REQUEST", bad.getJSONObject("error").getString("code"))

        val loaded = JSONArray(probe.callAttr("loaded_modules").toString())
        val names = (0 until loaded.length()).map { loaded.getString(it) }.toSet()
        for (name in listOf("typer", "rich", "mcp", "PySide6", "matplotlib", "pydantic", "PIL")) {
            assertFalse("$name must not load", name in names)
        }
    }

    @Test
    fun reportsColdAndWarmTimings() {
        val probe = probe()
        val request = JSONObject().put("operation", "dated_balance").put("as_of", "2026-01-16")

        val coldStart = System.nanoTime()
        call(request)
        val coldMs = (System.nanoTime() - coldStart) / 1_000_000.0

        val warmSamples = DoubleArray(20)
        for (i in 0 until 20) {
            val start = System.nanoTime()
            call(request)
            warmSamples[i] = (System.nanoTime() - start) / 1_000_000.0
        }
        warmSamples.sort()
        Log.i(
            "BridgeProbe",
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
