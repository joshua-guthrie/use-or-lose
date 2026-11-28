package com.westmouthbay.useorlose.calendar

import com.westmouthbay.useorlose.core.CoreError
import java.time.LocalDate

/** The editor modes: replace one existing group or add a single entry. */
enum class EditMode {
    EDIT,
    ADD,
}

enum class DayEditStep {
    FORM,
    REVIEW,
}

/** The originating edit context preserved across a setup handoff. */
data class EditorDraft(
    val date: LocalDate,
    val mode: EditMode,
    val category: String,
    val direction: String,
    val hoursText: String,
    val comment: String,
)

/** One selectable group on the tapped date (category + direction). */
data class EditGroup(
    val category: String,
    val direction: String,
    val totalHours: Double,
    val entryCount: Int,
)

data class DayEditUiState(
    val date: LocalDate? = null,
    /** The containing year resolved from the store, or null when uncovered. */
    val year: Int? = null,
    val mode: EditMode = EditMode.EDIT,
    val step: DayEditStep = DayEditStep.FORM,
    val groups: List<EditGroup> = emptyList(),
    val selectedCategory: String? = null,
    val selectedDirection: String? = null,
    val hoursText: String = "",
    val comment: String = "",
    /** Other activity on the date that the edit leaves untouched. */
    val untouchedRows: List<String> = emptyList(),
    /** The current group's rows shown by "View the N current entries". */
    val entryDetails: List<String> = emptyList(),
    val incrementHours: Double = 0.25,
    val defaultStatus: String = "planned",
    val preview: com.westmouthbay.useorlose.core.EditPreview? = null,
    val previewedFiles: Map<String, String>? = null,
    /** The store generation at preview time; saves must match it. */
    val previewedBase: String? = null,
    val loading: Boolean = false,
    val error: CoreError? = null,
    val published: Boolean = false,
    val stale: Boolean = false,
) {
    /** True when the tapped date lies outside every stored leave year. */
    val yearMissing: Boolean
        get() = year == null
}
