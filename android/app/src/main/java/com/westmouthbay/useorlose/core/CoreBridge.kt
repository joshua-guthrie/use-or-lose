package com.westmouthbay.useorlose.core

import com.chaquo.python.Python
import com.westmouthbay.useorlose.storage.StorageSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

/**
 * One typed in-process interface to the Python read facade, served by the
 * Chaquopy module `useorlose_probe`. All calls run off the main thread and are
 * pure reads: they build the schema-1 envelope, dispatch `call`, and map the
 * JSON response onto [CoreModels]. Failures surface as [CoreError].
 */
interface CoreRead {
    suspend fun datedBalance(
        snapshot: StorageSnapshot,
        request: DatedBalanceRequest,
        localDate: LocalDate,
    ): DatedBalanceResult

    suspend fun monthView(
        snapshot: StorageSnapshot,
        request: MonthViewRequest,
        localDate: LocalDate,
    ): MonthViewResult

    suspend fun payPeriod(
        snapshot: StorageSnapshot,
        request: DateRequest,
        localDate: LocalDate,
    ): PayPeriodDetailResult

    suspend fun dayActivity(
        snapshot: StorageSnapshot,
        request: DateRequest,
        localDate: LocalDate,
    ): DayActivityResult
}

class CoreBridge(private val python: Python = Python.getInstance()) : CoreRead {

    override suspend fun datedBalance(
        snapshot: StorageSnapshot,
        request: DatedBalanceRequest,
        localDate: LocalDate,
    ): DatedBalanceResult = DatedBalanceResult.fromJson(
        call(snapshot, requestJson("dated_balance", request.asOf, request.year), localDate),
    )

    override suspend fun monthView(
        snapshot: StorageSnapshot,
        request: MonthViewRequest,
        localDate: LocalDate,
    ): MonthViewResult = MonthViewResult.fromJson(
        call(
            snapshot,
            JSONObject()
                .put("operation", "month_view")
                .put("year", request.year)
                .put("month", request.month)
                .put("calendar_year", request.calendarYear),
            localDate,
        ),
    )

    override suspend fun payPeriod(
        snapshot: StorageSnapshot,
        request: DateRequest,
        localDate: LocalDate,
    ): PayPeriodDetailResult = PayPeriodDetailResult.fromJson(
        call(snapshot, requestJson("pay_period", request.date, request.year), localDate),
    )

    override suspend fun dayActivity(
        snapshot: StorageSnapshot,
        request: DateRequest,
        localDate: LocalDate,
    ): DayActivityResult = DayActivityResult.fromJson(
        call(snapshot, requestJson("day_activity", request.date, request.year), localDate),
    )

    private fun requestJson(operation: String, date: LocalDate, year: Int?): JSONObject {
        val request = JSONObject()
            .put("operation", operation)
            .put(if (operation == "dated_balance") "as_of" else "date", date.toString())
        if (year != null) {
            request.put("year", year)
        }
        return request
    }

    private suspend fun call(
        snapshot: StorageSnapshot,
        request: JSONObject,
        localDate: LocalDate,
    ): JSONObject = withContext(Dispatchers.IO) {
        val files = JSONObject()
        for ((name, text) in snapshot.files) {
            files.put(name, text)
        }
        val payload = JSONObject()
            .put("schema_version", 1)
            .put("local_date", localDate.toString())
            .put("files", files)
            .put("request", request)
        val raw = try {
            python.getModule("useorlose_probe").callAttr("call", payload.toString()).toString()
        } catch (error: Throwable) {
            throw CoreError.Unexpected("Bridge call failed: ${error.message}", error)
        }
        val envelope = try {
            JSONObject(raw)
        } catch (error: Exception) {
            throw CoreError.Unexpected("Bridge returned non-JSON text.", error)
        }
        if (envelope.optBoolean("ok")) {
            envelope.optJSONObject("result")
                ?: throw CoreError.Unexpected("Bridge returned ok=true without a result object.")
        } else {
            throw CoreError.fromErrorObject(envelope.optJSONObject("error") ?: JSONObject())
        }
    }
}
