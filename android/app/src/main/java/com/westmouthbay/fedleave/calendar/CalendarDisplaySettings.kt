package com.westmouthbay.fedleave.calendar

import java.time.DayOfWeek

/**
 * Calendar display choices consumed by the month grid, markers and balance
 * picker. I05 ships the defaults; I18 replaces them with persisted
 * preferences. The balance picker always shows pay-period ends regardless of
 * [showPayPeriodEnds], which only affects the main Calendar.
 */
data class CalendarDisplaySettings(
    val showHolidays: Boolean = true,
    val showPaydays: Boolean = true,
    val showPayPeriodEnds: Boolean = true,
    val showAccruals: Boolean = true,
    val weekStart: DayOfWeek = DayOfWeek.SUNDAY,
)
