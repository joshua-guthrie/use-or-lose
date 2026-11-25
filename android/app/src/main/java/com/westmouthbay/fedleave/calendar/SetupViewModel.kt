package com.westmouthbay.fedleave.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chaquo.python.Python
import com.westmouthbay.fedleave.core.AppDependencies
import com.westmouthbay.fedleave.core.CoreError
import com.westmouthbay.fedleave.core.CreationBridge
import com.westmouthbay.fedleave.core.ManualCreateRequest
import com.westmouthbay.fedleave.core.TimeSource
import com.westmouthbay.fedleave.core.TodayCreateRequest
import com.westmouthbay.fedleave.storage.OpenResult
import com.westmouthbay.fedleave.storage.PublishResult
import com.westmouthbay.fedleave.storage.StoreCandidate
import com.westmouthbay.fedleave.storage.StoreCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Owns the leave-year setup flow: method choice, the start-of-year and
 * Today balance forms, the preview, and the single confirmed publish
 * through the I04 writer. Preview and cancel write nothing; a stale base
 * or a midnight rollover forces a fresh preview and a new confirmation.
 */
class SetupViewModel(
    private val creation: com.westmouthbay.fedleave.core.CreationRead,
    private val store: StoreCoordinator,
    private val timeSource: TimeSource,
    private val timezone: String = ZoneId.systemDefault().id,
    private val providedScope: CoroutineScope? = null,
) : ViewModel() {

    private val scope: CoroutineScope by lazy { providedScope ?: viewModelScope }

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

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

    /** The current store's config.json, when present, for the core builders. */
    private suspend fun currentConfig(): String? =
        (store.open() as? OpenResult.Ready)?.snapshot?.files?.get("config.json")

    /** Enter the flow from the welcome screen (resumeDate null) or an uncovered date. */
    fun begin(proposal: SetupProposal, resumeDate: String?) {
        _state.update {
            it.copy(
                step = SetupStep.METHOD,
                year = proposal.year.toString(),
                leaveYearStart = proposal.leaveYearStart,
                payPeriods = proposal.payPeriods,
                annualAccrual = proposal.annualAccrual,
                sickAccrual = proposal.sickAccrual,
                resumeDate = resumeDate,
                todayIso = timeSource.today().toString(),
            )
        }
    }

    fun onYearChanged(value: String) = _state.update { it.copy(year = value) }

    fun onStartChanged(value: String) = _state.update { it.copy(leaveYearStart = value) }

    fun onPayPeriodsChanged(value: Int) = _state.update { it.copy(payPeriods = value) }

    fun onAnnualAccrualChanged(value: String) = _state.update { it.copy(annualAccrual = value) }

    fun onSickAccrualChanged(value: String) = _state.update { it.copy(sickAccrual = value) }

    fun onOpeningChanged(category: String, value: String) = _state.update {
        it.copy(openings = it.openings + (category to value))
    }

    fun onTargetChanged(category: String, value: String) = _state.update {
        it.copy(todayTargets = it.todayTargets + (category to value))
    }

    fun onShowOtherOpenings() = _state.update { it.copy(showOtherOpenings = !it.showOtherOpenings) }

    fun onShowOtherTargets() = _state.update { it.copy(showOtherTargets = !it.showOtherTargets) }

    fun onChooseStartOfYear() = _state.update {
        it.copy(step = SetupStep.START_FORM, error = null, stale = false)
    }

    fun onChooseToday() = _state.update {
        it.copy(step = SetupStep.TODAY_FORM, error = null, stale = false)
    }

    fun onBackToMethod() = _state.update {
        it.copy(step = SetupStep.METHOD, preview = null, previewKind = null, error = null, stale = false)
    }

    fun onBackToForm() = _state.update {
        it.copy(
            step = if (it.previewKind == SetupKind.TODAY) SetupStep.TODAY_FORM else SetupStep.START_FORM,
            preview = null,
            previewKind = null,
            error = null,
            stale = false,
        )
    }

    fun onCancel() {
        val resumeDate = _state.value.resumeDate
        _state.update { SetupUiState(resumeDate = resumeDate) }
    }

    /** Today is offered only when the proposed schedule contains it. */
    fun todayInRange(): Boolean {
        val state = _state.value
        val start = runCatching { LocalDate.parse(state.leaveYearStart) }.getOrNull() ?: return false
        val end = start.plusDays((state.payPeriods * 14L) - 1)
        return !timeSource.today().isBefore(start) && !timeSource.today().isAfter(end)
    }

    fun onPreview() {
        val state = _state.value
        val year = state.year.toIntOrNull()
        val start = runCatching { LocalDate.parse(state.leaveYearStart) }.getOrNull()
        val annual = state.annualAccrual.toDoubleOrNull()
        val sick = state.sickAccrual.toDoubleOrNull()
        if (year == null || start == null || annual == null || sick == null) {
            _state.update {
                it.copy(error = CoreError.Read("INVALID_REQUEST", "request", "Check the schedule fields."))
            }
            return
        }
        val kind = when (state.step) {
            SetupStep.TODAY_FORM -> SetupKind.TODAY
            else -> SetupKind.MANUAL
        }
        val hours = parseHours(if (kind == SetupKind.TODAY) state.todayTargets else state.openings)
        if (hours == null) {
            _state.update {
                it.copy(error = CoreError.Read("INVALID_REQUEST", "hours", "Enter valid nonnegative hours."))
            }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        launchRead {
            val config = currentConfig()
            val result = runCatching {
                when (kind) {
                    SetupKind.MANUAL -> creation.previewManual(
                        ManualCreateRequest(
                            year = year,
                            leaveYearStart = start,
                            payPeriods = state.payPeriods,
                            annualAccrual = annual,
                            sickAccrual = sick,
                            balances = hours,
                            configJson = config,
                            timezone = timezone,
                        ),
                    )
                    SetupKind.TODAY -> creation.previewToday(
                        TodayCreateRequest(
                            year = year,
                            leaveYearStart = start,
                            payPeriods = state.payPeriods,
                            annualAccrual = annual,
                            sickAccrual = sick,
                            today = timeSource.today(),
                            targets = hours,
                            configJson = config,
                            timezone = timezone,
                        ),
                    )
                }
            }
            result.fold(
                onSuccess = { creationResult ->
                    val base = (store.open() as? OpenResult.Ready)?.snapshot?.generationId
                    _state.update {
                        it.copy(
                            step = SetupStep.REVIEW,
                            preview = creationResult.preview,
                            previewedFiles = creationResult.files,
                            previewKind = kind,
                            previewedToday = timeSource.today().toString(),
                            previewedBase = base,
                            loading = false,
                            error = null,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(loading = false, error = error.toCoreError())
                    }
                },
            )
        }
    }

    fun onConfirm() {
        val state = _state.value
        val files = state.previewedFiles
        val year = state.year.toIntOrNull()
        if (files == null || year == null || state.published) {
            return
        }
        // A midnight rollover invalidates the preview: refresh and require a
        // new confirmation.
        if (state.previewedToday != timeSource.today().toString()) {
            _state.update { it.copy(stale = true) }
            onPreview()
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        launchRead {
            val opened = store.open()
            val snapshot = opened as? OpenResult.Ready
            val candidate = StoreCandidate()
            // A generation is the complete store: carry the existing files
            // forward and overlay the created ones.
            snapshot?.snapshot?.files?.forEach { (name, text) ->
                candidate.put(name, text)
            }
            for ((name, text) in files) {
                candidate.put(name, text)
            }
            // The expected base is the generation captured at preview time:
            // any competing change (or a same-year creation) is stale.
            val published = store.publish(
                candidate,
                expectedBase = state.previewedBase,
                fingerprint = "setup:$year",
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

    /** Returns null when any non-blank entry is not a finite number. */
    private fun parseHours(values: Map<String, String>): Map<String, Double>? {
        val result = mutableMapOf<String, Double>()
        for ((category, text) in values) {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) {
                continue
            }
            val hours = trimmed.toDoubleOrNull()
            if (hours == null || !hours.isFinite()) {
                return null
            }
            result[category] = hours
        }
        return result
    }

    private fun Throwable.toCoreError(): CoreError = when (this) {
        is CoreError -> this
        else -> CoreError.Unexpected(message ?: javaClass.simpleName, this)
    }

    companion object {
        fun factory(dependencies: AppDependencies): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SetupViewModel(
                    creation = CreationBridge(Python.getInstance()),
                    store = dependencies.store,
                    timeSource = dependencies.timeSource,
                )
            }
        }
    }
}
