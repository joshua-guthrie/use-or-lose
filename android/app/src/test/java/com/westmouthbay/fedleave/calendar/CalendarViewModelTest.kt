package com.westmouthbay.fedleave.calendar

import com.westmouthbay.fedleave.core.CoreError
import com.westmouthbay.fedleave.core.CoreRead
import com.westmouthbay.fedleave.core.DatedBalanceRequest
import com.westmouthbay.fedleave.core.DatedBalanceResult
import com.westmouthbay.fedleave.core.DateRequest
import com.westmouthbay.fedleave.core.DayActivityResult
import com.westmouthbay.fedleave.core.MonthViewRequest
import com.westmouthbay.fedleave.core.MonthViewResult
import com.westmouthbay.fedleave.core.PayPeriodDetailResult
import com.westmouthbay.fedleave.core.PeriodInfo
import com.westmouthbay.fedleave.core.TimeSource
import com.westmouthbay.fedleave.storage.PublishResult
import com.westmouthbay.fedleave.storage.StorageSnapshot
import com.westmouthbay.fedleave.storage.StoreCandidate
import com.westmouthbay.fedleave.storage.StoreCoordinator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.YearMonth

/**
 * State-machine tests against the pinned Calendar 2026 fixture (real store
 * metadata: year bounds, 26 periods) with a deterministic fake bridge that
 * returns the reference values from the acceptance table.
 */
class CalendarViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val jan13 = LocalDate.parse("2026-01-13")
    private val jan16 = LocalDate.parse("2026-01-16")
    private val jan22 = LocalDate.parse("2026-01-22")
    private val jan24 = LocalDate.parse("2026-01-24")
    private val jan25 = LocalDate.parse("2026-01-25")
    private val feb7 = LocalDate.parse("2026-02-07")
    private val jan4_2027 = LocalDate.parse("2027-01-04")
    private val jan9_2027 = LocalDate.parse("2027-01-09")

    private val fixtureNames = listOf(
        "config.json",
        "leave_years/2026.json",
        "holiday_cache/federal_holidays_2026.json",
    )

    // ---- harness ---------------------------------------------------------

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource(name)) { "missing resource $name" }
            .readText(Charsets.UTF_8)

    private fun publishedStore(): Pair<File, StoreCoordinator> {
        val root = tmp.newFolder("store")
        val coordinator = StoreCoordinator(root)
        val candidate = StoreCandidate()
        for (name in fixtureNames) {
            candidate.put(name, resource(name))
        }
        val published = runBlocking { coordinator.publish(candidate, expectedBase = null, fingerprint = "vm-test") }
        assertTrue("fixture rejected: $published", published is PublishResult.Published)
        return root to coordinator
    }

    private fun storeTree(root: File): Map<String, String> =
        Files.walk(root.toPath())
            .filter { Files.isRegularFile(it) }
            .toList()
            .associate { it.toString() to it.toFile().readText(Charsets.UTF_8) }

    private fun viewModel(coordinator: StoreCoordinator, bridge: FakeBridge): CalendarViewModel =
        CalendarViewModel(
            bridge = bridge,
            store = coordinator,
            timeSource = TimeSource.fixed("2026-01-16"),
            displaySettings = CalendarDisplaySettings(),
            providedScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob()),
        )

    private class FakeBridge : CoreRead {

        val balancesByDate = mutableMapOf<LocalDate, Map<String, Double>>()
        val failBalanceDates = mutableSetOf<LocalDate>()
        val balanceGates = mutableMapOf<LocalDate, CompletableDeferred<Unit>>()
        val payPeriodResults = mutableMapOf<LocalDate, PayPeriodDetailResult>()

        val balanceRequests = mutableListOf<LocalDate>()
        val monthRequests = mutableListOf<MonthViewRequest>()
        val payPeriodRequests = mutableListOf<LocalDate>()

        override suspend fun datedBalance(
            snapshot: StorageSnapshot,
            request: DatedBalanceRequest,
            localDate: LocalDate,
        ): DatedBalanceResult {
            balanceRequests += request.asOf
            balanceGates.remove(request.asOf)?.await()
            if (request.asOf in failBalanceDates) {
                throw CoreError.Read(
                    CoreError.LEAVE_YEAR_NOT_FOUND,
                    "as_of",
                    "No leave year covers ${request.asOf}.",
                )
            }
            val balances = checkNotNull(balancesByDate[request.asOf]) { "no fixture balance for ${request.asOf}" }
            return DatedBalanceResult(
                year = 2026,
                asOf = request.asOf,
                projected = false,
                projectTo = null,
                balances = balances,
                parental = null,
                warnings = emptyList(),
            )
        }

        override suspend fun monthView(
            snapshot: StorageSnapshot,
            request: MonthViewRequest,
            localDate: LocalDate,
        ): MonthViewResult {
            monthRequests += request
            return MonthViewResult(
                year = request.calendarYear,
                month = request.month,
                today = localDate,
                monthStart = LocalDate.of(request.calendarYear, request.month, 1),
                monthEnd = LocalDate.of(request.calendarYear, request.month, 1)
                    .withDayOfMonth(LocalDate.of(request.calendarYear, request.month, 1).lengthOfMonth()),
                calendarStart = LocalDate.of(request.calendarYear, request.month, 1),
                calendarEnd = LocalDate.of(request.calendarYear, request.month, 1)
                    .withDayOfMonth(LocalDate.of(request.calendarYear, request.month, 1).lengthOfMonth()),
                days = emptyList(),
                payPeriods = emptyList(),
                payDates = emptySet(),
                payPeriodEndDates = emptySet(),
                warnings = emptyList(),
            )
        }

        override suspend fun payPeriod(
            snapshot: StorageSnapshot,
            request: DateRequest,
            localDate: LocalDate,
        ): PayPeriodDetailResult {
            payPeriodRequests += request.date
            return checkNotNull(payPeriodResults[request.date]) { "no fixture period for ${request.date}" }
        }

        override suspend fun dayActivity(
            snapshot: StorageSnapshot,
            request: DateRequest,
            localDate: LocalDate,
        ): DayActivityResult = error("dayActivity is not consumed by I05 state tests")
    }

    private fun standardBridge(): FakeBridge = FakeBridge().apply {
        balancesByDate[jan13] = mapOf("annual" to 92.0, "sick" to 20.0)
        balancesByDate[jan16] = mapOf("annual" to 90.0, "sick" to 20.0)
        balancesByDate[jan22] = mapOf("annual" to 86.0, "sick" to 20.0)
        balancesByDate[jan24] = mapOf("annual" to 92.0, "sick" to 24.0)
        balancesByDate[jan25] = mapOf("annual" to 89.0, "sick" to 24.0)
        balancesByDate[jan4_2027] = mapOf("annual" to 233.0, "sick" to 120.0)
        payPeriodResults[jan24] = PayPeriodDetailResult(
            year = 2026,
            date = jan24,
            period = PeriodInfo(
                number = 1,
                startDate = LocalDate.parse("2026-01-11"),
                endDate = jan24,
                payDate = LocalDate.parse("2026-01-30"),
                accrualDate = jan24,
            ),
            activity = mapOf(
                "annual" to com.westmouthbay.fedleave.core.CategoryTotals(
                    earned = 6.0, used = 14.0, worked = 0.0, net = -8.0,
                ),
                "sick" to com.westmouthbay.fedleave.core.CategoryTotals(
                    earned = 4.0, used = 0.0, worked = 0.0, net = 4.0,
                ),
            ),
            endingBalances = mapOf("annual" to 92.0, "sick" to 24.0),
            warnings = emptyList(),
        )
    }

    // ---- cases -----------------------------------------------------------

    @Test
    fun launchShowsTodayAndFollowsToday() = runBlocking {
        val (root, coordinator) = publishedStore()
        val bridge = standardBridge()
        val before = storeTree(root)
        val vm = viewModel(coordinator, bridge)

        vm.onLaunch()
        vm.awaitIdle()

        val state = vm.state.value
        assertEquals(jan16, state.today)
        assertEquals(YearMonth.of(2026, 1), state.displayMonth)
        assertEquals(jan16, state.balanceDate)
        assertTrue(state.followsToday)
        assertEquals(jan16, state.balance?.asOf)
        assertEquals(90.0, state.balance?.balances?.get("annual")!!, 0.0)
        assertEquals(20.0, state.balance?.balances?.get("sick")!!, 0.0)
        assertEquals(YearMonth.of(2026, 1), state.monthFor)
        assertNull(state.balanceError)
        assertNull(state.monthError)
        assertEquals(before, storeTree(root))
    }

    @Test
    fun localDateAdvanceRefreshesCutoffWhileTodayFollows() = runBlocking {
        val (root, coordinator) = publishedStore()
        val bridge = standardBridge()
        bridge.balancesByDate[LocalDate.parse("2026-01-17")] =
            mapOf("annual" to 90.0, "sick" to 20.0)
        val before = storeTree(root)
        val vm = viewModel(coordinator, bridge)
        vm.onLaunch()
        vm.awaitIdle()

        vm.onLocalDateAdvance(LocalDate.parse("2026-01-17"))
        vm.awaitIdle()

        val state = vm.state.value
        assertEquals(LocalDate.parse("2026-01-17"), state.today)
        assertEquals(LocalDate.parse("2026-01-17"), state.balanceDate)
        assertEquals(90.0, state.balance?.balances?.get("annual")!!, 0.0)
        assertEquals(YearMonth.of(2026, 1), state.displayMonth)
        assertEquals(before, storeTree(root))
    }

    @Test
    fun confirmedDateLeavesTodayFollowingAndCancelPreservesIt() = runBlocking {
        val (root, coordinator) = publishedStore()
        val bridge = standardBridge()
        val before = storeTree(root)
        val vm = viewModel(coordinator, bridge)
        vm.onLaunch()
        vm.awaitIdle()

        vm.onChooseDate()
        vm.onPickerDaySelected(jan13)
        vm.onConfirmPicker()
        vm.awaitIdle()

        var state = vm.state.value
        assertEquals(jan13, state.balanceDate)
        assertFalse(state.followsToday)
        assertEquals(jan13, state.balance?.asOf)
        assertEquals(92.0, state.balance?.balances?.get("annual")!!, 0.0)
        assertEquals(20.0, state.balance?.balances?.get("sick")!!, 0.0)

        vm.onChooseDate()
        vm.onPickerDaySelected(jan24)
        vm.onCancelPicker()
        vm.awaitIdle()

        state = vm.state.value
        assertEquals(jan13, state.balanceDate)
        assertEquals(jan13, state.balance?.asOf)
        assertEquals(92.0, state.balance?.balances?.get("annual")!!, 0.0)
        assertNull(state.pickerDraft)
        assertNull(state.pickerMonth)
        assertEquals(before, storeTree(root))
    }

    @Test
    fun periodEndLoadsClosingBalancesAndPeriodDetail() = runBlocking {
        val (root, coordinator) = publishedStore()
        val bridge = standardBridge()
        val before = storeTree(root)
        val vm = viewModel(coordinator, bridge)
        vm.onLaunch()
        vm.awaitIdle()

        vm.onChooseDate()
        vm.onPickerDaySelected(jan24)
        vm.onConfirmPicker()
        vm.awaitIdle()

        val state = vm.state.value
        assertEquals(jan24, state.balanceDate)
        assertEquals(92.0, state.balance?.balances?.get("annual")!!, 0.0)
        assertEquals(24.0, state.balance?.balances?.get("sick")!!, 0.0)
        assertTrue(state.isProjected)
        assertEquals(jan24, state.periodFor)
        assertEquals(1, state.period?.period?.number)
        assertEquals(LocalDate.parse("2026-01-11"), state.period?.period?.startDate)
        assertEquals(LocalDate.parse("2026-01-30"), state.period?.period?.payDate)
        assertEquals(6.0, state.period?.activity?.get("annual")?.earned!!, 0.0)
        assertEquals(14.0, state.period?.activity?.get("annual")?.used!!, 0.0)
        assertEquals(4.0, state.period?.activity?.get("sick")?.earned!!, 0.0)
        assertEquals(0.0, state.period?.activity?.get("sick")?.used!!, 0.0)
        assertEquals(before, storeTree(root))
    }

    @Test
    fun futureDatesAreProjectedAndExactValuesMatch() = runBlocking {
        val (_, coordinator) = publishedStore()
        val bridge = standardBridge()
        val vm = viewModel(coordinator, bridge)
        vm.onLaunch()
        vm.awaitIdle()

        vm.onChooseDate()
        vm.onPickerDaySelected(jan22)
        vm.onConfirmPicker()
        vm.awaitIdle()
        var state = vm.state.value
        assertEquals(86.0, state.balance?.balances?.get("annual")!!, 0.0)
        assertEquals(20.0, state.balance?.balances?.get("sick")!!, 0.0)
        assertTrue(state.isProjected)
        assertNull(state.periodFor)

        vm.onChooseDate()
        vm.onPickerDaySelected(jan25)
        vm.onConfirmPicker()
        vm.awaitIdle()
        state = vm.state.value
        assertEquals(89.0, state.balance?.balances?.get("annual")!!, 0.0)
        assertEquals(24.0, state.balance?.balances?.get("sick")!!, 0.0)
        assertTrue(state.isProjected)

        vm.onChooseDate()
        vm.onPickerDaySelected(jan13)
        vm.onConfirmPicker()
        vm.awaitIdle()
        state = vm.state.value
        assertFalse(state.isProjected)
    }

    @Test
    fun failedRequestShowsUnavailableAndRetryRecovers() = runBlocking {
        val (_, coordinator) = publishedStore()
        val bridge = standardBridge()
        val vm = viewModel(coordinator, bridge)
        vm.onLaunch()
        vm.awaitIdle()

        bridge.failBalanceDates += jan22
        vm.onChooseDate()
        vm.onPickerDaySelected(jan22)
        vm.onConfirmPicker()
        vm.awaitIdle()

        var state = vm.state.value
        assertEquals(jan22, state.balanceDate)
        assertNull(state.balance)
        assertEquals(CoreError.LEAVE_YEAR_NOT_FOUND, state.balanceError?.code)
        assertFalse(state.balanceLoading)

        bridge.failBalanceDates -= jan22
        vm.onRetryBalance()
        vm.awaitIdle()

        state = vm.state.value
        assertEquals(jan22, state.balance?.asOf)
        assertEquals(86.0, state.balance?.balances?.get("annual")!!, 0.0)
        assertNull(state.balanceError)
    }

    @Test
    fun lateOlderResponseCannotReplaceNewerDate() = runBlocking {
        val (_, coordinator) = publishedStore()
        val bridge = standardBridge()
        val vm = viewModel(coordinator, bridge)
        vm.onLaunch()
        vm.awaitIdle()

        val gate = CompletableDeferred<Unit>()
        bridge.balanceGates[jan13] = gate

        vm.onChooseDate()
        vm.onPickerDaySelected(jan13)
        vm.onConfirmPicker()

        vm.onChooseDate()
        vm.onPickerDaySelected(jan22)
        vm.onConfirmPicker()

        // The Jan 22 read is not gated and completes first.
        var state = vm.state.value
        assertEquals(jan22, state.balance?.asOf)
        assertEquals(86.0, state.balance?.balances?.get("annual")!!, 0.0)

        // The held Jan 13 response arrives late and must be discarded.
        gate.complete(Unit)
        vm.awaitIdle()

        state = vm.state.value
        assertEquals(jan22, state.balanceDate)
        assertEquals(jan22, state.balance?.asOf)
        assertEquals(86.0, state.balance?.balances?.get("annual")!!, 0.0)
        assertNull(state.balanceError)
    }

    @Test
    fun jan2027ResolvesToThe2026LedgerAndBrowsingPreservesCutoff() = runBlocking {
        val (_, coordinator) = publishedStore()
        val bridge = standardBridge()
        val vm = viewModel(coordinator, bridge)
        vm.onLaunch()
        vm.awaitIdle()

        vm.onChooseDate()
        vm.onPickerDaySelected(jan4_2027)
        vm.onConfirmPicker()
        vm.awaitIdle()

        var state = vm.state.value
        assertEquals(jan4_2027, state.balanceDate)
        assertEquals(233.0, state.balance?.balances?.get("annual")!!, 0.0)
        assertEquals(120.0, state.balance?.balances?.get("sick")!!, 0.0)

        vm.onSelectMonth(YearMonth.of(2027, 1))
        vm.awaitIdle()

        state = vm.state.value
        assertEquals(YearMonth.of(2027, 1), state.displayMonth)
        assertEquals(jan4_2027, state.balanceDate)
        assertEquals(jan4_2027, state.balance?.asOf)
        val monthRequest = bridge.monthRequests.last()
        assertEquals(2026, monthRequest.year)
        assertEquals(1, monthRequest.month)
        assertEquals(2027, monthRequest.calendarYear)

        vm.onOpenDay(jan9_2027)
        state = vm.state.value
        assertEquals(jan9_2027, state.selectedDay)
        assertEquals(jan4_2027, state.balanceDate)
        vm.onCloseDay()
        assertNull(vm.state.value.selectedDay)
    }

    @Test
    fun menuTodayRestoresMonthAndCutoff() = runBlocking {
        val (_, coordinator) = publishedStore()
        val bridge = standardBridge()
        val vm = viewModel(coordinator, bridge)
        vm.onLaunch()
        vm.awaitIdle()

        vm.onChooseDate()
        vm.onPickerDaySelected(jan13)
        vm.onConfirmPicker()
        vm.awaitIdle()
        vm.onSelectMonth(YearMonth.of(2026, 2))
        vm.awaitIdle()

        vm.onMenuToday()
        vm.awaitIdle()

        val state = vm.state.value
        assertEquals(jan16, state.today)
        assertEquals(YearMonth.of(2026, 1), state.displayMonth)
        assertEquals(jan16, state.balanceDate)
        assertTrue(state.followsToday)
        assertEquals(90.0, state.balance?.balances?.get("annual")!!, 0.0)
        assertEquals(YearMonth.of(2026, 1), state.monthFor)
    }

    @Test
    fun pickerShortcutsComeFromStoredPeriods() = runBlocking {
        val (_, coordinator) = publishedStore()
        val bridge = standardBridge()
        val vm = viewModel(coordinator, bridge)
        vm.onLaunch()
        vm.awaitIdle()

        val shortcuts = vm.nearbyPayPeriodEnds(jan16)
        assertEquals(listOf(jan24, feb7), shortcuts.map { it.end })
        assertEquals(listOf(1, 2), shortcuts.map { it.number })

        val lastOnly = vm.nearbyPayPeriodEnds(jan9_2027)
        assertEquals(listOf(jan9_2027), lastOnly.map { it.end })
        assertEquals(listOf(26), lastOnly.map { it.number })

        vm.onChooseDate()
        vm.onPickerShortcutSelected(feb7)
        var state = vm.state.value
        assertEquals(feb7, state.pickerDraft)
        assertEquals(YearMonth.of(2026, 2), state.pickerMonth)

        vm.onCancelPicker()
        state = vm.state.value
        assertEquals(jan16, state.balanceDate)
    }

    @Test
    fun leaveYearEndComesFromTheSnapshot() = runBlocking {
        val (_, coordinator) = publishedStore()
        val bridge = standardBridge()
        val vm = viewModel(coordinator, bridge)
        vm.onLaunch()
        vm.awaitIdle()

        assertEquals(jan9_2027, vm.leaveYearEndFor(jan9_2027))
        assertEquals(jan9_2027, vm.leaveYearEndFor(jan4_2027))
        assertNull(vm.leaveYearEndFor(LocalDate.parse("2030-05-05")))
    }

    @Test
    fun unavailableStoreReportsErrorsWithoutCrashing() = runBlocking {
        val emptyRoot = tmp.newFolder("empty-store")
        val coordinator = StoreCoordinator(emptyRoot)
        val bridge = standardBridge()
        val vm = viewModel(coordinator, bridge)
        vm.onLaunch()
        vm.awaitIdle()

        val state = vm.state.value
        assertTrue(state.balanceError is CoreError.Unexpected)
        assertTrue(state.monthError is CoreError.Unexpected)
    }
}
