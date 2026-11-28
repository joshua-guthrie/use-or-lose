package com.westmouthbay.useorlose.calendar

import android.content.Context
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.westmouthbay.useorlose.R
import com.westmouthbay.useorlose.core.DayEntry
import com.westmouthbay.useorlose.core.ParentalSummary

/**
 * Pure presentation helpers for balance values, category labels, the card
 * preview and the full list. I05-03 owns the hidden-count hint, the
 * capacity-based preview and the PPL/FMLA rows; the label maps landed with
 * I05-02.
 */
object BalancePresentation {

    /** Desktop category order for the balance panel (annual and sick first). */
    val CATEGORY_ORDER = listOf(
        "annual", "sick", "comp", "credit", "travel_comp", "time_off_award",
        "restored_annual", "religious_comp", "ppl", "fmla",
    )

    sealed interface BalanceValue {
        data class Hours(val value: Double) : BalanceValue
        data object Unavailable : BalanceValue
    }

    data class BalanceRow(val category: String, val value: BalanceValue)

    /**
     * The rows shown for a dated result: annual and sick are always reported
     * (even at zero); other supported categories appear when nonzero; PPL and
     * FMLA remaining come from the parental summary and stay when reported
     * (an exhausted grant keeps its zero, a missing value is Unavailable);
     * remaining nonzero categories follow in stable key order.
     */
    fun visibleBalances(
        balances: Map<String, Double>,
        parental: ParentalSummary?,
    ): List<BalanceRow> {
        val rows = mutableListOf<BalanceRow>()
        for (key in CATEGORY_ORDER) {
            if (key == "ppl" || key == "fmla") {
                continue
            }
            val value = balances[key] ?: continue
            if (key != "annual" && key != "sick" && value == 0.0) {
                continue
            }
            rows += BalanceRow(key, BalanceValue.Hours(value))
        }
        if (parental != null) {
            rows += BalanceRow(
                "ppl",
                parental.pplAvailableHours?.let { BalanceValue.Hours(it) }
                    ?: BalanceValue.Unavailable,
            )
            rows += BalanceRow(
                "fmla",
                parental.fmlaRemainingHours?.let { BalanceValue.Hours(it) }
                    ?: BalanceValue.Unavailable,
            )
        }
        for (key in balances.keys.filter { it !in CATEGORY_ORDER }.sorted()) {
            val value = balances[key] ?: continue
            if (value != 0.0) {
                rows += BalanceRow(key, BalanceValue.Hours(value))
            }
        }
        return rows
    }

    /**
     * The preview columns for the balance card: four columns at the
     * reference width and text scale (two rows of four), two columns on
     * narrow screens or large text, matching the mockup boards.
     */
    fun previewColumns(maxWidth: Dp, fontScale: Float): Int =
        if (maxWidth >= 320.dp && fontScale < 1.3f) 4 else 2

    /** The card preview rows and how many reported balances stay hidden. */
    fun preview(rows: List<BalanceRow>, columns: Int): Pair<List<BalanceRow>, Int> {
        val visibleCount = columns * 2
        val preview = rows.take(visibleCount)
        return preview to (rows.size - preview.size).coerceAtLeast(0)
    }

    fun valueText(context: Context, value: BalanceValue): String = when (value) {
        is BalanceValue.Hours -> context.getString(R.string.hours_value, formatHours(value.value))
        BalanceValue.Unavailable -> context.getString(R.string.unavailable)
    }

    fun shortLabel(context: Context, category: String): String {
        val res = when (category) {
            "annual" -> R.string.cat_annual_short
            "sick" -> R.string.cat_sick_short
            "comp" -> R.string.cat_comp_short
            "credit" -> R.string.cat_credit_short
            "travel_comp" -> R.string.cat_travel_comp_short
            "time_off_award" -> R.string.cat_time_off_award_short
            "restored_annual" -> R.string.cat_restored_annual_short
            "religious_comp" -> R.string.cat_religious_comp_short
            "ppl" -> R.string.cat_ppl_short
            "fmla" -> R.string.cat_fmla_short
            else -> 0
        }
        return if (res != 0) context.getString(res) else fallbackLabel(category)
    }

    fun fullLabel(context: Context, category: String): String {
        val res = when (category) {
            "annual" -> R.string.cat_annual_full
            "sick" -> R.string.cat_sick_full
            "comp" -> R.string.cat_comp_full
            "credit" -> R.string.cat_credit_full
            "travel_comp" -> R.string.cat_travel_comp_full
            "time_off_award" -> R.string.cat_time_off_award_full
            "restored_annual" -> R.string.cat_restored_annual_full
            "religious_comp" -> R.string.cat_religious_comp_full
            "ppl" -> R.string.cat_ppl_full
            "fmla" -> R.string.cat_fmla_full
            "overtime" -> R.string.cat_overtime_full
            "admin" -> R.string.cat_admin_full
            "lwop" -> R.string.cat_lwop_full
            "military" -> R.string.cat_military_full
            "court" -> R.string.cat_court_full
            "excused" -> R.string.cat_excused_full
            "holiday" -> R.string.cat_holiday_full
            "flex" -> R.string.cat_flex_full
            "other" -> R.string.cat_other_full
            else -> 0
        }
        return if (res != 0) context.getString(res) else fallbackLabel(category)
    }

    private fun fallbackLabel(category: String): String =
        category.split('_').joinToString(" ") { part ->
            part.replaceFirstChar { it.uppercase() }
        }

    /** "90.0" → "90", "2.25" → "2.25". */
    /** The full direction label for the editor ("Use", "Earn", "Worked"). */
    fun directionLabel(context: Context, direction: String): String = when (direction) {
        "used" -> context.getString(R.string.direction_use)
        "earned" -> context.getString(R.string.direction_earn)
        "worked" -> context.getString(R.string.direction_worked)
        "paid_out" -> context.getString(R.string.direction_paid_out)
        else -> direction
    }

    fun formatHours(hours: Double): String =
        if (hours % 1.0 == 0.0) hours.toLong().toString() else hours.toString()

    /** "A -2.0" → "A −2", matching the mockup's grid lines. */
    fun formatDisplayLine(line: String): String =
        line.replace(Regex("\\.0$"), "").replace("-", "\u2212")

    /** The day-route value for one entry: "2 h used", "6 h earned". */
    fun entryValueText(context: Context, entry: DayEntry): String = when (entry.direction) {
        "earned" -> context.getString(R.string.hours_earned, formatHours(entry.hours))
        "used" -> context.getString(R.string.hours_used, formatHours(entry.hours))
        else -> context.getString(
            R.string.hours_direction,
            formatHours(entry.hours),
            entry.direction ?: "",
        )
    }
}
