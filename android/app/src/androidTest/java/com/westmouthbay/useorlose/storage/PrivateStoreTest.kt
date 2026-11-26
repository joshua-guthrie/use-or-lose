package com.westmouthbay.useorlose.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class PrivateStoreTest {

    private val pinnedDigest = "03e9e1757f73015b356b900703a13a88390e854f274c7bce78bd2f9f00c7aea9"

    private val fixtureNames = listOf(
        "config.json",
        "leave_years/2026.json",
        "holiday_cache/federal_holidays_2026.json",
    )

    private fun targetContext() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun assetsContext() = InstrumentationRegistry.getInstrumentation().context

    @Before
    fun resetStoreBetweenSuites() {
        // The I05 UI suites also publish fixture stores into the shared app
        // data directory; reset it so imports below always start clean and
        // test order cannot affect the result.
        StorePaths.forContext(targetContext()).deleteRecursively()
    }

    private fun tree(dir: File): Map<String, String> =
        Files.walk(dir.toPath())
            .filter { Files.isRegularFile(it) }
            .toList()
            .associate { it.toString() to it.toFile().readText(Charsets.UTF_8) }

    @Test
    fun fixtureImportsUnderNoBackupDirectoryAndReopens() = runBlocking {
        val storeRoot = StorePaths.forContext(targetContext())
        val coordinator = StoreCoordinator(storeRoot)

        val result = DeveloperFixture.importNamedAssets(assetsContext(), coordinator, fixtureNames)
        assertTrue(result is PublishResult.Published)
        assertEquals(pinnedDigest, (result as PublishResult.Published).generationId)

        val noBackup = targetContext().noBackupFilesDir.absolutePath
        assertTrue(storeRoot.absolutePath.startsWith(noBackup))

        val before = tree(storeRoot)
        val reopened = StoreCoordinator(storeRoot).open()
        assertTrue(reopened is OpenResult.Ready)
        assertEquals(pinnedDigest, (reopened as OpenResult.Ready).snapshot.generationId)
        assertEquals(before, tree(storeRoot))
    }

    @Test
    fun malformedCandidateIsRejectedWithoutReplacingTheStore() = runBlocking {
        val storeRoot = StorePaths.forContext(targetContext())
        val coordinator = StoreCoordinator(storeRoot)
        DeveloperFixture.importNamedAssets(assetsContext(), coordinator, fixtureNames)

        val candidate = StoreCandidate()
        for (name in fixtureNames) {
            candidate.put(name, assetsContext().assets.open(name).use { it.readBytes().decodeToString() })
        }
        candidate.put(
            "leave_years/2026.json",
            """{"schema_version": 1, "leave_year": 2026""",
        )

        val rejected = coordinator.publish(candidate, pinnedDigest, "test")
        assertTrue(rejected is PublishResult.Rejected)

        val reopened = StoreCoordinator(storeRoot).open()
        assertTrue(reopened is OpenResult.Ready)
        assertEquals(pinnedDigest, (reopened as OpenResult.Ready).snapshot.generationId)
    }

    @Test
    fun invalidUtf8FixtureFailsStrictDecoding() {
        val bytes = assetsContext().assets.open("invalid-utf8/leave_years/2026.json").use { it.readBytes() }
        val failure = runCatching { StoreValidator.decodeUtf8Strict(bytes) }.exceptionOrNull()
        assertTrue(failure is java.nio.charset.CharacterCodingException)
    }
}
