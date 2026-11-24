package com.westmouthbay.fedleave.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.westmouthbay.fedleave.core.CoreError
import com.westmouthbay.fedleave.core.CoreRead
import com.westmouthbay.fedleave.core.DatedBalanceRequest
import com.westmouthbay.fedleave.core.DateRequest
import com.westmouthbay.fedleave.core.MonthViewRequest
import com.westmouthbay.fedleave.core.TimeSource
import com.westmouthbay.fedleave.storage.OpenResult
import com.westmouthbay.fedleave.storage.StorageSnapshot
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
import java.time.YearMonth

/**
 * Owns the Calendar state across rotation and route return. Reads are
 * launch-and-forget coroutines matched back to the latest request identity;
 * a stale month or balance response can never replace a newer result.
 *
 * In tests, construct with an external scope (e.g. `Unconfined`) and call
 * [awaitIdle] before asserting. In production the injected scope is null and
 * the view model's own scope is used; [awaitIdle] is test-only plumbing.
 */
class CalendarViewModel(
    private val bridge: CoreRead,
    private val store: StoreCoordinator,
    private val timeSource: TimeSource,
    val displaySettings: CalendarDisplaySettings,
    private val providedScope: CoroutineScope? = null,
) : ViewModel() {

    private val scope: CoroutineScope by lazy { providedScope ?: viewModelScope }

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    /** Jobs launched by [launchRead]; appended on the action thread and drained by [awaitIdle]. */
    private val trackedJobs = java.util.Collections.synchronizedList(mutableListOf<Job>())

    private var snapshot: StorageSnapshot? = null
    private var years: Map<Int, YearMetadata> = emptyMap()

    private var monthRequestId = 0
    private var balanceRequestId = 0
    private var periodRequestId = 0

    // ---- lifecycle -------------------------------------------------------

    /** Fresh Calendar launch: open the store, then read today's month and balance. */
    fun onLaunch() {
        launchRead {
            val open = store.open()
            val ready = open as? OpenResult.Ready
            if (ready == null) {
                val error = CoreError.Unexpected("Store did not open: ${open.javaClass.simpleName}.")
                _state.update {
                    it.copy(
                        monthError = error,
                        balanceError = error,
                    )
                }
                return@launchRead
            }
            snapshot = ready.snapshot
            years = parseYearMetadata(ready.snapshot)
            val today = timeSource.today()
            val month = YearMonth.from(today)
            _state.update {
                it.copy(
                    today = today,
                    displayMonth = month,
                    balanceDate = today,
                    followsToday = true,
                )
            }
            loadMonth(month)
            loadBalance(today)
        }
    }

    /**
     * The local date advanced (clock tick or test hook). Today-following
     * refreshes the cutoff and follows the day into a new month only when the
     * previous today's month was on screen; a browsed month is preserved.
     * An explicit confirmed cutoff stays fixed when the clock changes.
     */
    fun onLocalDateAdvance(newToday: LocalDate) {
        val current = _state.value
        val newMonth = YearMonth.from(newToday)
        if (!current.followsToday) {
            _state.update { it.copy(today = newToday) }
            return
        }
        val previousTodayMonth = current.today?.let { YearMonth.from(it) }
        val wasShowingTodayMonth = current.displayMonth == previousTodayMonth
        _state.update {
            it.copy(
                today = newToday,
                balanceDate = newToday,
                displayMonth = if (wasShowingTodayMonth) newMonth else it.displayMonth,
            )
        }
        launchRead {
            if (_state.value.displayMonth == newMonth) {
                loadMonth(newMonth)
            }
            loadBalance(newToday)
        }
    }

    // ---- month and day ---------------------------------------------------

    /** Browse to another month; the balance date and result stay paired. */
    fun onSelectMonth(month: YearMonth) {
        if (_state.value.displayMonth == month) {
            return
        }
        _state.update { it.copy(displayMonth = month) }
        launchRead { loadMonth(month) }
    }

    /** Open the read-only day route for [date]; the cutoff is untouched. */
    fun onOpenDay(date: LocalDate) {
        _state.update { it.copy(selectedDay = date) }
    }

    fun onCloseDay() {
        _state.update { it.copy(selectedDay = null) }
    }

    // ---- balance picker --------------------------------------------------

    /** Open the picker with the draft initialized to the committed cutoff. */
    fun onChooseDate() {
        val anchor = _state.value.balanceDate ?: return
        _state.update {
            it.copy(
                pickerDraft = anchor,
                pickerMonth = YearMonth.from(anchor),
            )
        }
    }

    /** Choosing a cell updates the pending draft only. */
    fun onPickerDaySelected(date: LocalDate) {
        _state.update { it.copy(pickerDraft = date) }
    }

    /** A shortcut updates the draft and moves the picker month to it. */
    fun onPickerShortcutSelected(date: LocalDate) {
        _state.update {
            it.copy(
                pickerDraft = date,
                pickerMonth = YearMonth.from(date),
            )
        }
    }

    fun onPickerMonthChanged(month: YearMonth) {
        _state.update { it.copy(pickerMonth = month) }
    }

    /**
     * Commit the draft: leave Today following, clear the previous visible
     * result and show loading while the requested date resolves.
     */
    fun onConfirmPicker() {
        val draft = _state.value.pickerDraft ?: return
        _state.update {
            it.copy(
                balanceDate = draft,
                followsToday = false,
                pickerDraft = null,
                pickerMonth = null,
                balanceFor = null,
                balance = null,
                balanceLoading = true,
                balanceError = null,
                periodFor = null,
                period = null,
                periodLoading = false,
                periodError = null,
            )
        }
        launchRead { loadBalance(draft) }
    }

    /** Cancel discards the draft and picker month; the result stays. */
    fun onCancelPicker() {
        _state.update { it.copy(pickerDraft = null, pickerMonth = null) }
    }

    // ---- menu ------------------------------------------------------------

    /** Menu > Today: restore today's month and cutoff, supersede in-flight reads. */
    fun onMenuToday() {
        val today = timeSource.today()
        val month = YearMonth.from(today)
        monthRequestId++
        balanceRequestId++
        periodRequestId++
        _state.update {
            it.copy(
                today = today,
                displayMonth = month,
                balanceDate = today,
                followsToday = true,
                selectedDay = null,
                pickerDraft = null,
                pickerMonth = null,
            )
        }
        launchRead {
            loadMonth(month)
            loadBalance(today)
        }
    }

    // ---- retries ---------------------------------------------------------

    fun onRetryMonth() {
        val month = _state.value.displayMonth ?: return
        launchRead { loadMonth(month) }
    }

    fun onRetryBalance() {
        val date = _state.value.balanceDate ?: return
        launchRead { loadBalance(date) }
    }

    fun onRetryPeriod() {
        val date = _state.value.periodFor ?: _state.value.balanceDate ?: return
        launchRead { loadPeriod(date) }
    }

    // ---- derived presentation -------------------------------------------

    /**
     * The first two known pay-period ends on or after [anchor], ordered by
     * date, from the leave year covering the anchor. Empty when none qualify
     * (the picker then hides the shortcut group).
     */
    fun nearbyPayPeriodEnds(anchor: LocalDate): List<StoredPeriod> {
        val covering = years.values.firstOrNull { anchor in it.start..it.end } ?: return emptyList()
        return covering.periods
            .filter { it.end >= anchor }
            .sortedBy { it.end }
            .take(2)
    }

    /** The leave-year end matching [date], for the year-end marker. */
    fun leaveYearEndFor(date: LocalDate): LocalDate? =
        years.values.firstOrNull { date in it.start..it.end }?.end

    // ---- reads -----------------------------------------------------------

    private suspend fun loadMonth(month: YearMonth) {
        val id = ++monthRequestId
        val currentSnapshot = snapshot
        if (currentSnapshot == null) {
            _state.update {
                it.copy(
                    monthLoading = false,
                    monthError = CoreError.Unexpected("No store snapshot is open."),
                )
            }
            return
        }
        val monthStart = month.atDay(1)
        val monthEnd = month.atEndOfMonth()
        val covering = years.values.firstOrNull { monthStart <= it.end && monthEnd >= it.start }
        if (covering == null) {
            _state.update {
                it.copy(
                    monthFor = null,
                    month = null,
                    monthLoading = false,
                    monthError = CoreError.Read(
                        CoreError.LEAVE_YEAR_NOT_FOUND,
                        "month",
                        "No leave year covers ${month.atDay(1)}.",
                    ),
                )
            }
            return
        }
        _state.update { it.copy(monthLoading = true, monthError = null) }
        val localDate = _state.value.today ?: timeSource.today()
        val result = runCatching {
            bridge.monthView(
                currentSnapshot,
                MonthViewRequest(
                    year = covering.year,
                    month = month.monthValue,
                    calendarYear = month.year,
                ),
                localDate,
            )
        }
        if (id != monthRequestId || _state.value.displayMonth != month) {
            return
        }
        result.fold(
            onSuccess = { view ->
                _state.update {
                    it.copy(
                        monthFor = month,
                        month = view,
                        monthLoading = false,
                        monthError = null,
                    )
                }
            },
            onFailure = { error ->
                _state.update {
                    it.copy(
                        monthFor = null,
                        month = null,
                        monthLoading = false,
                        monthError = error.toCoreError(),
                    )
                }
            },
        )
    }

    private suspend fun loadBalance(date: LocalDate) {
        val id = ++balanceRequestId
        val currentSnapshot = snapshot
        if (currentSnapshot == null) {
            _state.update {
                it.copy(
                    balanceLoading = false,
                    balanceError = CoreError.Unexpected("No store snapshot is open."),
                )
            }
            return
        }
        _state.update { it.copy(balanceLoading = true, balanceError = null) }
        val localDate = _state.value.today ?: timeSource.today()
        val result = runCatching {
            bridge.datedBalance(currentSnapshot, DatedBalanceRequest(asOf = date), localDate)
        }
        if (id != balanceRequestId || _state.value.balanceDate != date) {
            return
        }
        result.fold(
            onSuccess = { balance ->
                _state.update {
                    it.copy(
                        balanceFor = date,
                        balance = balance,
                        balanceLoading = false,
                        balanceError = null,
                    )
                }
            },
            onFailure = { error ->
                _state.update {
                    it.copy(
                        balanceFor = date,
                        balance = null,
                        balanceLoading = false,
                        balanceError = error.toCoreError(),
                    )
                }
            },
        )
        loadPeriodIfEnd(date)
    }

    /** When the committed cutoff is a stored period end, load its detail. */
    private suspend fun loadPeriodIfEnd(date: LocalDate) {
        val covering = years.values.firstOrNull { date in it.start..it.end }
        val isEnd = covering?.periods?.any { it.end == date } == true
        if (!isEnd) {
            _state.update {
                it.copy(
                    periodFor = null,
                    period = null,
                    periodLoading = false,
                    periodError = null,
                )
            }
            return
        }
        loadPeriod(date)
    }

    private suspend fun loadPeriod(date: LocalDate) {
        val id = ++periodRequestId
        val currentSnapshot = snapshot
        if (currentSnapshot == null) {
            _state.update {
                it.copy(
                    periodLoading = false,
                    periodError = CoreError.Unexpected("No store snapshot is open."),
                )
            }
            return
        }
        _state.update { it.copy(periodLoading = true, periodError = null) }
        val localDate = _state.value.today ?: timeSource.today()
        val result = runCatching {
            bridge.payPeriod(currentSnapshot, DateRequest(date), localDate)
        }
        if (id != periodRequestId || _state.value.balanceDate != date) {
            return
        }
        result.fold(
            onSuccess = { detail ->
                _state.update {
                    it.copy(
                        periodFor = date,
                        period = detail,
                        periodLoading = false,
                        periodError = null,
                    )
                }
            },
            onFailure = { error ->
                _state.update {
                    it.copy(
                        periodFor = date,
                        period = null,
                        periodLoading = false,
                        periodError = error.toCoreError(),
                    )
                }
            },
        )
    }

    // ---- plumbing --------------------------------------------------------

    private fun launchRead(block: suspend () -> Unit) {
        val job = scope.launch { block() }
        trackedJobs += job
        job.invokeOnCompletion { trackedJobs.remove(job) }
    }

    /** Wait until every read launched so far has completed (test-only). */
    suspend fun awaitIdle() {
        while (true) {
            val pending = trackedJobs.toList()
            if (pending.isEmpty()) {
                return
            }
            pending.joinAll()
        }
    }

    private fun Throwable.toCoreError(): CoreError = when (this) {
        is CoreError -> this
        else -> CoreError.Unexpected(message ?: javaClass.simpleName, this)
    }
}
