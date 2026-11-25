package com.westmouthbay.fedleave.calendar

import com.westmouthbay.fedleave.core.CoreError
import com.westmouthbay.fedleave.core.DatedBalanceResult
import com.westmouthbay.fedleave.core.MonthViewResult
import com.westmouthbay.fedleave.core.PayPeriodDetailResult
import com.westmouthbay.fedleave.storage.StorageSnapshot
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

/**
 * Snapshot metadata parsed from the store's `leave_years/<year>.json` files:
 * the inclusive year bounds and the complete stored pay-period list. This is
 * presentation input (year-end marker, day events, picker shortcuts); the
 * shared core remains the only accounting authority.
 */
data class StoredPeriod(
    val number: Int?,
    val start: LocalDate,
    val end: LocalDate,
    val payDate: LocalDate?,
)

data class YearMetadata(
    val year: Int,
    val start: LocalDate,
    val end: LocalDate,
    val periods: List<StoredPeriod>,
)

/**
 * Parses leave-year metadata from the snapshot's `leave_years/<year>.json`
 * files. Malformed entries are skipped, never thrown: presentation metadata
 * must not crash the Calendar when a ledger carries unexpected shapes.
 */
internal fun parseYearMetadata(snapshot: StorageSnapshot): Map<Int, YearMetadata> {
    val result = mutableMapOf<Int, YearMetadata>()
    val yearFile = Regex("leave_years/(\\d+)\\.json")
    for ((name, text) in snapshot.files) {
        val match = yearFile.matchEntire(name) ?: continue
        val year = match.groupValues[1].toIntOrNull() ?: continue
        val json = runCatching { JSONObject(text) }.getOrNull() ?: continue
        val start = runCatching { LocalDate.parse(json.optString("leave_year_start")) }.getOrNull()
            ?: continue
        val end = runCatching { LocalDate.parse(json.optString("leave_year_end")) }.getOrNull()
            ?: continue
        val periods = json.optJSONArray("pay_periods")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val period = array.optJSONObject(index) ?: return@mapNotNull null
                val periodStart =
                    runCatching { LocalDate.parse(period.optString("start_date")) }.getOrNull()
                        ?: return@mapNotNull null
                val periodEnd =
                    runCatching { LocalDate.parse(period.optString("end_date")) }.getOrNull()
                        ?: return@mapNotNull null
                StoredPeriod(
                    number = if (period.has("pay_period_number") && !period.isNull("pay_period_number")) {
                        runCatching { period.getInt("pay_period_number") }.getOrNull()
                    } else {
                        null
                    },
                    start = periodStart,
                    end = periodEnd,
                    payDate = period.optString("pay_date").takeIf { it.isNotEmpty() }
                        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                )
            }
        } ?: emptyList()
        result[year] = YearMetadata(year, start, end, periods)
    }
    return result
}

/**
 * The full Calendar screen state. Month and balance results are paired with
 * the keys they were requested for; stale responses are filtered by request
 * identity inside the ViewModel before they reach this state.
 */
data class CalendarUiState(
    val today: LocalDate? = null,
    val displayMonth: YearMonth? = null,
    val balanceDate: LocalDate? = null,
    val followsToday: Boolean = true,
    /** The date of the opened read-only day route, or null when closed. */
    val selectedDay: LocalDate? = null,
    /** Transient picker state; cleared on cancel, confirm and back. */
    val pickerDraft: LocalDate? = null,
    val pickerMonth: YearMonth? = null,
    /** The shortcut anchor while the picker is open. */
    val pickerAnchor: LocalDate? = null,
    /** Month result for the picker's own displayed month. */
    val pickerMonthFor: YearMonth? = null,
    val pickerMonthResult: MonthViewResult? = null,
    val pickerMonthLoading: Boolean = false,
    val pickerMonthError: CoreError? = null,
    /** Month result keyed to the month it was requested for. */
    val monthFor: YearMonth? = null,
    val month: MonthViewResult? = null,
    val monthLoading: Boolean = false,
    val monthError: CoreError? = null,
    /** Balance result keyed to the cutoff it was requested for. */
    val balanceFor: LocalDate? = null,
    val balance: DatedBalanceResult? = null,
    val balanceLoading: Boolean = false,
    val balanceError: CoreError? = null,
    /** Period detail keyed to the balance cutoff when it is a period end. */
    val periodFor: LocalDate? = null,
    val period: PayPeriodDetailResult? = null,
    val periodLoading: Boolean = false,
    val periodError: CoreError? = null,
    /** First use or a store that cannot open: route to the setup welcome. */
    val setupRequired: Boolean = false,
) {
    /** True when the committed cutoff is later than today; a UI label only. */
    val isProjected: Boolean
        get() = today != null && balanceDate != null && balanceDate > today
}
