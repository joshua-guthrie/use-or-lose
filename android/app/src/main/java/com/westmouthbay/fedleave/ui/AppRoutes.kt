package com.westmouthbay.fedleave.ui

import java.time.LocalDate

/**
 * Navigation routes for the FedLeave app. I05-02 delivers the Calendar,
 * month picker, read-only day route and the placeholder Reports/Settings
 * destinations; I05-03 adds the balance and date-picker routes.
 */
object AppRoutes {

    const val CALENDAR = "calendar"
    const val DAY = "day/{date}"
    const val MONTH_PICKER = "month_picker"
    const val BALANCES = "balances"
    const val BALANCE_DATE_PICKER = "balance_date_picker"
    const val WELCOME = "welcome"
    const val SETUP = "setup/{for_date}"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"

    const val DAY_ARG = "date"
    const val SETUP_ARG = "for_date"

    fun day(date: LocalDate): String = "day/${date}"

    fun setup(forDate: String): String = "setup/$forDate"

    const val SETUP_FOR_WELCOME = "welcome"
}
