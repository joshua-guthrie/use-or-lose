package com.westmouthbay.useorlose.calendar

import com.westmouthbay.useorlose.core.CoreError
import com.westmouthbay.useorlose.core.CreationPreview

/** The setup screens, in order, per the annotated boards. */
enum class SetupStep {
    METHOD,
    START_FORM,
    TODAY_FORM,
    REVIEW,
}

enum class SetupKind {
    MANUAL,
    TODAY,
}

/** Values proposed by the caller (Calendar metadata or defaults). */
data class SetupProposal(
    val year: Int,
    val leaveYearStart: String,
    val payPeriods: Int,
    val annualAccrual: String,
    val sickAccrual: String,
)

data class SetupUiState(
    val step: SetupStep = SetupStep.METHOD,
    val year: String = "",
    val leaveYearStart: String = "",
    val payPeriods: Int = 26,
    val annualAccrual: String = "",
    val sickAccrual: String = "",
    val openings: Map<String, String> = emptyMap(),
    val todayTargets: Map<String, String> = emptyMap(),
    val showOtherOpenings: Boolean = false,
    val showOtherTargets: Boolean = false,
    val preview: CreationPreview? = null,
    val previewedFiles: Map<String, String>? = null,
    val previewKind: SetupKind? = null,
    val previewedToday: String? = null,
    /** The store generation at preview time; confirms must match it. */
    val previewedBase: String? = null,
    /** The injected local day for the Today choice and its review. */
    val todayIso: String? = null,
    val loading: Boolean = false,
    val error: CoreError? = null,
    val published: Boolean = false,
    val stale: Boolean = false,
    /** The originating context: an uncovered balance date, or null for welcome. */
    val resumeDate: String? = null,
) {
    /** The balance categories offered under "Other starting balances". */
    companion object {
        val OTHER_CATEGORIES = listOf(
            "comp", "credit", "travel_comp", "time_off_award", "religious_comp", "restored_annual",
        )
    }
}
