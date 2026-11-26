package com.westmouthbay.useorlose.core

import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

/**
 * The typed write-side creation surface over the bridge module
 * `useorlose_creation`, which reuses the approved desktop builders
 * (`manual_ledger`, dated accrual changes, forced-balance rows) without
 * importing the desktop CLI stack. Returns candidate files for the I04
 * store publisher plus a preview summary. All calls run off the main
 * thread and map failures to [CoreError].
 */

data class ManualCreateRequest(
    val year: Int,
    val leaveYearStart: LocalDate,
    val payPeriods: Int?,
    val annualAccrual: Double,
    val sickAccrual: Double,
    val balances: Map<String, Double>,
    val configJson: String?,
    val timezone: String,
)

data class TodayCreateRequest(
    val year: Int,
    val leaveYearStart: LocalDate,
    val payPeriods: Int?,
    val annualAccrual: Double,
    val sickAccrual: Double,
    val today: LocalDate,
    val targets: Map<String, Double>,
    val configJson: String?,
    val timezone: String,
)

data class NextAccrual(
    val date: LocalDate,
    val annual: Double,
    val sick: Double,
)

data class CreationPreview(
    val year: Int,
    val start: LocalDate,
    val end: LocalDate,
    val payPeriodCount: Int,
    val firstPayPeriod: LocalDate,
    val annualAccrual: Double,
    val sickAccrual: Double,
    val openings: Map<String, Double>,
    val nextAccrual: NextAccrual?,
)

data class CreationResult(
    val files: Map<String, String>,
    val preview: CreationPreview,
)

/** The creation surface the setup flow consumes (fakes in tests). */
interface CreationRead {
    suspend fun previewManual(request: ManualCreateRequest): CreationResult
    suspend fun previewToday(request: TodayCreateRequest): CreationResult
}

class CreationBridge(private val python: Python = Python.getInstance()) : CreationRead {

    override suspend fun previewManual(request: ManualCreateRequest): CreationResult = call(
        JSONObject()
            .put("operation", "manual_create")
            .put("year", request.year)
            .put("leave_year_start", request.leaveYearStart.toString())
            .put("pay_periods", request.payPeriods ?: JSONObject.NULL)
            .put("annual_accrual", request.annualAccrual)
            .put("sick_accrual", request.sickAccrual)
            .put("balances", JSONObject(request.balances))
            .put("timezone", request.timezone)
            .apply { request.configJson?.let { put("config", it) } },
    )

    override suspend fun previewToday(request: TodayCreateRequest): CreationResult = call(
        JSONObject()
            .put("operation", "today_create")
            .put("year", request.year)
            .put("leave_year_start", request.leaveYearStart.toString())
            .put("pay_periods", request.payPeriods ?: JSONObject.NULL)
            .put("annual_accrual", request.annualAccrual)
            .put("sick_accrual", request.sickAccrual)
            .put("today", request.today.toString())
            .put("balances", JSONObject(request.targets))
            .put("timezone", request.timezone)
            .apply { request.configJson?.let { put("config", it) } },
    )

    private suspend fun call(request: JSONObject): CreationResult =
        withContext(Dispatchers.IO) {
            // The creation module reads the envelope fields at the top level
            // (operation, year, ...) alongside schema_version.
            val payload = JSONObject(request.toString())
                .put("schema_version", 1)
            val raw = try {
                python.getModule("useorlose_creation").callAttr("create", payload.toString()).toString()
            } catch (error: Throwable) {
                throw CoreError.Unexpected("Creation call failed: ${error.message}", error)
            }
            val envelope = try {
                JSONObject(raw)
            } catch (error: Exception) {
                throw CoreError.Unexpected("Creation bridge returned non-JSON text.", error)
            }
            if (!envelope.optBoolean("ok")) {
                throw CoreError.fromErrorObject(envelope.optJSONObject("error") ?: JSONObject())
            }
            val result = envelope.optJSONObject("result")
                ?: throw CoreError.Unexpected("Creation bridge returned ok=true without a result.")
            val files = mutableMapOf<String, String>()
            val filesObject = result.optJSONObject("files") ?: JSONObject()
            for (key in filesObject.keys()) {
                files[key] = filesObject.getString(key)
            }
            val previewObject = result.getJSONObject("preview")
            CreationResult(
                files = files,
                preview = CreationPreview(
                    year = previewObject.getInt("year"),
                    start = LocalDate.parse(previewObject.getString("start")),
                    end = LocalDate.parse(previewObject.getString("end")),
                    payPeriodCount = previewObject.getInt("pay_period_count"),
                    firstPayPeriod = LocalDate.parse(previewObject.getString("first_pay_period")),
                    annualAccrual = previewObject.getDouble("annual_accrual"),
                    sickAccrual = previewObject.getDouble("sick_accrual"),
                    openings = previewObject.optJSONObject("openings")?.let { openings ->
                        val map = mutableMapOf<String, Double>()
                        for (key in openings.keys()) {
                            map[key] = openings.getDouble(key)
                        }
                        map
                    } ?: emptyMap(),
                    nextAccrual = previewObject.optJSONObject("next_accrual")?.let { next ->
                        NextAccrual(
                            date = LocalDate.parse(next.getString("date")),
                            annual = next.getDouble("annual"),
                            sick = next.getDouble("sick"),
                        )
                    },
                ),
            )
        }
}
