package com.westmouthbay.fedleave.calendar

import com.westmouthbay.fedleave.core.CoreError
import com.westmouthbay.fedleave.core.DatedBalanceResult
import com.westmouthbay.fedleave.core.MonthViewResult
import com.westmouthbay.fedleave.core.PayPeriodDetailResult
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
) {
    /** True when the committed cutoff is later than today; a UI label only. */
    val isProjected: Boolean
        get() = today != null && balanceDate != null && balanceDate > today
}
