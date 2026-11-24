package com.westmouthbay.fedleave.core

import org.json.JSONObject
import java.time.LocalDate

/**
 * Typed results of the versioned read facade (`fedleave.read_api`, schema 1)
 * as returned through the Chaquopy bridge module `fedleave_probe`.
 *
 * Every parsing function takes the JSON `result` object from a successful
 * envelope and never mutates it. Shapes mirror the pinned goldens under
 * `android/qa/golden/calendar-2026/`.
 */

data class DatedBalanceRequest(
    val asOf: LocalDate,
    val year: Int? = null,
)

data class MonthViewRequest(
    val year: Int,
    val month: Int,
    val calendarYear: Int = year,
)

data class DateRequest(
    val date: LocalDate,
    val year: Int? = null,
)

data class ParentalSummary(
    val pplAvailableHours: Double?,
    val fmlaRemainingHours: Double?,
)

data class DatedBalanceResult(
    val year: Int,
    val asOf: LocalDate,
    val projected: Boolean,
    val projectTo: String?,
    val balances: Map<String, Double>,
    val parental: ParentalSummary?,
    val warnings: List<String>,
) {
    companion object {
        fun fromJson(json: JSONObject): DatedBalanceResult {
            val balances = mutableMapOf<String, Double>()
            val balancesObject = json.getJSONObject("balances")
            for (key in balancesObject.keys()) {
                balances[key] = balancesObject.getDouble(key)
            }
            return DatedBalanceResult(
                year = json.getInt("year"),
                asOf = LocalDate.parse(json.getString("as_of")),
                projected = json.optBoolean("projected"),
                projectTo = json.nullableString("project_to"),
                balances = balances,
                parental = json.nullableObject("parental")?.let { parental ->
                    val fmla = parental.nullableObject("fmla")
                    ParentalSummary(
                        pplAvailableHours = parental.nullableDouble("ppl_available_hours"),
                        fmlaRemainingHours = fmla?.nullableDouble("remaining_hours"),
                    )
                },
                warnings = json.stringList("warnings"),
            )
        }
    }
}

data class DayEntry(
    val id: String?,
    val category: String,
    val direction: String?,
    val hours: Double,
    val status: String?,
    val source: String?,
    val description: String,
) {
    companion object {
        fun fromJson(json: JSONObject): DayEntry = DayEntry(
            id = json.nullableString("id"),
            category = json.getString("category"),
            direction = json.nullableString("direction"),
            hours = json.getDouble("hours"),
            status = json.nullableString("status"),
            source = json.nullableString("source"),
            description = json.optString("description", ""),
        )
    }
}

data class CategoryTotals(
    val earned: Double,
    val used: Double,
    val worked: Double,
    val net: Double,
) {
    companion object {
        fun fromJson(json: JSONObject): CategoryTotals = CategoryTotals(
            earned = json.optDouble("earned", 0.0),
            used = json.optDouble("used", 0.0),
            worked = json.optDouble("worked", 0.0),
            net = json.optDouble("net", 0.0),
        )

        /**
         * Build per-category totals from an activity object shaped
         * `{"earned": {…}, "used": {…}, "worked": {…}, "net": {…}}`.
         */
        fun fromActivityObject(json: JSONObject): Map<String, CategoryTotals> {
            val earned = json.optJSONObject("earned") ?: JSONObject()
            val used = json.optJSONObject("used") ?: JSONObject()
            val worked = json.optJSONObject("worked") ?: JSONObject()
            val net = json.optJSONObject("net") ?: JSONObject()
            val keys = (
                earned.keys().asSequence() +
                    used.keys().asSequence() +
                    worked.keys().asSequence() +
                    net.keys().asSequence()
                ).toSet()
            return keys.associateWith { category ->
                CategoryTotals(
                    earned = earned.optDouble(category, 0.0),
                    used = used.optDouble(category, 0.0),
                    worked = worked.optDouble(category, 0.0),
                    net = net.optDouble(category, 0.0),
                )
            }
        }
    }
}

data class MonthDay(
    val date: LocalDate,
    val inDisplayMonth: Boolean,
    val holidayName: String?,
    val isToday: Boolean,
    val isPayday: Boolean,
    val isPayPeriodEnd: Boolean,
    val entries: List<DayEntry>,
    val displayLines: List<String>,
) {
    companion object {
        fun fromJson(json: JSONObject): MonthDay = MonthDay(
            date = LocalDate.parse(json.getString("date")),
            inDisplayMonth = json.optBoolean("in_display_month"),
            holidayName = json.nullableString("holiday_name"),
            isToday = json.optBoolean("is_today"),
            isPayday = json.optBoolean("is_payday"),
            isPayPeriodEnd = json.optBoolean("is_pay_period_end"),
            entries = json.objectList("entries") { DayEntry.fromJson(it) },
            displayLines = json.stringList("display_lines"),
        )
    }
}

data class PeriodRow(
    val number: Int?,
    val start: LocalDate,
    val end: LocalDate,
    val payDate: LocalDate?,
    val touchesDisplayMonth: Boolean,
    val totals: Map<String, CategoryTotals>,
    val endingBalances: Map<String, Double>,
) {
    companion object {
        fun fromJson(json: JSONObject): PeriodRow = PeriodRow(
            number = json.nullableInt("number"),
            start = LocalDate.parse(json.getString("start")),
            end = LocalDate.parse(json.getString("end")),
            payDate = json.nullableDate("pay_date"),
            touchesDisplayMonth = json.optBoolean("touches_display_month"),
            totals = json.getJSONObject("totals").keys().asSequence()
                .map { key -> key to CategoryTotals.fromJson(json.getJSONObject("totals").getJSONObject(key)) }
                .toMap(),
            endingBalances = json.doubleMap("ending_balances"),
        )
    }
}

data class MonthViewResult(
    val year: Int,
    val month: Int,
    val today: LocalDate,
    val monthStart: LocalDate,
    val monthEnd: LocalDate,
    val calendarStart: LocalDate,
    val calendarEnd: LocalDate,
    val days: List<MonthDay>,
    val payPeriods: List<PeriodRow>,
    val payDates: Set<LocalDate>,
    val payPeriodEndDates: Set<LocalDate>,
    val warnings: List<String>,
) {
    companion object {
        fun fromJson(json: JSONObject): MonthViewResult = MonthViewResult(
            year = json.getInt("year"),
            month = json.getInt("month"),
            today = LocalDate.parse(json.getString("today")),
            monthStart = LocalDate.parse(json.getString("month_start")),
            monthEnd = LocalDate.parse(json.getString("month_end")),
            calendarStart = LocalDate.parse(json.getString("calendar_start")),
            calendarEnd = LocalDate.parse(json.getString("calendar_end")),
            days = json.objectList("days") { MonthDay.fromJson(it) },
            payPeriods = json.objectList("pay_periods") { PeriodRow.fromJson(it) },
            payDates = json.localDateSet("pay_dates"),
            payPeriodEndDates = json.localDateSet("pay_period_end_dates"),
            warnings = json.stringList("warnings"),
        )
    }
}

data class PeriodInfo(
    val number: Int?,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val payDate: LocalDate?,
    val accrualDate: LocalDate?,
) {
    companion object {
        fun fromJson(json: JSONObject): PeriodInfo = PeriodInfo(
            number = json.nullableInt("pay_period_number"),
            startDate = LocalDate.parse(json.getString("start_date")),
            endDate = LocalDate.parse(json.getString("end_date")),
            payDate = json.nullableDate("pay_date"),
            accrualDate = json.nullableDate("accrual_date"),
        )
    }
}

data class PayPeriodDetailResult(
    val year: Int,
    val date: LocalDate,
    val period: PeriodInfo?,
    val activity: Map<String, CategoryTotals>,
    val endingBalances: Map<String, Double>,
    val warnings: List<String>,
) {
    companion object {
        fun fromJson(json: JSONObject): PayPeriodDetailResult = PayPeriodDetailResult(
            year = json.getInt("year"),
            date = LocalDate.parse(json.getString("date")),
            period = json.nullableObject("pay_period")?.let { PeriodInfo.fromJson(it) },
            activity = CategoryTotals.fromActivityObject(json.optJSONObject("activity") ?: JSONObject()),
            endingBalances = json.doubleMap("ending_balances"),
            warnings = json.stringList("warnings"),
        )
    }
}

data class DayActivityResult(
    val year: Int,
    val date: LocalDate,
    val hasActivity: Boolean,
    val activity: Map<String, CategoryTotals>,
) {
    companion object {
        fun fromJson(json: JSONObject): DayActivityResult = DayActivityResult(
            year = json.getInt("year"),
            date = LocalDate.parse(json.getString("date")),
            hasActivity = json.optBoolean("has_activity"),
            activity = CategoryTotals.fromActivityObject(json.optJSONObject("activity") ?: JSONObject()),
        )
    }
}

/**
 * A structured failure from the read path. [Read] carries the facade's own
 * machine-readable code (`INVALID_REQUEST`, `LEAVE_YEAR_NOT_FOUND`, …);
 * [Unexpected] is a local failure that the facade never reported (bad
 * envelope, bridge crash, unusable store).
 */
sealed class CoreError : Exception() {
    abstract val code: String
    abstract val field: String?

    class Read(
        override val code: String,
        override val field: String?,
        message: String,
    ) : CoreError() {
        override val message: String = message
    }

    class Unexpected(
        message: String,
        cause: Throwable? = null,
    ) : CoreError() {
        override val code: String = "UNEXPECTED"
        override val field: String? = null
        override val message: String = message
        init {
            if (cause != null) {
                initCause(cause)
            }
        }
    }

    companion object {
        const val LEAVE_YEAR_NOT_FOUND = "LEAVE_YEAR_NOT_FOUND"

        /** Parse the `error` object of a failed envelope (`ok: false`). */
        fun fromErrorObject(error: JSONObject): CoreError = Read(
            code = error.optString("code", "UNKNOWN"),
            field = error.nullableString("field"),
            message = error.optString("message", ""),
        )
    }
}

// Small org.json helpers shared by the parsing functions above.

private fun JSONObject.nullableString(key: String): String? = when {
    !has(key) || isNull(key) -> null
    else -> getString(key)
}

private fun JSONObject.nullableObject(key: String): JSONObject? = when {
    !has(key) || isNull(key) -> null
    else -> optJSONObject(key)
}

private fun JSONObject.nullableDouble(key: String): Double? = when {
    !has(key) || isNull(key) -> null
    else -> getDouble(key)
}

private fun JSONObject.nullableInt(key: String): Int? = when {
    !has(key) || isNull(key) -> null
    else -> getInt(key)
}

private fun JSONObject.nullableDate(key: String): LocalDate? = when {
    !has(key) || isNull(key) -> null
    else -> LocalDate.parse(getString(key))
}

private fun JSONObject.stringList(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).map { array.getString(it) }
}

private fun JSONObject.localDateSet(key: String): Set<LocalDate> {
    val array = optJSONArray(key) ?: return emptySet()
    return (0 until array.length()).map { LocalDate.parse(array.getString(it)) }.toSet()
}

private fun JSONObject.doubleMap(key: String): Map<String, Double> {
    val objectValue = optJSONObject(key) ?: return emptyMap()
    val result = mutableMapOf<String, Double>()
    for (entryKey in objectValue.keys()) {
        result[entryKey] = objectValue.getDouble(entryKey)
    }
    return result
}

private inline fun <T> JSONObject.objectList(key: String, transform: (JSONObject) -> T): List<T> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).map { transform(array.getJSONObject(it)) }
}
