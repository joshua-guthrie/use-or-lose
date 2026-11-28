package com.westmouthbay.useorlose.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.westmouthbay.useorlose.core.AddEntryRequest
import com.westmouthbay.useorlose.core.AppDependencies
import com.westmouthbay.useorlose.core.CoreError
import com.westmouthbay.useorlose.core.EditDayRequest
import com.westmouthbay.useorlose.core.EditPreview
import com.westmouthbay.useorlose.core.EditingBridge
import com.westmouthbay.useorlose.core.EditingRead
import com.westmouthbay.useorlose.storage.OpenResult
import com.westmouthbay.useorlose.storage.PublishResult
import com.westmouthbay.useorlose.storage.StoreCandidate
import com.westmouthbay.useorlose.storage.StoreCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate

/**
 * Owns the day editor: the group selector, the hours/comment form, the
 * preview with the exact replaced and created IDs, and the single confirmed
 * publish through the I04 writer. Preview, cancel and back write nothing;
 * a stale base forces a fresh preview and a new confirmation. When the
 * tapped date is uncovered, the editor hands off to setup and resumes with
 * its draft afterwards (T-CAL-YEAR-02).
 */
class DayEditViewModel(
    private val editing: EditingRead,
    private val store: StoreCoordinator,
    private val providedScope: CoroutineScope? = null,
) : ViewModel() {

    private val scope: CoroutineScope by lazy { providedScope ?: viewModelScope }

    private val _state = MutableStateFlow(DayEditUiState())
    val state: StateFlow<DayEditUiState> = _state.asStateFlow()

    private val trackedJobs = java.util.Collections.synchronizedList(mutableListOf<Job>())

    private fun launchRead(block: suspend () -> Unit) {
        val job = scope.launch { block() }
        trackedJobs += job
        job.invokeOnCompletion { trackedJobs.remove(job) }
    }

    /** Test-only: wait for launched work to finish. */
    suspend fun awaitIdle() {
        while (true) {
            val pending = trackedJobs.toList()
            if (pending.isEmpty()) {
                return
            }
            pending.joinAll()
        }
    }

    private suspend fun snapshotOrNull(): com.westmouthbay.useorlose.storage.StorageSnapshot? =
        (store.open() as? OpenResult.Ready)?.snapshot

    /**
     * Enter the editor for a date. When [draft] is present (a resume after
     * setup), the draft values are restored; otherwise the first existing
     * group is preselected.
     */
    fun begin(date: LocalDate, draft: EditorDraft? = null, mode: EditMode? = null) {
        _state.update {
            DayEditUiState(
                date = date,
                mode = mode ?: draft?.mode ?: EditMode.EDIT,
                selectedCategory = draft?.category?.takeIf { it.isNotBlank() },
                selectedDirection = draft?.direction?.takeIf { it.isNotBlank() },
                hoursText = draft?.hoursText ?: "",
                comment = draft?.comment ?: "",
            )
        }
        launchRead {
            val snapshot = snapshotOrNull()
            if (snapshot == null) {
                _state.update {
                    it.copy(
                        error = CoreError.Read(
                            "STORE_UNAVAILABLE",
                            "store",
                            "The existing data needs review before a day can be edited.",
                        ),
                    )
                }
                return@launchRead
            }
            val years = parseYearMetadata(snapshot)
            val year = years.values
                .firstOrNull { !date.isBefore(it.start) && !date.isAfter(it.end) }
                ?.year
            val increment = runCatching {
                snapshot.files["config.json"]?.let { JSONObject(it) }
                    ?.optJSONObject("defaults")?.optDouble("time_increment_hours")
            }.getOrNull() ?: 0.25
            val groups = when (year) {
                null -> emptyList()
                else -> buildGroups(snapshot, year, date)
            }
            val untouched = when (year) {
                null -> emptyList()
                else -> untouchedRowsFor(
                    snapshot,
                    year,
                    date,
                    excludeCategory = draft?.category?.takeIf { it.isNotBlank() },
                    excludeDirection = draft?.direction?.takeIf { it.isNotBlank() },
                )
            }
            _state.update {
                it.copy(
                    year = year,
                    groups = groups,
                    untouchedRows = untouched,
                    incrementHours = if (increment > 0) increment else 0.25,
                )
            }
        }
    }

    /** Show the current group's rows (a read-only list; I09 links here later). */
    fun onViewEntries() {
        val state = _state.value
        val date = state.date ?: return
        val year = state.year ?: return
        val category = state.selectedCategory ?: return
        val direction = state.selectedDirection ?: return
        launchRead {
            val snapshot = snapshotOrNull() ?: return@launchRead
            val ledger = runCatching {
                JSONObject(snapshot.files.getValue("leave_years/$year.json"))
            }.getOrNull() ?: return@launchRead
            val rows = ledger.optJSONArray("transactions") ?: return@launchRead
            val names = (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                if (row.optString("date") != date.toString()) return@mapNotNull null
                if (row.optString("category") != category || row.optString("direction") != direction) return@mapNotNull null
                "${row.optString("id")} \u00b7 ${row.optDouble("hours", 0.0)} h \u00b7 ${row.optString("status")}"
            }
            _state.update { it.copy(entryDetails = names) }
        }
    }

    /** The editable ordinary groups on one date, from the stored ledger. */
    private fun buildGroups(
        snapshot: com.westmouthbay.useorlose.storage.StorageSnapshot,
        year: Int,
        date: LocalDate,
    ): List<EditGroup> {
        val ledger = runCatching {
            JSONObject(snapshot.files.getValue("leave_years/$year.json"))
        }.getOrNull() ?: return emptyList()
        val rows = ledger.optJSONArray("transactions") ?: return emptyList()
        val totals = mutableMapOf<Pair<String, String>, Pair<Double, Int>>()
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            if (row.optString("date") != date.toString()) continue
            if (row.optBoolean("carryover_generated", false)) continue
            if (row.optString("source") in setOf("auto_accrual", "forced-balance", "expiration-engine", "expiration-rollover")) {
                continue
            }
            val category = row.optString("category")
            val direction = row.optString("direction")
            val key = category to direction
            val (hours, count) = totals[key] ?: (0.0 to 0)
            totals[key] = (hours + row.optDouble("hours", 0.0)) to (count + 1)
        }
        return totals.map { (key, value) ->
            EditGroup(
                category = key.first,
                direction = key.second,
                totalHours = value.first,
                entryCount = value.second,
            )
        }.sortedWith(compareBy({ it.category }, { it.direction }))
    }

    fun onSelectGroup(category: String, direction: String) {
        _state.update {
            it.copy(
                selectedCategory = category,
                selectedDirection = direction,
                step = DayEditStep.FORM,
                preview = null,
                previewedFiles = null,
                error = null,
                stale = false,
            )
        }
        val state = _state.value
        val date = state.date ?: return
        val year = state.year ?: return
        launchRead {
            val snapshot = snapshotOrNull() ?: return@launchRead
            _state.update {
                it.copy(
                    untouchedRows = untouchedRowsFor(snapshot, year, date, category, direction),
                )
            }
        }
    }

    fun onHoursChanged(value: String) = _state.update { it.copy(hoursText = value) }

    fun onCommentChanged(value: String) = _state.update { it.copy(comment = value) }

    fun onBackToDraft() = _state.update {
        it.copy(step = DayEditStep.FORM, preview = null, previewedFiles = null, error = null, stale = false)
    }

    /** The untouched activity listed on the review (per category+direction). */
    private fun untouchedRowsFor(
        snapshot: com.westmouthbay.useorlose.storage.StorageSnapshot,
        year: Int,
        date: LocalDate,
        excludeCategory: String?,
        excludeDirection: String?,
    ): List<String> {
        val ledger = runCatching {
            JSONObject(snapshot.files.getValue("leave_years/$year.json"))
        }.getOrNull() ?: return emptyList()
        val rows = ledger.optJSONArray("transactions") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            if (row.optString("date") != date.toString()) return@mapNotNull null
            val category = row.optString("category")
            val direction = row.optString("direction")
            if (category == excludeCategory && direction == excludeDirection) return@mapNotNull null
            val hours = row.optDouble("hours", 0.0)
            "${category}|${direction}|$hours"
        }
    }

    fun onPreview() {
        val state = _state.value
        val date = state.date ?: return
        val year = state.year ?: return
        val hours = state.hoursText.trim().toDoubleOrNull()
        if (state.mode == EditMode.EDIT) {
            val category = state.selectedCategory ?: return
            val direction = state.selectedDirection ?: return
            if (hours == null || !hours.isFinite() || hours < 0) {
                _state.update {
                    it.copy(error = CoreError.Read("INVALID_HOURS", "hours", "Enter valid nonnegative hours."))
                }
                return
            }
            _state.update { it.copy(loading = true, error = null) }
            launchRead {
                val snapshot = snapshotOrNull()
                if (snapshot == null) {
                    _state.update {
                        it.copy(
                            loading = false,
                            error = CoreError.Read("STORE_UNAVAILABLE", "store", "The store cannot be read."),
                        )
                    }
                    return@launchRead
                }
                val ledger = snapshot.files["leave_years/$year.json"]
                    ?: run {
                        _state.update {
                            it.copy(
                                loading = false,
                                error = CoreError.Read("LEAVE_YEAR_NOT_FOUND", "year", "The leave year is missing."),
                            )
                        }
                        return@launchRead
                    }
                val result = runCatching {
                    editing.previewEditDay(
                        EditDayRequest(
                            year = year,
                            date = date,
                            category = category,
                            direction = direction,
                            hours = hours,
                            comment = state.comment.takeIf { it.isNotBlank() },
                            ledgerJson = ledger,
                        ),
                    )
                }
                result.fold(
                    onSuccess = { editResult ->
                        when (editResult.preview) {
                            is EditPreview.Mixed -> _state.update {
                                it.copy(
                                    loading = false,
                                    step = DayEditStep.REVIEW,
                                    preview = editResult.preview,
                                    previewedFiles = null,
                                    previewedBase = null,
                                    error = null,
                                )
                            }
                            else -> _state.update {
                                it.copy(
                                    step = DayEditStep.REVIEW,
                                    preview = editResult.preview,
                                    previewedFiles = editResult.files,
                                    previewedBase = snapshot.generationId,
                                    untouchedRows = untouchedRowsFor(snapshot, year, date, category, direction),
                                    loading = false,
                                    error = null,
                                )
                            }
                        }
                    },
                    onFailure = { error ->
                        _state.update { it.copy(loading = false, error = error.toCoreError()) }
                    },
                )
            }
        } else {
            val category = state.selectedCategory ?: return
            val direction = state.selectedDirection ?: return
            if (hours == null || !hours.isFinite() || hours <= 0) {
                _state.update {
                    it.copy(error = CoreError.Read("INVALID_HOURS", "hours", "Enter positive hours."))
                }
                return
            }
            _state.update { it.copy(loading = true, error = null) }
            launchRead {
                val snapshot = snapshotOrNull()
                if (snapshot == null) {
                    _state.update {
                        it.copy(
                            loading = false,
                            error = CoreError.Read("STORE_UNAVAILABLE", "store", "The store cannot be read."),
                        )
                    }
                    return@launchRead
                }
                val ledger = snapshot.files["leave_years/$year.json"]
                    ?: run {
                        _state.update {
                            it.copy(
                                loading = false,
                                error = CoreError.Read("LEAVE_YEAR_NOT_FOUND", "year", "The leave year is missing."),
                            )
                        }
                        return@launchRead
                    }
                val result = runCatching {
                    editing.previewAddEntry(
                        AddEntryRequest(
                            year = year,
                            date = date,
                            category = category,
                            direction = direction,
                            hours = hours,
                            comment = state.comment,
                            status = state.defaultStatus,
                            ledgerJson = ledger,
                        ),
                    )
                }
                result.fold(
                    onSuccess = { editResult ->
                        _state.update {
                            it.copy(
                                step = DayEditStep.REVIEW,
                                preview = editResult.preview,
                                previewedFiles = editResult.files,
                                previewedBase = snapshot.generationId,
                                loading = false,
                                error = null,
                            )
                        }
                    },
                    onFailure = { error ->
                        _state.update { it.copy(loading = false, error = error.toCoreError()) }
                    },
                )
            }
        }
    }

    fun onConfirm() {
        val state = _state.value
        val files = state.previewedFiles
        val year = state.year
        if (files == null || year == null || state.published) {
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        launchRead {
            val opened = store.open()
            if (opened is OpenResult.Recoverable) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = CoreError.Read(
                            "STORE_UNAVAILABLE",
                            "store",
                            "The existing data needs review before this day can be saved.",
                        ),
                    )
                }
                return@launchRead
            }
            val snapshot = opened as? OpenResult.Ready
            if (snapshot == null) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = CoreError.Read("STORE_UNAVAILABLE", "store", "The store cannot be read."),
                    )
                }
                return@launchRead
            }
            val candidate = StoreCandidate()
            snapshot.snapshot.files.forEach { (name, text) -> candidate.put(name, text) }
            for ((name, text) in files) {
                candidate.put(name, text)
            }
            val published = store.publish(
                candidate,
                expectedBase = state.previewedBase,
                fingerprint = "day-edit:$year",
            )
            when (published) {
                is PublishResult.Published -> _state.update {
                    it.copy(loading = false, published = true, error = null, stale = false)
                }
                PublishResult.StaleBase -> _state.update {
                    it.copy(loading = false, stale = true)
                }
                is PublishResult.Rejected -> _state.update {
                    it.copy(
                        loading = false,
                        error = CoreError.Read(
                            "REJECTED",
                            "store",
                            published.issues.joinToString("; ") { it.message },
                        ),
                    )
                }
                is PublishResult.Failed -> _state.update {
                    it.copy(
                        loading = false,
                        error = CoreError.Unexpected("Store publish failed: ${published.reason}"),
                    )
                }
            }
        }
    }

    /** The draft handed to setup so the editor can resume after creation. */
    fun draft(): EditorDraft? {
        val state = _state.value
        val date = state.date ?: return null
        return EditorDraft(
            date = date,
            mode = state.mode,
            category = state.selectedCategory ?: "",
            direction = state.selectedDirection ?: "",
            hoursText = state.hoursText,
            comment = state.comment,
        )
    }

    private fun Throwable.toCoreError(): CoreError = when (this) {
        is CoreError -> this
        else -> CoreError.Unexpected(message ?: javaClass.simpleName, this)
    }

    companion object {
        fun factory(dependencies: AppDependencies): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DayEditViewModel(
                    editing = EditingBridge(),
                    store = dependencies.store,
                )
            }
        }
    }
}
