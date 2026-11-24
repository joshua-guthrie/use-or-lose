package com.westmouthbay.fedleave.storage

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class StoreCoordinatorTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private val pinnedDigest = "03e9e1757f73015b356b900703a13a88390e854f274c7bce78bd2f9f00c7aea9"

    private fun fixtureRoot(): File =
        listOf(File("qa/fixtures/calendar-2026"), File("../qa/fixtures/calendar-2026"))
            .firstOrNull { it.isDirectory } ?: error("Calendar fixture not found")

    private fun storageRoot(): File =
        listOf(File("qa/fixtures/storage"), File("../qa/fixtures/storage"))
            .firstOrNull { it.isDirectory } ?: error("Storage fixtures not found")

    private fun fixtureFiles(): Map<String, String> = mapOf(
        "config.json" to File(fixtureRoot(), "config.json").readText(Charsets.UTF_8),
        "leave_years/2026.json" to File(fixtureRoot(), "leave_years/2026.json").readText(Charsets.UTF_8),
        "holiday_cache/federal_holidays_2026.json" to
            File(fixtureRoot(), "holiday_cache/federal_holidays_2026.json").readText(Charsets.UTF_8),
    )

    private fun defectFiles(variant: String): Map<String, String> {
        val base = File(storageRoot(), variant)
        val names = listOf("config.json", "leave_years/2026.json", "holiday_cache/federal_holidays_2026.json")
        return names.mapNotNull { name ->
            val file = File(base, name)
            if (file.isFile) name to file.readText(Charsets.UTF_8) else null
        }.toMap()
    }

    private fun coordinator(
        dir: File,
        io: StoreIo = DiskStoreIo(),
        clock: Clock = Clock.fixed(Instant.parse("2026-01-16T00:00:00Z"), ZoneOffset.UTC),
    ) = StoreCoordinator(dir, io, clock)

    private fun candidate(files: Map<String, String>): StoreCandidate =
        StoreCandidate().apply { files.forEach { (name, text) -> put(name, text) } }

    private fun tree(dir: File): Map<String, String> {
        val paths = Files.walk(dir.toPath()).filter { Files.isRegularFile(it) }.toList()
        return paths.associate { it.toString() to it.toFile().readText(Charsets.UTF_8) }
    }

    @Test
    fun publishesPinnedFixtureWithPinnedDigest() = runBlocking {
        val root = temporary.newFolder()
        val store = coordinator(root)
        val result = store.publish(candidate(fixtureFiles()), null, "")
        assertTrue("result=$result", result is PublishResult.Published)
        assertEquals(pinnedDigest, (result as PublishResult.Published).generationId)

        val opened = store.open()
        assertTrue(opened is OpenResult.Ready)
        val snapshot = (opened as OpenResult.Ready).snapshot
        assertEquals(pinnedDigest, snapshot.generationId)
        assertEquals("keys", fixtureFiles().keys, snapshot.files.keys)
        fixtureFiles().forEach { (key, value) ->
            assertEquals("content $key", value, snapshot.files[key])
        }
    }

    @Test
    fun readsNeverMutateTheStore() = runBlocking {
        val root = temporary.newFolder()
        val store = coordinator(root)
        store.publish(candidate(fixtureFiles()), null, "")
        val before = tree(File(root, "generations"))
        repeat(3) {
            val opened = store.open()
            assertTrue(opened is OpenResult.Ready)
        }
        assertEquals(before, tree(File(root, "generations")))
    }

    @Test
    fun rejectsStaleBaseWithoutWriting() = runBlocking {
        val root = temporary.newFolder()
        val store = coordinator(root)
        val first = store.publish(candidate(fixtureFiles()), null, "")
        assertTrue(first is PublishResult.Published)

        val stale = store.publish(candidate(fixtureFiles()), null, "")
        assertEquals(PublishResult.StaleBase, stale)

        val fresh = store.publish(candidate(fixtureFiles()), pinnedDigest, "")
        assertTrue(fresh is PublishResult.Published)
    }

    @Test
    fun rejectsMalformedCandidateAndKeepsPriorStore() = runBlocking {
        val root = temporary.newFolder()
        val store = coordinator(root)
        store.publish(candidate(fixtureFiles()), null, "")
        val broken = candidate(fixtureFiles())
        broken.put("leave_years/2026.json", """{"schema_version": 1, "leave_year": 2026""")

        val result = store.publish(broken, pinnedDigest, "")
        assertTrue(result is PublishResult.Rejected)
        assertTrue((result as PublishResult.Rejected).issues.isNotEmpty())

        val opened = store.open()
        assertTrue(opened is OpenResult.Ready)
        assertEquals(pinnedDigest, (opened as OpenResult.Ready).snapshot.generationId)
    }

    @Test
    fun defectFixturesAreRejected() = runBlocking {
        val root = temporary.newFolder()
        val store = coordinator(root)
        store.publish(candidate(fixtureFiles()), null, "")
        for (variant in listOf("future-schema", "invalid-values", "duplicate-ids", "missing-ledger", "malformed-json")) {
            val result = store.publish(candidate(defectFiles(variant)), pinnedDigest, "")
            assertTrue("$variant must be rejected", result is PublishResult.Rejected)
        }
        val opened = store.open()
        assertEquals(pinnedDigest, (opened as OpenResult.Ready).snapshot.generationId)
    }

    @Test
    fun invalidUtf8FailsStrictDecoding() {
        val bytes = File(storageRoot(), "invalid-utf8/leave_years/2026.json").readBytes()
        val failure = runCatching { StoreValidator.decodeUtf8Strict(bytes) }.exceptionOrNull()
        assertTrue(failure is java.nio.charset.CharacterCodingException)
    }

    @Test
    fun interruptionLeavesPriorStoreUsable() = runBlocking {
        val root = temporary.newFolder()
        val store = coordinator(root)
        store.publish(candidate(fixtureFiles()), null, "")
        val changed = fixtureFiles() + ("holiday_cache/extra.json" to """{"schema_version":1,"holidays":[{"name":"Test"}]}""")
        val second = candidate(changed)

        val failingWrites = coordinator(root, FaultyStoreIo(failWritesAfter = 1))
        val stagingFailure = failingWrites.publish(second, pinnedDigest, "")
        assertTrue(stagingFailure is PublishResult.Failed)
        assertTrue(failingWrites.open() is OpenResult.Ready)

        val failingMove = coordinator(root, FaultyStoreIo(failMove = true))
        val prePointerFailure = failingMove.publish(second, pinnedDigest, "")
        assertTrue(prePointerFailure is PublishResult.Failed)
        assertEquals(pinnedDigest, (failingMove.open() as OpenResult.Ready).snapshot.generationId)

        val crashAfterPointer = coordinator(root, FaultyStoreIo(moveThenThrow = true))
        val postPointerFailure = crashAfterPointer.publish(second, pinnedDigest, "")
        assertTrue(postPointerFailure is PublishResult.Failed)
        val reopened = crashAfterPointer.open()
        assertTrue(reopened is OpenResult.Ready)
        assertTrue((reopened as OpenResult.Ready).snapshot.generationId != pinnedDigest)
    }

    @Test
    fun recoveryChoicesPreserveBothCandidates() = runBlocking {
        val root = temporary.newFolder()
        val store = coordinator(root)
        store.publish(candidate(fixtureFiles()), null, "")
        val firstId = pinnedDigest

        val changed = fixtureFiles() + ("holiday_cache/extra.json" to """{"schema_version":1,"holidays":[]}""")
        val second = store.publish(candidate(changed), firstId, "")
        assertTrue(second is PublishResult.Published)
        val secondId = (second as PublishResult.Published).generationId
        assertTrue(firstId != secondId)

        File(root, "active.json").writeText("garbage", Charsets.UTF_8)
        val opened = store.open()
        assertTrue(opened is OpenResult.Recoverable)
        val choices = (opened as OpenResult.Recoverable).choices.map { it.generationId }.toSet()
        assertTrue(choices.contains(firstId))
        assertTrue(choices.contains(secondId))

        val recovered = store.recover(firstId)
        assertTrue(recovered is OpenResult.Ready)
        assertEquals(firstId, (recovered as OpenResult.Ready).snapshot.generationId)
    }

    @Test
    fun retentionKeepsActivePlusOnePrior() = runBlocking {
        val root = temporary.newFolder()
        val store = coordinator(root)
        val first = store.publish(candidate(fixtureFiles()), null, "") as PublishResult.Published
        val second = store.publish(
            candidate(fixtureFiles() + ("holiday_cache/extra.json" to """{"schema_version":1,"holidays":[]}""")),
            first.generationId,
            "",
        ) as PublishResult.Published
        val third = store.publish(
            candidate(
                fixtureFiles() +
                    ("holiday_cache/extra.json" to """{"schema_version":1,"holidays":[{"name":"Test"}]}"""),
            ),
            second.generationId,
            "",
        ) as PublishResult.Published

        val generations = File(root, "generations").listFiles()!!.map { it.name }.toSet()
        assertEquals(setOf(second.generationId, third.generationId), generations)
    }

    @Test
    fun restartReopensTheSameGeneration() = runBlocking {
        val root = temporary.newFolder()
        coordinator(root).publish(candidate(fixtureFiles()), null, "")

        val reopened = coordinator(root).open()
        assertTrue(reopened is OpenResult.Ready)
        assertEquals(pinnedDigest, (reopened as OpenResult.Ready).snapshot.generationId)
    }

    @Test
    fun emptyStoreOpensEmpty() = runBlocking {
        val opened = coordinator(temporary.newFolder()).open()
        assertEquals(OpenResult.Empty, opened)
    }

    @Test
    fun fingerprintIsRecordedInThePointer() = runBlocking {
        val root = temporary.newFolder()
        val store = coordinator(root)
        store.publish(candidate(fixtureFiles()), null, "plan-fingerprint-1")
        val pointer = File(root, "active.json").readText(Charsets.UTF_8)
        assertTrue(pointer.contains("plan-fingerprint-1"))
        assertTrue(pointer.contains("2026-01-16T00:00:00Z"))
    }

    @Test
    fun nonCanonicalPathsAreRejectedByTheCandidate() {
        val candidate = StoreCandidate()
        val failure = runCatching { candidate.put("../escape.json", "{}") }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    private class FaultyStoreIo(
        private val delegate: DiskStoreIo = DiskStoreIo(),
        private val failWritesAfter: Int = Int.MAX_VALUE,
        private val failMove: Boolean = false,
        private val moveThenThrow: Boolean = false,
    ) : StoreIo by delegate {
        private var writes = 0

        override fun write(path: File, bytes: ByteArray) {
            writes += 1
            if (writes > failWritesAfter) throw IOException("injected write failure")
            delegate.write(path, bytes)
        }

        override fun move(source: File, target: File) {
            if (moveThenThrow) {
                delegate.move(source, target)
                throw IOException("injected post-move failure")
            }
            if (failMove) throw IOException("injected move failure")
            delegate.move(source, target)
        }
    }
}
