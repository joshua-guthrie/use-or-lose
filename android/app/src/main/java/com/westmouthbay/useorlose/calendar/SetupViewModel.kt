package com.westmouthbay.useorlose.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.westmouthbay.useorlose.core.AppDependencies
import com.westmouthbay.useorlose.core.CarryoverCreateRequest
import com.westmouthbay.useorlose.core.CoreError
import com.westmouthbay.useorlose.core.CreationBridge
import com.westmouthbay.useorlose.core.ManualCreateRequest
import com.westmouthbay.useorlose.core.TimeSource
import com.westmouthbay.useorlose.core.TodayCreateRequest
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
import java.time.LocalDate
import java.time.ZoneId

/**
 * Owns the leave-year setup flow: method choice, the start-of-year and
 * Today balance forms, the preview, and the single confirmed publish
 * through the I04 writer. Preview and cancel write nothing; a stale base
 * or a midnight rollover forces a fresh preview and a new confirmation.
 */
class SetupViewModel(
    private val creation: com.westmouthbay.useorlose.core.CreationRead,
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
        // A fresh entry resets everything, including a prior published year:
        // consecutive missing years are created one flow at a time.
        val sourceYear = proposal.year - 1
        _state.update {
            SetupUiState(
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
        // The carryover choice is offered when the adjacent source file
        // exists; deeper schedule/policy validation happens at preview.
        launchRead {
            val snapshot = (store.open() as? OpenResult.Ready)?.snapshot
            val available = snapshot?.files?.containsKey("leave_years/$sourceYear.json") == true
            _state.update { it.copy(carryoverSourceYear = sourceYear.takeIf { available }) }
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
        it.copy(
            step = SetupStep.METHOD,
            preview = null,
            previewKind = null,
            carryoverPreview = null,
            carryoverPayPeriods = null,
            error = null,
            stale = false,
        )
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

    fun onChooseCarryover() {
        val state = _state.value
        val sourceYear = state.carryoverSourceYear ?: state.year.toIntOrNull()?.minus(1)
        val year = state.year.toIntOrNull()
        if (sourceYear == null || year == null) {
            _state.update {
                it.copy(error = CoreError.Read("INVALID_REQUEST", "year", "Check the leave-year fields."))
            }
            return
        }
        _state.update { it.copy(loading = true, error = null, carryoverBlocked = null) }
        launchRead { previewCarryoverNow(sourceYear, year, state.payPeriods) }
    }

    fun onCarryoverPayPeriodsChanged(value: Int) {
        _state.update { it.copy(carryoverPayPeriods = value) }
        // The schedule override changes the candidate: re-derive a fresh
        // preview whenever the review already shows one.
        val state = _state.value
        val sourceYear = state.carryoverSourceYear ?: state.year.toIntOrNull()?.minus(1)
        val year = state.year.toIntOrNull()
        if (sourceYear == null || year == null || state.carryoverPreview == null) {
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        launchRead { previewCarryoverNow(sourceYear, year, value) }
    }

    fun onCarryoverBack() {
        val resumeDate = _state.value.resumeDate
        if (resumeDate != null) {
            onCancel()
        } else {
            onBackToMethod()
        }
    }

    /** Re-read the current store and derive the carryover preview from it. */
    private suspend fun previewCarryoverNow(
        sourceYear: Int,
        year: Int,
        payPeriods: Int?,
    ) {
        val snapshot = (store.open() as? OpenResult.Ready)?.snapshot
        if (snapshot == null) {
            _state.update {
                it.copy(
                    loading = false,
                    error = CoreError.Read(
                        "STORE_UNAVAILABLE",
                        "store",
                        "The existing data needs review before a new year can be created.",
                    ),
                )
            }
            return
        }
        val source = snapshot.files["leave_years/$sourceYear.json"]
        if (source == null) {
            _state.update {
                it.copy(
                    loading = false,
                    step = SetupStep.METHOD,
                    carryoverBlocked = "No valid $sourceYear source ledger is available.",
                )
            }
            return
        }
        val config = snapshot.files["config.json"]
        val result = runCatching {
            creation.previewCarryover(
                CarryoverCreateRequest(
                    year = year,
                    sourceYear = sourceYear,
                    sourceJson = source,
                    payPeriods = payPeriods,
                    configJson = config,
                ),
            )
        }
        result.fold(
            onSuccess = { carryoverResult ->
                val base = snapshot.generationId
                _state.update {
                    it.copy(
                        step = SetupStep.CARRYOVER_REVIEW,
                        carryoverPreview = carryoverResult.preview,
                        previewedFiles = carryoverResult.files,
                        previewKind = SetupKind.CARRYOVER,
                        previewedBase = base,
                        loading = false,
                        stale = false,
                        error = null,
                    )
                }
            },
            onFailure = { error ->
                val core = error.toCoreError()
                _state.update {
                    it.copy(
                        loading = false,
                        step = SetupStep.METHOD,
                        carryoverBlocked = core.describe(),
                    )
                }
            },
        )
    }

    fun onCarryoverConfirm() {
        val state = _state.value
        val year = state.year.toIntOrNull()
        val sourceYear = state.carryoverSourceYear
        if (year == null || sourceYear == null || state.published) {
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        launchRead {
            val opened = store.open()
            val snapshot = opened as? OpenResult.Ready
            if (snapshot == null) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = CoreError.Read(
                            "STORE_UNAVAILABLE",
                            "store",
                            "The existing data needs review before a new year can be created.",
                        ),
                    )
                }
                return@launchRead
            }
            if (snapshot.snapshot.files.containsKey("leave_years/$year.json")) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = CoreError.Read(
                            "DESTINATION_EXISTS",
                            "year",
                            "The $year leave year already exists.",
                        ),
                    )
                }
                return@launchRead
            }
            val payPeriods = state.carryoverPayPeriods
            val result = runCatching {
                creation.previewCarryover(
                    CarryoverCreateRequest(
                        year = year,
                        sourceYear = sourceYear,
                        sourceJson = snapshot.snapshot.files.getValue("leave_years/$sourceYear.json"),
                        payPeriods = payPeriods,
                        configJson = snapshot.snapshot.files["config.json"],
                    ),
                )
            }
            result.fold(
                onSuccess = { carryoverResult ->
                    val reviewed = state.carryoverPreview?.previewFingerprint
                    if (reviewed != null && carryoverResult.preview.previewFingerprint != reviewed) {
                        _state.update {
                            it.copy(
                                step = SetupStep.CARRYOVER_REVIEW,
                                carryoverPreview = carryoverResult.preview,
                                previewedFiles = carryoverResult.files,
                                previewedBase = snapshot.snapshot.generationId,
                                loading = false,
                                stale = true,
                            )
                        }
                        return@fold
                    }
                    val candidate = StoreCandidate()
                    snapshot.snapshot.files.forEach { (name, text) -> candidate.put(name, text) }
                    for ((name, text) in carryoverResult.files) {
                        candidate.put(name, text)
                    }
                    val published = store.publish(
                        candidate,
                        expectedBase = state.previewedBase,
                        fingerprint = "carryover:$year",
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
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(loading = false, error = error.toCoreError())
                    }
                },
            )
        }
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
        val kind = state.previewKind
            ?: if (state.step == SetupStep.TODAY_FORM) SetupKind.TODAY else SetupKind.MANUAL
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
                    SetupKind.CARRYOVER -> null
                }
            }
            result.fold(
                onSuccess = { creationResult ->
                    if (creationResult == null) {
                        _state.update {
                            it.copy(
                                loading = false,
                                error = CoreError.Read(
                                    "INVALID_REQUEST",
                                    "request",
                                    "Carryover previews use the carryover flow.",
                                ),
                            )
                        }
                        return@fold
                    }
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
            if (opened is OpenResult.Recoverable) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = CoreError.Read(
                            "STORE_UNAVAILABLE",
                            "store",
                            "The existing data needs review before a new year can be created.",
                        ),
                    )
                }
                return@launchRead
            }
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

    private fun CoreError.describe(): String = when (this) {
        is CoreError.Read -> message
        else -> message ?: javaClass.simpleName
    }

    companion object {
        fun factory(dependencies: AppDependencies): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SetupViewModel(
                    creation = CreationBridge(),
                    store = dependencies.store,
                    timeSource = dependencies.timeSource,
                )
            }
        }
    }
}
