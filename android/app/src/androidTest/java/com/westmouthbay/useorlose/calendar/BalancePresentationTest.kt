package com.westmouthbay.useorlose.calendar

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.westmouthbay.useorlose.core.ParentalSummary
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Pure presentation rules from chapter 01-02, on the JVM. */
@RunWith(AndroidJUnit4::class)
class BalancePresentationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val tenBalances = mapOf(
        "annual" to 90.0,
        "sick" to 20.0,
        "comp" to 8.0,
        "credit" to 4.0,
        "travel_comp" to 6.0,
        "time_off_award" to 12.0,
        "restored_annual" to 10.0,
        "religious_comp" to 2.0,
        "admin" to 0.0,
        "lwop" to 0.0,
        "overtime" to 0.0,
    )

    @Test
    fun visibleBalancesOrdersCategoriesAndKeepsAnnualAndSick() {
        val rows = BalancePresentation.visibleBalances(tenBalances, parental = null)
        assertEquals(
            listOf(
                "annual", "sick", "comp", "credit", "travel_comp", "time_off_award",
                "restored_annual", "religious_comp",
            ),
            rows.map { it.category },
        )
        assertEquals(BalancePresentation.BalanceValue.Hours(90.0), rows[0].value)
    }

    @Test
    fun parentalAddsPplAndFmlaAfterReligiousComp() {
        val rows = BalancePresentation.visibleBalances(
            tenBalances,
            ParentalSummary(pplAvailableHours = 60.0, fmlaRemainingHours = 120.0),
        )
        assertEquals(10, rows.size)
        assertEquals("ppl", rows[8].category)
        assertEquals("fmla", rows[9].category)
        assertEquals(BalancePresentation.BalanceValue.Hours(60.0), rows[8].value)
        assertEquals(BalancePresentation.BalanceValue.Hours(120.0), rows[9].value)
    }

    @Test
    fun exhaustedPplGrantKeepsItsZeroAndMissingValuesAreUnavailable() {
        val rows = BalancePresentation.visibleBalances(
            tenBalances,
            ParentalSummary(pplAvailableHours = 0.0, fmlaRemainingHours = null),
        )
        assertEquals(BalancePresentation.BalanceValue.Hours(0.0), rows[8].value)
        assertEquals(BalancePresentation.BalanceValue.Unavailable, rows[9].value)
        assertEquals("Unavailable", BalancePresentation.valueText(context, rows[9].value))
    }

    @Test
    fun previewUsesFourColumnsAtReferenceSize() {
        val rows = BalancePresentation.visibleBalances(
            tenBalances,
            ParentalSummary(60.0, 120.0),
        )
        val columns = BalancePresentation.previewColumns(360.dp, fontScale = 1f)
        val (preview, hidden) = BalancePresentation.preview(rows, columns)
        assertEquals(8, preview.size)
        assertEquals(2, hidden)
        assertEquals(8, columns * 2)
    }

    @Test
    fun previewUsesTwoColumnsForLargeTextOrNarrowScreens() {
        val rows = BalancePresentation.visibleBalances(
            tenBalances,
            ParentalSummary(60.0, 120.0),
        )
        val (previewLarge, hiddenLarge) = BalancePresentation.preview(
            rows,
            BalancePresentation.previewColumns(360.dp, fontScale = 2f),
        )
        assertEquals(4, previewLarge.size)
        assertEquals(6, hiddenLarge)
        val (previewNarrow, hiddenNarrow) = BalancePresentation.preview(
            rows,
            BalancePresentation.previewColumns(280.dp, fontScale = 1f),
        )
        assertEquals(4, previewNarrow.size)
        assertEquals(6, hiddenNarrow)
    }

    @Test
    fun noHiddenValuesWhenEverythingFits() {
        val rows = BalancePresentation.visibleBalances(
            mapOf("annual" to 90.0, "sick" to 20.0),
            parental = null,
        )
        val (preview, hidden) = BalancePresentation.preview(rows, 4)
        assertEquals(rows.size, preview.size)
        assertEquals(0, hidden)
    }

    @Test
    fun hourAndLineFormattingMatchesTheMockups() {
        assertEquals("90", BalancePresentation.formatHours(90.0))
        assertEquals("2.25", BalancePresentation.formatHours(2.25))
        assertEquals("A \u22128", BalancePresentation.formatDisplayLine("A -8.0"))
        assertEquals("S +4.5", BalancePresentation.formatDisplayLine("S +4.5"))
    }
}
