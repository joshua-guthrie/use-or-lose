package com.westmouthbay.fedleave.calendar

import android.content.Context
import com.westmouthbay.fedleave.R
import com.westmouthbay.fedleave.core.DayEntry

/**
 * Pure presentation helpers for balance values, category labels and grid
 * display lines. I05-02 pulled this file forward from the I05-03 task list
 * because the card and the day route need the shared label maps now; I05-03
 * extends it with the hidden-count hint, capacity-based preview and the full
 * list rules.
 */
object BalancePresentation {

    /** Desktop category order for the balance panel (annual and sick first). */
    val CATEGORY_ORDER = listOf(
        "annual", "sick", "comp", "credit", "travel_comp", "time_off_award",
        "restored_annual", "religious_comp", "ppl", "fmla",
    )

    data class BalanceRow(val category: String, val hours: Double)

    /**
     * The rows shown for a dated result: annual and sick are always reported
     * (even at zero); other supported categories appear when nonzero, in the
     * desktop order, followed by remaining nonzero categories by stable key.
     */
    fun visibleBalances(balances: Map<String, Double>): List<BalanceRow> {
        val ordered = CATEGORY_ORDER.mapNotNull { key ->
            balances[key]?.let { BalanceRow(key, it) }
        }.filter { it.category == "annual" || it.category == "sick" || it.hours != 0.0 }
        val others = balances.keys
            .filter { it !in CATEGORY_ORDER }
            .sorted()
            .mapNotNull { key -> balances[key]?.let { BalanceRow(key, it) } }
            .filter { it.hours != 0.0 }
        return ordered + others
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
