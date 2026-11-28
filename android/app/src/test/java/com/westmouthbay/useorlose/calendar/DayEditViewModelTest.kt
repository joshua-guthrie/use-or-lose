package com.westmouthbay.useorlose.calendar

import com.westmouthbay.useorlose.core.AddEntryPreview
import com.westmouthbay.useorlose.core.AddEntryRequest
import com.westmouthbay.useorlose.core.EditDayPreview
import com.westmouthbay.useorlose.core.EditDayRequest
import com.westmouthbay.useorlose.core.EditPreview
import com.westmouthbay.useorlose.core.EditResult
import com.westmouthbay.useorlose.core.EditingRead
import com.westmouthbay.useorlose.storage.OpenResult
import com.westmouthbay.useorlose.storage.PublishResult
import com.westmouthbay.useorlose.storage.StoreCandidate
import com.westmouthbay.useorlose.storage.StoreCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

private fun editableLedger(year: Int, start: String): String {
    val startDate = LocalDate.parse(start)
    val periods = JSONArray()
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
    val transactions = JSONArray()
    listOf(
        JSONObject()
            .put("id", "20260116-001")
            .put("date", "2026-01-16")
            .put("category", "annual")
            .put("direction", "used")
            .put("hours", 2.0)
            .put("description", "")
            .put("status", "approved")
            .put("source", "manual"),
        JSONObject()
            .put("id", "20260116-002")
            .put("date", "2026-01-16")
            .put("category", "annual")
            .put("direction", "used")
            .put("hours", 1.0)
            .put("description", "")
            .put("status", "approved")
            .put("source", "manual"),
        JSONObject()
            .put("id", "20260116-003")
            .put("date", "2026-01-16")
            .put("category", "sick")
            .put("direction", "used")
            .put("hours", 1.0)
            .put("description", "")
            .put("status", "approved")
            .put("source", "manual"),
    ).forEach { transactions.put(it) }
    val ledger = JSONObject()
        .put("schema_version", 1)
        .put("leave_year", year)
        .put("leave_year_start", start)
        .put("leave_year_end", currentStart.minusDays(1).toString())
        .put("pay_period_count", 26)
        .put("pay_periods", periods)
        .put("annual_leave_accrual_hours", 0.0)
        .put("sick_leave_accrual_hours", 0.0)
        .put("starting_balances", JSONObject().put("annual", 100.0).put("sick", 20.0))
        .put("carryover_from_previous_year", JSONObject())
        .put("transactions", transactions)
        .put("initialization", JSONObject())
        .put("rollover_status", JSONObject())
    return ledger.toString()
}

/**
 * The day editor state machine against the real I04 writer: group preview,
 * review with exact IDs, confirm, back-preserves-draft, mixed metadata and
 * stale bases. The fake editing bridge replaces the two annual-use rows
 * with one row carrying the reviewed values.
 */
class DayEditViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeEditing : EditingRead {
        val requests = mutableListOf<String>()

        override suspend fun previewEditDay(request: EditDayRequest): EditResult {
            requests += "edit:${request.category}/${request.direction}=${request.hours}"
            val ledger = JSONObject(request.ledgerJson)
            val rows = ledger.getJSONArray("transactions")
            val kept = JSONArray()
            val removed = mutableListOf<String>()
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                if (row.optString("date") == "2026-01-16" &&
                    row.optString("category") == request.category &&
                    row.optString("direction") == request.direction
                ) {
                    removed += row.getString("id")
                } else {
                    kept.put(row)
                }
            }
            if (request.hours > 0) {
                kept.put(
                    JSONObject()
                        .put("id", "20260116-009")
                        .put("date", "2026-01-16")
                        .put("category", request.category)
                        .put("direction", request.direction)
                        .put("hours", request.hours)
                        .put("description", request.comment ?: "")
                        .put("status", "reconciled")
                        .put("source", "set-day"),
                )
            }
            ledger.put("transactions", kept)
            return EditResult(
                files = mapOf("leave_years/${request.year}.json" to ledger.toString()),
                preview = EditPreview.Day(
                    EditDayPreview(
                        year = request.year,
                        date = request.date,
                        category = request.category,
                        direction = request.direction,
                        beforeHours = 3.0,
                        afterHours = request.hours,
                        removedTransactionIds = removed,
                        createdTransactionIds = if (request.hours > 0) listOf("20260116-009") else emptyList(),
                        comment = request.comment ?: "",
                        protectedRows = emptyList(),
                    ),
                ),
            )
        }

        override suspend fun previewAddEntry(request: AddEntryRequest): EditResult {
            requests += "add:${request.category}/${request.direction}=${request.hours}"
            val ledger = JSONObject(request.ledgerJson)
            val rows = ledger.getJSONArray("transactions")
            rows.put(
                JSONObject()
                    .put("id", "20260116-010")
                    .put("date", request.date.toString())
                    .put("category", request.category)
                    .put("direction", request.direction)
                    .put("hours", request.hours)
                    .put("description", request.comment)
                    .put("status", request.status)
                    .put("source", "manual"),
            )
            ledger.put("transactions", rows)
            return EditResult(
                files = mapOf("leave_years/${request.year}.json" to ledger.toString()),
                preview = EditPreview.Add(
                    AddEntryPreview(
                        year = request.year,
                        date = request.date,
                        category = request.category,
                        direction = request.direction,
                        hours = request.hours,
                        comment = request.comment,
                        status = request.status,
                        createdTransactionIds = listOf("20260116-010"),
                    ),
                ),
            )
        }
    }

    private fun storeTree(root: File): Map<String, String> =
        java.nio.file.Files.walk(root.toPath())
            .filter { java.nio.file.Files.isRegularFile(it) }
            .toList()
            .associate { it.toString() to it.toFile().readText(Charsets.UTF_8) }

    private fun viewModel(
        coordinator: StoreCoordinator,
        bridge: FakeEditing,
    ): DayEditViewModel = DayEditViewModel(
        editing = bridge,
        store = coordinator,
        providedScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob()),
    )

    private fun seededStore(root: File): StoreCoordinator {
        val coordinator = StoreCoordinator(root)
        val existing = StoreCandidate()
        existing.put("config.json", "{\"schema_version\":1,\"defaults\":{\"time_increment_hours\":0.25}}")
        existing.put("leave_years/2026.json", editableLedger(2026, "2026-01-11"))
        assertTrue(
            runBlocking { coordinator.publish(existing, null, "base") } is PublishResult.Published,
        )
        return coordinator
    }

    @Test
    fun editPreviewShowsExactIdsAndConfirmPublishesOnce() = runBlocking {
        val root = tmp.newFolder("store")
        val coordinator = seededStore(root)
        val vm = viewModel(coordinator, FakeEditing())
        vm.begin(LocalDate.parse("2026-01-16"))
        vm.awaitIdle()

        val state = vm.state.value
        assertEquals(2026, state.year)
        assertEquals(2, state.groups.size)
        vm.onSelectGroup("annual", "used")
        vm.onHoursChanged("3.5")
        vm.onCommentChanged("Appointment \"quoted\"")
        vm.onPreview()
        vm.awaitIdle()

        val preview = vm.state.value.preview as EditPreview.Day
        assertEquals(listOf("20260116-001", "20260116-002"), preview.value.removedTransactionIds)
        assertEquals(3.0, preview.value.beforeHours, 0.0001)
        assertEquals(3.5, preview.value.afterHours, 0.0001)

        vm.onConfirm()
        vm.awaitIdle()

        assertTrue(vm.state.value.published)
        val files = (coordinator.open() as OpenResult.Ready).snapshot.files
        assertTrue(files.containsKey("leave_years/2026.json"))
        val ledger = JSONObject(files.getValue("leave_years/2026.json"))
        val rows = ledger.getJSONArray("transactions")
        val annualUsed = (0 until rows.length()).map { rows.getJSONObject(it) }
            .filter { it.optString("date") == "2026-01-16" && it.optString("category") == "annual" && it.optString("direction") == "used" }
        assertEquals(1, annualUsed.size)
        assertEquals(3.5, annualUsed[0].getDouble("hours"), 0.0001)
        assertEquals("Appointment \"quoted\"", annualUsed[0].optString("description"))
        // The sick row is untouched.
        val sick = (0 until rows.length()).map { rows.getJSONObject(it) }
            .first { it.optString("category") == "sick" }
        assertEquals(1.0, sick.getDouble("hours"), 0.0001)
    }

    @Test
    fun backPreservesTheDraftAndWritesNothing() = runBlocking {
        val root = tmp.newFolder("store")
        val coordinator = seededStore(root)
        val before = storeTree(root)
        val vm = viewModel(coordinator, FakeEditing())
        vm.begin(LocalDate.parse("2026-01-16"))
        vm.awaitIdle()
        vm.onSelectGroup("annual", "used")
        vm.onHoursChanged("3.5")
        vm.onCommentChanged("Draft comment")
        vm.onPreview()
        vm.awaitIdle()
        vm.onBackToDraft()
        vm.awaitIdle()

        val state = vm.state.value
        assertEquals(DayEditStep.FORM, state.step)
        assertEquals("3.5", state.hoursText)
        assertEquals("Draft comment", state.comment)
        assertEquals(before, storeTree(root))
    }

    @Test
    fun clearingTheGroupRemovesTheRows() = runBlocking {
        val root = tmp.newFolder("store")
        val coordinator = seededStore(root)
        val vm = viewModel(coordinator, FakeEditing())
        vm.begin(LocalDate.parse("2026-01-16"))
        vm.awaitIdle()
        vm.onSelectGroup("annual", "used")
        vm.onHoursChanged("0")
        vm.onPreview()
        vm.awaitIdle()

        val preview = vm.state.value.preview as EditPreview.Day
        assertEquals(2, preview.value.removedTransactionIds.size)
        assertTrue(preview.value.createdTransactionIds.isEmpty())
        vm.onConfirm()
        vm.awaitIdle()
        val ledger = JSONObject(
            (coordinator.open() as OpenResult.Ready).snapshot.files.getValue("leave_years/2026.json"),
        )
        val rows = ledger.getJSONArray("transactions")
        val annualUsed = (0 until rows.length()).map { rows.getJSONObject(it) }
            .filter { it.optString("category") == "annual" && it.optString("direction") == "used" }
        assertTrue(annualUsed.isEmpty())
    }

    @Test
    fun uncoveredDateOffersSetupAndPreservesTheDraft() = runBlocking {
        val root = tmp.newFolder("store")
        val coordinator = seededStore(root)
        val vm = viewModel(coordinator, FakeEditing())
        vm.begin(LocalDate.parse("2027-02-01"))
        vm.awaitIdle()

        val state = vm.state.value
        assertTrue(state.yearMissing)
        assertNull(vm.state.value.year)
        val draft = vm.draft()
        assertNotNull(draft)
        assertEquals(LocalDate.parse("2027-02-01"), draft?.date)
    }

    @Test
    fun addEntryPublishesWithTheStoredDefaultStatus() = runBlocking {
        val root = tmp.newFolder("store")
        val coordinator = seededStore(root)
        val vm = viewModel(coordinator, FakeEditing())
        vm.begin(LocalDate.parse("2026-01-16"), mode = EditMode.ADD)
        vm.awaitIdle()
        vm.onSelectGroup("annual", "used")
        vm.onHoursChanged("1")
        vm.onCommentChanged("Follow-up")
        vm.onPreview()
        vm.awaitIdle()

        val preview = vm.state.value.preview as EditPreview.Add
        assertEquals("planned", preview.value.status)
        vm.onConfirm()
        vm.awaitIdle()
        assertTrue(vm.state.value.published)
    }

    private fun assertNull(value: Any?) {
        assertTrue(value == null)
    }
}
