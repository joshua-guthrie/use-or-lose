package com.westmouthbay.useorlose.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Parser tests for the typed facade models. The JSON snippets replicate the
 * field shapes of the pinned goldens under `android/qa/golden/calendar-2026/`;
 * the device CoreBridgeTest compares the real goldens end to end.
 */
class CoreModelsTest {

    @Test
    fun datedBalanceParsesBalancesWarningsAndNullParental() {
        val json = JSONObject(
            """
            {
              "year": 2026, "as_of": "2026-01-16", "projected": false, "project_to": null,
              "balances": {"annual": 90.0, "sick": 20.0, "admin": 0.0},
              "parental": null, "warnings": []
            }
            """.trimIndent(),
        )
        val result = DatedBalanceResult.fromJson(json)
        assertEquals(2026, result.year)
        assertEquals(LocalDate.parse("2026-01-16"), result.asOf)
        assertFalse(result.projected)
        assertNull(result.projectTo)
        assertEquals(90.0, result.balances["annual"]!!, 0.0)
        assertEquals(20.0, result.balances["sick"]!!, 0.0)
        assertNull(result.parental)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun datedBalanceMapsParentalSummary() {
        val json = JSONObject(
            """
            {
              "year": 2026, "as_of": "2026-01-16", "projected": true, "project_to": "2027-01-09",
              "balances": {"annual": 90.0},
              "parental": {
                "as_of": "2026-01-16",
                "ppl_available_hours": 60.0,
                "ppl_usable_hours": 60.0,
                "ppl_windows": [],
                "warnings": [],
                "fmla": {"remaining_hours": 120.0, "known_remaining_hours": 120.0}
              },
              "warnings": ["warning-a"]
            }
            """.trimIndent(),
        )
        val result = DatedBalanceResult.fromJson(json)
        assertTrue(result.projected)
        assertEquals("2027-01-09", result.projectTo)
        assertEquals(60.0, result.parental?.pplAvailableHours!!, 0.0)
        assertEquals(120.0, result.parental?.fmlaRemainingHours!!, 0.0)
        assertEquals(listOf("warning-a"), result.warnings)
    }

    @Test
    fun monthViewParsesDaysEntriesAndPeriodRows() {
        val json = JSONObject(
            """
            {
              "year": 2026, "month": 1, "today": "2026-01-16",
              "month_start": "2026-01-01", "month_end": "2026-01-31",
              "calendar_start": "2025-12-28", "calendar_end": "2026-01-31",
              "days": [
                {
                  "date": "2025-12-28", "in_display_month": false, "holiday_name": null,
                  "is_today": false, "is_payday": false, "is_pay_period_end": false,
                  "entries": [], "display_lines": []
                },
                {
                  "date": "2026-01-16", "in_display_month": true, "holiday_name": null,
                  "is_today": true, "is_payday": false, "is_pay_period_end": false,
                  "entries": [
                    {
                      "id": "20260116-001", "category": "annual", "direction": "used",
                      "hours": 2.0, "status": "approved", "source": "manual", "description": ""
                    },
                    {
                      "id": null, "category": "sick", "direction": null, "hours": 1.0,
                      "status": null, "source": null, "description": "odd row"
                    }
                  ],
                  "display_lines": ["A -2.0"]
                }
              ],
              "pay_periods": [
                {
                  "number": 1, "start": "2026-01-11", "end": "2026-01-24",
                  "pay_date": "2026-01-30", "touches_display_month": true,
                  "totals": {
                    "annual": {"earned": 6.0, "used": 14.0, "worked": 0.0, "net": -8.0}
                  },
                  "ending_balances": {"annual": 92.0, "sick": 24.0}
                }
              ],
              "pay_dates": ["2026-01-30"], "pay_period_end_dates": ["2026-01-24"],
              "warnings": []
            }
            """.trimIndent(),
        )
        val result = MonthViewResult.fromJson(json)
        assertEquals(2, result.days.size)
        val day16 = result.days[1]
        assertTrue(day16.isToday)
        assertTrue(day16.inDisplayMonth)
        assertEquals("A -2.0", day16.displayLines.single())
        assertEquals(2, day16.entries.size)
        assertEquals("20260116-001", day16.entries[0].id)
        assertNull(day16.entries[1].id)
        assertEquals(1.0, day16.entries[1].hours, 0.0)
        val period = result.payPeriods.single()
        assertEquals(1, period.number)
        assertEquals(LocalDate.parse("2026-01-30"), period.payDate)
        assertEquals(6.0, period.totals.getValue("annual").earned, 0.0)
        assertEquals(14.0, period.totals.getValue("annual").used, 0.0)
        assertEquals(92.0, period.endingBalances.getValue("annual"), 0.0)
        assertEquals(setOf(LocalDate.parse("2026-01-30")), result.payDates)
        assertEquals(setOf(LocalDate.parse("2026-01-24")), result.payPeriodEndDates)
    }

    @Test
    fun payPeriodDetailIgnoresNestedPayPeriodKeyInActivity() {
        val json = JSONObject(
            """
            {
              "year": 2026, "date": "2026-01-24",
              "pay_period": {
                "pay_period_number": 1, "start_date": "2026-01-11",
                "end_date": "2026-01-24", "pay_date": "2026-01-30",
                "accrual_date": "2026-01-24"
              },
              "activity": {
                "earned": {"annual": 6.0, "sick": 4.0},
                "used": {"annual": 14.0, "sick": 0.0},
                "worked": {"annual": 0.0, "sick": 0.0},
                "net": {"annual": -8.0, "sick": 4.0},
                "pay_period": {
                  "pay_period_number": 1, "start_date": "2026-01-11",
                  "end_date": "2026-01-24", "pay_date": "2026-01-30",
                  "accrual_date": "2026-01-24"
                }
              },
              "ending_balances": {"annual": 92.0, "sick": 24.0},
              "warnings": []
            }
            """.trimIndent(),
        )
        val result = PayPeriodDetailResult.fromJson(json)
        assertEquals(1, result.period?.number)
        assertEquals(LocalDate.parse("2026-01-11"), result.period?.startDate)
        assertEquals(LocalDate.parse("2026-01-30"), result.period?.payDate)
        assertEquals(setOf("annual", "sick"), result.activity.keys)
        assertEquals(6.0, result.activity.getValue("annual").earned, 0.0)
        assertEquals(14.0, result.activity.getValue("annual").used, 0.0)
        assertEquals(0.0, result.activity.getValue("sick").used, 0.0)
        assertEquals(92.0, result.endingBalances.getValue("annual"), 0.0)
    }

    @Test
    fun dayActivityParsesWithoutWorkedSection() {
        val json = JSONObject(
            """
            {
              "year": 2026, "date": "2026-01-16", "has_activity": true,
              "activity": {
                "earned": {"annual": 0.0},
                "used": {"annual": 2.0},
                "net": {"annual": -2.0}
              }
            }
            """.trimIndent(),
        )
        val result = DayActivityResult.fromJson(json)
        assertTrue(result.hasActivity)
        assertEquals(2.0, result.activity.getValue("annual").used, 0.0)
        assertEquals(0.0, result.activity.getValue("annual").worked, 0.0)
    }

    @Test
    fun errorEnvelopeMapsStructuredCode() {
        val error = CoreError.fromErrorObject(
            JSONObject(
                """
                {"code": "LEAVE_YEAR_NOT_FOUND", "field": "as_of",
                 "message": "No leave year covers 2019-01-01."}
                """.trimIndent(),
            ),
        )
        assertTrue(error is CoreError.Read)
        assertEquals(CoreError.LEAVE_YEAR_NOT_FOUND, error.code)
        assertEquals("as_of", error.field)
        assertEquals("No leave year covers 2019-01-01.", error.message)
    }

    @Test
    fun missingErrorObjectStillProducesAReadError() {
        val error = CoreError.fromErrorObject(JSONObject())
        assertTrue(error is CoreError.Read)
        assertEquals("UNKNOWN", error.code)
        assertNull(error.field)
    }
}
