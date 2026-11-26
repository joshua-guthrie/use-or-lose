package com.westmouthbay.useorlose.calendar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.westmouthbay.useorlose.core.AppDependencies
import com.westmouthbay.useorlose.core.CoreError
import com.westmouthbay.useorlose.core.CoreRead
import com.westmouthbay.useorlose.core.DatedBalanceRequest
import com.westmouthbay.useorlose.core.DatedBalanceResult
import com.westmouthbay.useorlose.core.DateRequest
import com.westmouthbay.useorlose.core.DayActivityResult
import com.westmouthbay.useorlose.core.DayEntry
import com.westmouthbay.useorlose.core.MonthViewRequest
import com.westmouthbay.useorlose.core.MonthViewResult
import com.westmouthbay.useorlose.core.ParentalSummary
import com.westmouthbay.useorlose.core.PayPeriodDetailResult
import com.westmouthbay.useorlose.storage.StorageSnapshot
import com.westmouthbay.useorlose.storage.StoreCoordinator
import com.westmouthbay.useorlose.storage.StorePaths
import org.junit.Test
import java.time.LocalDate

/**
 * Layout and failure states with a fake bridge: the ten-balance card
 * preview and hint (T-CAL-BAL-08), the exactly-eight and unavailable-value
 * cases, the unavailable state with Retry, and the grid's "+N" overflow
 * line for a day with three entries.
 */
open class BalanceLayoutTest : CalendarUiTestBase() {

    open class LayoutBridge : CoreRead {

        var failBalances = false
        var failCount = 0
        val balanceByDate = mutableMapOf<LocalDate, DatedBalanceResult>()
        var compEntry = false

        private fun dated(asOf: LocalDate): DatedBalanceResult {
            if (failBalances) {
                failCount++
                throw CoreError.Read(
                    CoreError.LEAVE_YEAR_NOT_FOUND,
                    "as_of",
                    "No leave year covers ${asOf}.",
                )
            }
            val balances = mutableMapOf(
                "annual" to 90.0, "sick" to 20.0, "comp" to 8.0, "credit" to 4.0,
                "travel_comp" to 6.0, "time_off_award" to 12.0,
                "restored_annual" to 10.0, "religious_comp" to 2.0,
            )
            return DatedBalanceResult(
                year = 2026,
                asOf = asOf,
                projected = false,
                projectTo = null,
                balances = balances,
                parental = ParentalSummary(pplAvailableHours = 60.0, fmlaRemainingHours = 120.0),
                warnings = emptyList(),
            )
        }

        override suspend fun datedBalance(
            snapshot: StorageSnapshot,
            request: DatedBalanceRequest,
            localDate: LocalDate,
        ): DatedBalanceResult = dated(request.asOf)

        override suspend fun monthView(
            snapshot: StorageSnapshot,
            request: MonthViewRequest,
            localDate: LocalDate,
        ): MonthViewResult {
            val start = LocalDate.of(request.calendarYear, request.month, 1)
            val days = (1..start.lengthOfMonth()).map { dayNumber ->
                val date = start.withDayOfMonth(dayNumber)
                val entries = if (compEntry && date == LocalDate.parse("2026-01-24")) {
                    listOf(
                        DayEntry("x1", "annual", "earned", 6.0, "reconciled", "auto_accrual", ""),
                        DayEntry("x2", "sick", "earned", 4.0, "reconciled", "auto_accrual", ""),
                        DayEntry("x3", "comp", "earned", 1.0, "reconciled", "manual", ""),
                    )
                } else {
                    emptyList()
                }
                com.westmouthbay.useorlose.core.MonthDay(
                    date = date,
                    inDisplayMonth = true,
                    holidayName = null,
                    isToday = date == localDate,
                    isPayday = false,
                    isPayPeriodEnd = false,
                    entries = entries,
                    displayLines = entries.map {
                        val signed = if (it.direction == "earned") "+" else "-"
                        "A $signed${it.hours}.0"
                    },
                )
            }
            return MonthViewResult(
                year = request.calendarYear,
                month = request.month,
                today = localDate,
                monthStart = start,
                monthEnd = start.withDayOfMonth(start.lengthOfMonth()),
                calendarStart = start,
                calendarEnd = start.withDayOfMonth(start.lengthOfMonth()),
                days = days,
                payPeriods = emptyList(),
                payDates = emptySet(),
                payPeriodEndDates = emptySet(),
                warnings = emptyList(),
            )
        }

        override suspend fun payPeriod(
            snapshot: StorageSnapshot,
            request: DateRequest,
            localDate: LocalDate,
        ): PayPeriodDetailResult = error("not used")

        override suspend fun dayActivity(
            snapshot: StorageSnapshot,
            request: DateRequest,
            localDate: LocalDate,
        ): DayActivityResult = error("not used")
    }

    protected lateinit var bridge: LayoutBridge

    override fun createDependencies(context: android.content.Context): AppDependencies {
        bridge = LayoutBridge()
        return AppDependencies(
            bridge = bridge,
            store = StoreCoordinator(StorePaths.forContext(context)),
            timeSource = CalendarDeveloperConfig.fixtureTimeSource,
            displaySettings = displaySettings(),
        )
    }

    @Test
    fun tenBalancesPreviewWithHintAndFullList() {
        setAppContent()
        waitForBalanceCard()
        // Eight of ten preview values on the card plus the hint.
        composeRule.onNodeWithText("View all 10 balances \u00b7 2 more").assertIsDisplayed()
        composeRule.onNodeWithText("Comp").assertIsDisplayed()
        composeRule.onNodeWithText("8 h").assertIsDisplayed()
        composeRule.onNodeWithText("Relig. comp").assertIsDisplayed()
        composeRule.onNodeWithText("2 h").assertIsDisplayed()

        composeRule.onNodeWithTag("calendar_balance_card").performClick()
        composeRule.onNodeWithText("10 balances").assertIsDisplayed()
        composeRule.onNodeWithText("Paid parental leave").assertIsDisplayed()
        composeRule.onNodeWithText("60 h").assertIsDisplayed()
        composeRule.onNodeWithText("FMLA remaining").assertIsDisplayed()
        composeRule.onNodeWithText("120 h").assertIsDisplayed()
    }

    @Test
    fun unavailableBalanceShowsRetryAndRecovers() {
        setAppContent()
        waitForBalanceCard()
        composeRule.onNodeWithTag("calendar_balance_card").performClick()
        bridge.failBalances = true
        composeRule.onNodeWithText("Choose date").performClick()
        waitForPickerGrid()
        // Pick a day and confirm; the read fails and the panel shows the
        // unavailable state for the requested date.
        composeRule.onNodeWithTag("balance_picker_day_2026-01-22").performClick()
        composeRule.onNodeWithText("Show January 22").performClick()
        composeRule.onNodeWithText("Balance unavailable").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Use or Lose could not read this date's leave year.",
        ).assertIsDisplayed()

        bridge.failBalances = false
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText("Annual leave").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("90 h").assertIsDisplayed()
    }

    @Test
    fun gridShowsOverflowCountForThreeEntriesAndDayRouteRevealsThem() {
        bridge.compEntry = true
        setAppContent()
        waitForBalanceCard()
        composeRule.onNodeWithText("+1").assertIsDisplayed()
        composeRule.onNodeWithTag("calendar_day_2026-01-24").performClick()
        composeRule.onNodeWithText("Edit day").assertIsDisplayed()
        composeRule.onNodeWithText("6 h earned").assertIsDisplayed()
        composeRule.onNodeWithText("4 h earned").assertIsDisplayed()
        composeRule.onNodeWithText("1 h earned").assertIsDisplayed()
    }
}
