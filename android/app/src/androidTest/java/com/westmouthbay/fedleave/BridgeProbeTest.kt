package com.westmouthbay.fedleave

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
        Python.getInstance().getModule("fedleave_probe")

    private fun asset(name: String): String =
        InstrumentationRegistry.getInstrumentation().context.assets.open(name)
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun request(asOf: String): String =
        JSONObject()
            .put("schema_version", 1)
            .put("as_of", asOf)
            .put("leave_year", JSONObject(asset("leave_years/2026.json")))
            .toString()

    @Test
    fun datedBalanceMatchesPinnedGolden() {
        val response = JSONObject(probe().callAttr("dated_balance", request("2026-01-16")).toString())
        assertTrue(response.getBoolean("ok"))
        assertEquals("2026-01-16", response.getString("as_of"))
        assertEquals(2026, response.getInt("year"))

        val balances = response.getJSONObject("balances")
        val golden = JSONObject(asset("balance-2026-01-16.json")).getJSONObject("balances")
        assertEquals(golden.length(), balances.length())
        val keys = golden.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            assertEquals(golden.getDouble(key), balances.getDouble(key), 0.0)
        }
    }

    @Test
    fun rejectsMalformedRequestsWithoutLoadingDesktopModules() {
        val probe = probe()
        val bad = JSONObject(probe.callAttr("dated_balance", "not-json").toString())
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
        val request = request("2026-01-16")

        val coldStart = System.nanoTime()
        probe.callAttr("dated_balance", request)
        val coldMs = (System.nanoTime() - coldStart) / 1_000_000.0

        val warmSamples = DoubleArray(20)
        for (i in 0 until 20) {
            val start = System.nanoTime()
            probe.callAttr("dated_balance", request)
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
