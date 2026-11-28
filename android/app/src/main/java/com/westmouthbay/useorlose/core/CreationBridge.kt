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
 * importing the CLI stack. Returns candidate files for the I04
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

data class CarryoverCreateRequest(
    val year: Int,
    val sourceYear: Int,
    val sourceJson: String,
    val payPeriods: Int?,
    val configJson: String?,
)

data class CarryoverReduction(
    val category: String,
    val hours: Double,
    val reason: String,
)

data class CarryoverLot(
    val category: String,
    val hours: Double,
    val originLeaveYear: Int?,
    val originTransactionId: String?,
    val expirationDate: String?,
)

data class CarryoverPreview(
    val year: Int,
    val sourceYear: Int,
    val sourceStart: LocalDate,
    val sourceEnd: LocalDate,
    val start: LocalDate,
    val end: LocalDate,
    val payPeriodCount: Int,
    val firstPayPeriod: LocalDate,
    val openings: Map<String, Double>,
    val reductions: List<CarryoverReduction>,
    val lots: List<CarryoverLot>,
    val warnings: List<String>,
    val sourceFingerprint: String,
    val previewFingerprint: String,
)

data class CarryoverCreationResult(
    val files: Map<String, String>,
    val preview: CarryoverPreview,
)

/** The creation surface the setup flow consumes (fakes in tests). */
interface CreationRead {
    suspend fun previewManual(request: ManualCreateRequest): CreationResult
    suspend fun previewToday(request: TodayCreateRequest): CreationResult
    suspend fun previewCarryover(request: CarryoverCreateRequest): CarryoverCreationResult
}

class CreationBridge() : CreationRead {

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

    override suspend fun previewCarryover(request: CarryoverCreateRequest): CarryoverCreationResult =
        withContext(Dispatchers.IO) {
            val envelope = invoke(
                JSONObject()
                    .put("operation", "carryover_create")
                    .put("year", request.year)
                    .put("source_year", request.sourceYear)
                    .put("source", request.sourceJson)
                    .put("pay_periods", request.payPeriods ?: JSONObject.NULL)
                    .apply { request.configJson?.let { put("config", it) } },
            )
            val result = envelope.optJSONObject("result")
                ?: throw CoreError.Unexpected("Creation bridge returned ok=true without a result.")
            val previewObject = result.getJSONObject("preview")
            CarryoverCreationResult(
                files = parseFiles(result),
                preview = CarryoverPreview(
                    year = previewObject.getInt("year"),
                    sourceYear = previewObject.getInt("source_year"),
                    sourceStart = LocalDate.parse(previewObject.getString("source_start")),
                    sourceEnd = LocalDate.parse(previewObject.getString("source_end")),
                    start = LocalDate.parse(previewObject.getString("start")),
                    end = LocalDate.parse(previewObject.getString("end")),
                    payPeriodCount = previewObject.getInt("pay_period_count"),
                    firstPayPeriod = LocalDate.parse(previewObject.getString("first_pay_period")),
                    openings = previewObject.optJSONObject("openings")?.let { openings ->
                        val map = mutableMapOf<String, Double>()
                        for (key in openings.keys()) {
                            map[key] = openings.getDouble(key)
                        }
                        map
                    } ?: emptyMap(),
                    reductions = previewObject.optJSONArray("reductions")?.let { reductions ->
                        buildList {
                            for (index in 0 until reductions.length()) {
                                val row = reductions.getJSONObject(index)
                                add(
                                    CarryoverReduction(
                                        category = row.getString("category"),
                                        hours = row.getDouble("hours"),
                                        reason = row.optString("reason", ""),
                                    ),
                                )
                            }
                        }
                    } ?: emptyList(),
                    lots = previewObject.optJSONArray("lots")?.let { lots ->
                        buildList {
                            for (index in 0 until lots.length()) {
                                val row = lots.getJSONObject(index)
                                add(
                                    CarryoverLot(
                                        category = row.getString("category"),
                                        hours = row.getDouble("hours"),
                                        originLeaveYear = row.optInt("origin_leave_year", -1).takeIf { it != -1 },
                                        originTransactionId = row.optString("origin_transaction_id")
                                            .takeIf { it.isNotEmpty() },
                                        expirationDate = row.optString("expiration_date")
                                            .takeIf { it.isNotEmpty() && it != "null" },
                                    ),
                                )
                            }
                        }
                    } ?: emptyList(),
                    warnings = previewObject.optJSONArray("warnings")?.let { warnings ->
                        buildList {
                            for (index in 0 until warnings.length()) {
                                add(warnings.getString(index))
                            }
                        }
                    } ?: emptyList(),
                    sourceFingerprint = previewObject.getString("source_fingerprint"),
                    previewFingerprint = previewObject.getString("preview_fingerprint"),
                ),
            )
        }

    /** Send one envelope to the bridge module and return the ok-verified response. */
    private suspend fun invoke(request: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val payload = JSONObject(request.toString()).put("schema_version", 1)
        val raw = try {
            Python.getInstance().getModule("useorlose_creation").callAttr("create", payload.toString()).toString()
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
        envelope
    }

    private fun parseFiles(result: JSONObject): Map<String, String> {
        val files = mutableMapOf<String, String>()
        val filesObject = result.optJSONObject("files") ?: JSONObject()
        for (key in filesObject.keys()) {
            files[key] = filesObject.getString(key)
        }
        return files
    }

    private suspend fun call(request: JSONObject): CreationResult =
        withContext(Dispatchers.IO) {
            // The creation module reads the envelope fields at the top level
            // (operation, year, ...) alongside schema_version.
            val envelope = invoke(request)
            val result = envelope.optJSONObject("result")
                ?: throw CoreError.Unexpected("Creation bridge returned ok=true without a result.")
            val files = parseFiles(result)
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
