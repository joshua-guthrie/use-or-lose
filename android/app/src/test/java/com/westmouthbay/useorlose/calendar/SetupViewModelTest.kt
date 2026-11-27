package com.westmouthbay.useorlose.calendar

import com.westmouthbay.useorlose.core.CarryoverCreateRequest
import com.westmouthbay.useorlose.core.CarryoverCreationResult
import com.westmouthbay.useorlose.core.CarryoverLot
import com.westmouthbay.useorlose.core.CarryoverPreview
import com.westmouthbay.useorlose.core.CarryoverReduction
import com.westmouthbay.useorlose.core.CreationPreview
import com.westmouthbay.useorlose.core.CreationRead
import com.westmouthbay.useorlose.core.CreationResult
import com.westmouthbay.useorlose.core.ManualCreateRequest
import com.westmouthbay.useorlose.core.NextAccrual
import com.westmouthbay.useorlose.core.TimeSource
import com.westmouthbay.useorlose.core.TodayCreateRequest
import com.westmouthbay.useorlose.storage.OpenResult
import com.westmouthbay.useorlose.storage.PublishResult
import com.westmouthbay.useorlose.storage.StoreCandidate
import com.westmouthbay.useorlose.storage.StoreCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
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

private fun testLedger(year: Int, start: String): String {
    val startDate = LocalDate.parse(start)
    val periods = org.json.JSONArray()
    var currentStart = startDate
    for (number in 1..26) {
        val end = currentStart.plusDays(13)
        periods.put(
            JSONObject()
                .put("pay_period_number", number)
                .put("start_date", currentStart.toString())
                .put("end_date", end.toString())
                .put("pay_date", end.plusDays(6).toString())
                .put("accrual_date", end.toString()),
        )
        currentStart = end.plusDays(1)
    }
    val ledger = JSONObject()
        .put("schema_version", 1)
        .put("leave_year", year)
        .put("leave_year_start", start)
        .put("leave_year_end", currentStart.minusDays(1).toString())
        .put("pay_period_count", 26)
        .put("pay_periods", periods)
        .put("annual_leave_accrual_hours", 1.25)
        .put("sick_leave_accrual_hours", 4.0)
        .put("starting_balances", JSONObject().put("travel_comp", 40.0))
        .put("carryover_from_previous_year", JSONObject())
        .put("transactions", org.json.JSONArray())
        .put("initialization", JSONObject())
        .put("rollover_status", JSONObject())
    return ledger.toString()
}

/**
 * The setup state machine against the real I04 writer: preview, confirm,
 * merge-with-existing, cancel, stale base, midnight rollover and duplicate
 * confirmation. The fake creation bridge returns a minimal schema-v1 ledger.
 */
class SetupViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()


    private class FakeCreation : CreationRead {
        val requests = mutableListOf<Pair<String, String>>()
        var failPreview = false
        var includeConfig = false

        override suspend fun previewManual(request: ManualCreateRequest): CreationResult {
            requests += "manual" to request.leaveYearStart.toString()
            if (failPreview) {
                throw com.westmouthbay.useorlose.core.CoreError.Read(
                    "INVALID_HOURS",
                    "hours",
                    "Hours must be nonnegative.",
                )
            }
            val files = mutableMapOf(
                "leave_years/${request.year}.json" to testLedger(request.year, request.leaveYearStart.toString()),
            )
            if (includeConfig) {
                files["config.json"] = "{\"schema_version\":1}"
            }
            return CreationResult(
                files = files,
                preview = CreationPreview(
                    year = request.year,
                    start = request.leaveYearStart,
                    end = request.leaveYearStart.plusDays(26 * 14 - 1),
                    payPeriodCount = request.payPeriods ?: 26,
                    firstPayPeriod = request.leaveYearStart,
                    annualAccrual = request.annualAccrual,
                    sickAccrual = request.sickAccrual,
                    openings = request.balances,
                    nextAccrual = null,
                ),
            )
        }

        override suspend fun previewToday(request: TodayCreateRequest): CreationResult {
            requests += "today" to request.today.toString()
            return CreationResult(
                files = mapOf(
                    "leave_years/${request.year}.json" to testLedger(request.year, request.leaveYearStart.toString()),
                ),
                preview = CreationPreview(
                    year = request.year,
                    start = request.leaveYearStart,
                    end = request.leaveYearStart.plusDays(26 * 14 - 1),
                    payPeriodCount = request.payPeriods ?: 26,
                    firstPayPeriod = request.leaveYearStart,
                    annualAccrual = request.annualAccrual,
                    sickAccrual = request.sickAccrual,
                    openings = request.targets,
                    nextAccrual = NextAccrual(request.today.plusDays(11), request.annualAccrual, request.sickAccrual),
                ),
            )
        }

        override suspend fun previewCarryover(request: CarryoverCreateRequest): CarryoverCreationResult {
            requests += "carryover" to request.sourceYear.toString()
            // The fake mirrors the fingerprint contract: it changes when the
            // source changes, so a stale confirm re-derives a new value.
            val fingerprint = "fp:" + request.sourceJson.hashCode()
            val start = LocalDate.parse("2027-01-10")
            return CarryoverCreationResult(
                files = mapOf(
                    "leave_years/${request.year}.json" to testLedger(request.year, "2027-01-10"),
                ),
                preview = CarryoverPreview(
                    year = request.year,
                    sourceYear = request.sourceYear,
                    sourceStart = LocalDate.parse("2026-01-11"),
                    sourceEnd = LocalDate.parse("2027-01-09"),
                    start = start,
                    end = start.plusDays(26 * 14 - 1),
                    payPeriodCount = request.payPeriods ?: 26,
                    firstPayPeriod = start,
                    openings = mapOf("annual" to 240.0, "sick" to 600.0, "credit" to 24.0),
                    reductions = listOf(
                        CarryoverReduction("annual", 40.0, "cap"),
                        CarryoverReduction("credit", 6.0, "cap"),
                    ),
                    lots = listOf(CarryoverLot("travel_comp", 40.0, 2026, "lot-1", null)),
                    warnings = emptyList(),
                    sourceFingerprint = "src",
                    previewFingerprint = fingerprint,
                ),
            )
        }
    }

    private fun storeTree(root: File): Map<String, String> =
        Files.walk(root.toPath())
            .filter { Files.isRegularFile(it) }
            .toList()
            .associate { it.toString() to it.toFile().readText(Charsets.UTF_8) }

    private fun viewModel(
        coordinator: StoreCoordinator,
        bridge: FakeCreation,
        today: String = "2026-01-16",
    ): SetupViewModel = SetupViewModel(
        creation = bridge,
        store = coordinator,
        timeSource = TimeSource.fixed(today),
        providedScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob()),
    )

    private fun proposal() = SetupProposal(
        year = 2027,
        leaveYearStart = "2027-01-10",
        payPeriods = 26,
        annualAccrual = "1.25",
        sickAccrual = "4",
    )

    @Test
    fun previewThenConfirmPublishesTheYear() = runBlocking {
        val root = tmp.newFolder("store")
        val coordinator = StoreCoordinator(root)
        val bridge = FakeCreation().apply { includeConfig = true }
        val vm = viewModel(coordinator, bridge)
        vm.begin(proposal(), resumeDate = null)
        vm.onChooseStartOfYear()
        vm.onOpeningChanged("travel_comp", "40")
        vm.onPreview()
        vm.awaitIdle()

        val state = vm.state.value
        assertEquals(SetupStep.REVIEW, state.step)
        assertEquals(LocalDate.parse("2027-01-10"), state.preview?.start)

        vm.onConfirm()
        vm.awaitIdle()

        assertTrue(vm.state.value.published)
        val opened = coordinator.open()
        assertTrue(opened is OpenResult.Ready)
        val files = (opened as OpenResult.Ready).snapshot.files
        assertTrue(files.containsKey("leave_years/2027.json"))
        assertTrue(files.containsKey("config.json"))
    }

    @Test
    fun confirmMergesTheExistingStoreFiles() = runBlocking {
        val root = tmp.newFolder("store")
        val coordinator = StoreCoordinator(root)
        // Publish an existing year first.
        val existing = StoreCandidate()
        existing.put("config.json", "{\"schema_version\":1,\"defaults\":{}}")
        existing.put(
            "leave_years/2026.json",
            testLedger(2026, "2026-01-11"),
        )
        assertTrue(
            runBlocking { coordinator.publish(existing, null, "base") } is PublishResult.Published,
        )
        val bridge = FakeCreation()
        val vm = viewModel(coordinator, bridge)
        vm.begin(proposal(), resumeDate = null)
        vm.onChooseStartOfYear()
        vm.onPreview()
        vm.awaitIdle()
        vm.onConfirm()
        vm.awaitIdle()

        assertTrue(vm.state.value.published)
        val files = (coordinator.open() as OpenResult.Ready).snapshot.files
        assertTrue(files.containsKey("leave_years/2026.json"))
        assertTrue(files.containsKey("leave_years/2027.json"))
        // The existing config is preserved, not replaced.
        assertEquals("{\"schema_version\":1,\"defaults\":{}}", files["config.json"])
    }

    @Test
    fun carryoverChoiceAppearsOnlyWithAnAdjacentSource() = runBlocking {
        val root = tmp.newFolder("store")
        val coordinator = StoreCoordinator(root)
        val existing = StoreCandidate()
        existing.put("config.json", "{\"schema_version\":1,\"defaults\":{}}")
        existing.put("leave_years/2026.json", testLedger(2026, "2026-01-11"))
        assertTrue(runBlocking { coordinator.publish(existing, null, "base") } is PublishResult.Published)

        val vm = viewModel(coordinator, FakeCreation())
        vm.begin(proposal(), resumeDate = null)
        vm.awaitIdle()
        assertEquals(2026, vm.state.value.carryoverSourceYear)

        val emptyRoot = tmp.newFolder("empty")
        val emptyVm = viewModel(StoreCoordinator(emptyRoot), FakeCreation())
        emptyVm.begin(proposal(), resumeDate = null)
        emptyVm.awaitIdle()
        assertNull(emptyVm.state.value.carryoverSourceYear)
    }

    @Test
    fun carryoverPreviewThenConfirmPublishesAndPreservesTheSource() = runBlocking {
        val root = tmp.newFolder("store")
        val coordinator = StoreCoordinator(root)
        val existing = StoreCandidate()
        existing.put("config.json", "{\"schema_version\":1,\"defaults\":{}}")
        existing.put("leave_years/2026.json", testLedger(2026, "2026-01-11"))
        assertTrue(runBlocking { coordinator.publish(existing, null, "base") } is PublishResult.Published)
        val sourceBefore = (coordinator.open() as OpenResult.Ready).snapshot.files["leave_years/2026.json"]

        val vm = viewModel(coordinator, FakeCreation())
        vm.begin(proposal(), resumeDate = null)
        vm.awaitIdle()
        vm.onChooseCarryover()
        vm.awaitIdle()

        val state = vm.state.value
        assertEquals(SetupStep.CARRYOVER_REVIEW, state.step)
        assertEquals(240.0, state.carryoverPreview?.openings?.get("annual") ?: 0.0, 0.0001)
        assertEquals(2, state.carryoverPreview?.reductions?.size)

        vm.onCarryoverConfirm()
        vm.awaitIdle()

        assertTrue(vm.state.value.published)
        val files = (coordinator.open() as OpenResult.Ready).snapshot.files
        assertTrue(files.containsKey("leave_years/2027.json"))
        // The source ledger stays byte-identical.
        assertEquals(sourceBefore, files["leave_years/2026.json"])
    }

    @Test
    fun staleCarryoverConfirmationRefreshesAndRequiresANewConfirm() = runBlocking {
        val root = tmp.newFolder("store")
        val coordinator = StoreCoordinator(root)
        val existing = StoreCandidate()
        existing.put("config.json", "{\"schema_version\":1,\"defaults\":{}}")
        existing.put("leave_years/2026.json", testLedger(2026, "2026-01-11"))
        assertTrue(runBlocking { coordinator.publish(existing, null, "base") } is PublishResult.Published)

        val vm = viewModel(coordinator, FakeCreation())
        vm.begin(proposal(), resumeDate = null)
        vm.awaitIdle()
        vm.onChooseCarryover()
        vm.awaitIdle()
        val reviewed = vm.state.value.carryoverPreview?.previewFingerprint

        // A competing change republishes the source with different bytes,
        // advancing the generation like another writer would.
        val baseBefore = (coordinator.open() as OpenResult.Ready).snapshot.generationId
        val changed = StoreCandidate()
        changed.put("config.json", "{\"schema_version\":1,\"defaults\":{}}")
        changed.put(
            "leave_years/2026.json",
            testLedger(2026, "2026-01-11").replace("\"sick_leave_accrual_hours\":4", "\"sick_leave_accrual_hours\":6"),
        )
        assertTrue(runBlocking { coordinator.publish(changed, baseBefore, "base2") } is PublishResult.Published)

        vm.onCarryoverConfirm()
        vm.awaitIdle()

        val state = vm.state.value
        assertTrue(state.stale)
        assertFalse(state.published)
        assertTrue(state.carryoverPreview?.previewFingerprint != reviewed)
        assertFalse((coordinator.open() as OpenResult.Ready).snapshot.files.containsKey("leave_years/2027.json"))

        // A fresh confirmation against the refreshed preview publishes once.
        vm.onCarryoverConfirm()
        vm.awaitIdle()
        assertTrue(vm.state.value.published)
    }

    @Test
    fun carryoverConfirmRejectsAnExistingDestination() = runBlocking {
        val root = tmp.newFolder("store")
        val coordinator = StoreCoordinator(root)
        val existing = StoreCandidate()
        existing.put("config.json", "{\"schema_version\":1,\"defaults\":{}}")
        existing.put("leave_years/2026.json", testLedger(2026, "2026-01-11"))
        existing.put("leave_years/2027.json", testLedger(2027, "2027-01-10"))
        assertTrue(runBlocking { coordinator.publish(existing, null, "base") } is PublishResult.Published)
        val destinationBefore = (coordinator.open() as OpenResult.Ready).snapshot.files["leave_years/2027.json"]

        val vm = viewModel(coordinator, FakeCreation())
        vm.begin(proposal(), resumeDate = null)
        vm.awaitIdle()
        vm.onChooseCarryover()
        vm.awaitIdle()
        vm.onCarryoverConfirm()
        vm.awaitIdle()

        assertFalse(vm.state.value.published)
        assertEquals("DESTINATION_EXISTS", (vm.state.value.error as? com.westmouthbay.useorlose.core.CoreError.Read)?.code)
        assertEquals(destinationBefore, (coordinator.open() as OpenResult.Ready).snapshot.files["leave_years/2027.json"])
    }

    @Test
    fun cancelAndPreviewWriteNothing() = runBlocking {
        val root = tmp.newFolder("store")
        val coordinator = StoreCoordinator(root)
        val existing = StoreCandidate()
        existing.put("config.json", "{\"schema_version\":1,\"defaults\":{}}")
        existing.put("leave_years/2026.json", testLedger(2026, "2026-01-11"))
        assertTrue(
            runBlocking { coordinator.publish(existing, null, "base") } is PublishResult.Published,
        )
        val before = storeTree(root)

        val bridge = FakeCreation()
        val vm = viewModel(coordinator, bridge)
        vm.begin(proposal(), resumeDate = null)
        vm.onChooseStartOfYear()
        vm.onPreview()
        vm.awaitIdle()
        vm.onCancel()

        assertEquals(before, storeTree(root))
        assertFalse(vm.state.value.published)
    }

    @Test
    fun staleBaseForcesAReviewAndWritesNothing() = runBlocking {
        val root = tmp.newFolder("store")
        val coordinator = StoreCoordinator(root)
        val base = StoreCandidate()
        base.put("config.json", "{\"schema_version\":1,\"defaults\":{}}")
        base.put("leave_years/2026.json", testLedger(2026, "2026-01-11"))
        assertTrue(
            runBlocking { coordinator.publish(base, null, "base") } is PublishResult.Published,
        )

        val bridge = FakeCreation()
        val vm = viewModel(coordinator, bridge)
        vm.begin(proposal(), resumeDate = null)
        vm.onChooseStartOfYear()
        vm.onPreview()
        vm.awaitIdle()

        // A competing publish changes the active generation.
        val current = (coordinator.open() as OpenResult.Ready).snapshot.generationId
        val competing = StoreCandidate()
        competing.put("config.json", "{\"schema_version\":1,\"defaults\":{\"workday_start\":\"08:00\"}}")
        competing.put("leave_years/2026.json", testLedger(2026, "2026-01-11"))
        assertTrue(
            runBlocking {
                coordinator.publish(competing, expectedBase = current, fingerprint = "competing")
            } is PublishResult.Published,
        )

        vm.onConfirm()
        vm.awaitIdle()

        assertFalse(vm.state.value.published)
        assertTrue(vm.state.value.stale)
        val files = (coordinator.open() as OpenResult.Ready).snapshot.files
        assertFalse(files.containsKey("leave_years/2027.json"))
    }

    @Test
    fun midnightRolloverRefreshesThePreview() = runBlocking {
        val root = tmp.newFolder("store")
        val coordinator = StoreCoordinator(root)
        val bridge = FakeCreation()
        var today = "2026-01-16"
        val vm = SetupViewModel(
            creation = bridge,
            store = coordinator,
            timeSource = TimeSource { LocalDate.parse(today) },
            providedScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob()),
        )
        vm.begin(proposal(), resumeDate = null)
        vm.onChooseStartOfYear()
        vm.onPreview()
        vm.awaitIdle()
        assertEquals(1, bridge.requests.size)

        today = "2026-01-17"
        vm.onConfirm()
        vm.awaitIdle()

        assertTrue(vm.state.value.stale)
        // The preview refreshed with the new local day.
        assertTrue(bridge.requests.size >= 2)
    }

    @Test
    fun duplicateConfirmationCannotPublishTwice() = runBlocking {
        val root = tmp.newFolder("store")
        val coordinator = StoreCoordinator(root)
        val bridge = FakeCreation().apply { includeConfig = true }
        val vm = viewModel(coordinator, bridge)
        vm.begin(proposal(), resumeDate = null)
        vm.onChooseStartOfYear()
        vm.onPreview()
        vm.awaitIdle()
        vm.onConfirm()
        vm.awaitIdle()
        assertTrue(vm.state.value.published)

        vm.onConfirm()
        vm.awaitIdle()
        // A repeated confirmation is a no-op: the first ledger stands.
        assertTrue(vm.state.value.published)
        assertFalse(vm.state.value.stale)
        val generations = (coordinator.open() as OpenResult.Ready).snapshot.files
            .count { it.key == "leave_years/2027.json" }
        assertEquals(1, generations)
    }

    @Test
    fun invalidHoursAreRejectedWithoutAPreviewCall() = runBlocking {
        val root = tmp.newFolder("store")
        val coordinator = StoreCoordinator(root)
        val bridge = FakeCreation()
        val vm = viewModel(coordinator, bridge)
        vm.begin(proposal(), resumeDate = null)
        vm.onChooseStartOfYear()
        vm.onOpeningChanged("annual", "abc")
        vm.onPreview()
        vm.awaitIdle()

        assertNull(vm.state.value.preview)
        assertEquals("INVALID_REQUEST", vm.state.value.error?.code)
        assertTrue(bridge.requests.isEmpty())
    }

    @Test
    fun todayMidnightRolloverRePreviewsAsToday() = runBlocking {
        val root = tmp.newFolder("store")
        val coordinator = StoreCoordinator(root)
        val bridge = FakeCreation()
        var today = "2026-02-10"
        val vm = SetupViewModel(
            creation = bridge,
            store = coordinator,
            timeSource = TimeSource { LocalDate.parse(today) },
            providedScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob()),
        )
        vm.begin(proposal(), resumeDate = null)
        vm.onChooseToday()
        vm.onTargetChanged("annual", "80")
        vm.onPreview()
        vm.awaitIdle()
        assertEquals("today", bridge.requests.last().first)

        today = "2026-02-11"
        vm.onConfirm()
        vm.awaitIdle()

        assertTrue(vm.state.value.stale)
        // The rollover re-preview keeps the Today kind and its targets.
        assertEquals("today", bridge.requests.last().first)
    }

    @Test
    fun reEnteringSetupAfterPublishStartsClean() = runBlocking {
        val root = tmp.newFolder("store")
        val coordinator = StoreCoordinator(root)
        val bridge = FakeCreation().apply { includeConfig = true }
        val vm = viewModel(coordinator, bridge)
        vm.begin(proposal(), resumeDate = null)
        vm.onChooseStartOfYear()
        vm.onPreview()
        vm.awaitIdle()
        vm.onConfirm()
        vm.awaitIdle()
        assertTrue(vm.state.value.published)

        vm.begin(proposal(), resumeDate = null)
        val state = vm.state.value
        assertFalse(state.published)
        assertEquals(SetupStep.METHOD, state.step)
        assertNull(state.preview)
    }

    @Test
    fun recoverableStoreBlocksPublishAndPreservesTheGenerations() = runBlocking {
        val root = tmp.newFolder("store")
        val coordinator = StoreCoordinator(root)
        // Publish a valid generation, then corrupt the pointer.
        val existing = StoreCandidate()
        existing.put("config.json", "{\"schema_version\":1,\"defaults\":{}}")
        existing.put("leave_years/2026.json", testLedger(2026, "2026-01-11"))
        assertTrue(
            runBlocking { coordinator.publish(existing, null, "base") } is PublishResult.Published,
        )
        val pointer = File(root, "active.json")
        pointer.writeText("{ not json")

        val bridge = FakeCreation()
        val vm = viewModel(coordinator, bridge)
        vm.begin(proposal(), resumeDate = null)
        vm.onChooseStartOfYear()
        vm.onPreview()
        vm.awaitIdle()
        vm.onConfirm()
        vm.awaitIdle()

        assertEquals("STORE_UNAVAILABLE", vm.state.value.error?.code)
        assertFalse(vm.state.value.published)
        // The complete recoverable generation survives.
        assertTrue(File(root, "generations").exists())
        assertTrue(File(root, "generations").listFiles()?.isNotEmpty() == true)
    }
}
