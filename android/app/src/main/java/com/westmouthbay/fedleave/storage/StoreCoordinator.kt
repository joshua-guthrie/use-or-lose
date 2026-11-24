package com.westmouthbay.fedleave.storage

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Clock

class StoreCoordinator(
    private val root: File,
    private val io: StoreIo = DiskStoreIo(),
    private val clock: Clock = Clock.systemUTC(),
) {

    private val mutex = Mutex()

    suspend fun open(): OpenResult = mutex.withLock { openLocked() }

    suspend fun beginCandidate(baseGenerationId: String?): StoreCandidate {
        if (baseGenerationId != null) {
            val snapshot = openLocked()
            require(snapshot is OpenResult.Ready && snapshot.snapshot.generationId == baseGenerationId) {
                "Base generation $baseGenerationId is not the active generation."
            }
        }
        return StoreCandidate()
    }

    suspend fun publish(
        candidate: StoreCandidate,
        expectedBase: String?,
        fingerprint: String,
    ): PublishResult = mutex.withLock {
        val current = activeGenerationId()
        if (expectedBase != current) {
            return@withLock PublishResult.StaleBase
        }
        val files = candidate.files()
        val issues = StoreValidator.validate(files)
        if (issues.isNotEmpty()) {
            return@withLock PublishResult.Rejected(issues)
        }
        val generationId = StoreDigest.treeOfStrings(files)
        try {
            val generationDir = StorePaths.generationDir(root, generationId)
            for ((name, text) in files) {
                io.write(File(generationDir, name), text.toByteArray(Charsets.UTF_8))
            }
            val pointer = JSONObject()
                .put("schema_version", 1)
                .put("generation_id", generationId)
                .put("committed_at", clock.instant().toString())
                .put("fingerprint", fingerprint)
                .toString()
            val temporary = File(root, "active.json.tmp")
            io.write(temporary, pointer.toByteArray(Charsets.UTF_8))
            io.move(temporary, StorePaths.pointerFile(root))
        } catch (error: IOException) {
            return@withLock PublishResult.Failed(error.message ?: "store write failed")
        }
        cleanupLocked(keep = listOfNotNull(current, generationId))
        PublishResult.Published(generationId)
    }

    suspend fun recover(generationId: String): OpenResult = mutex.withLock {
        val generationDir = StorePaths.generationDir(root, generationId)
        val files = loadGeneration(generationDir)
        if (files == null) {
            return@withLock OpenResult.Recoverable(completeGenerations())
        }
        val pointer = JSONObject()
            .put("schema_version", 1)
            .put("generation_id", generationId)
            .put("committed_at", clock.instant().toString())
            .put("fingerprint", "")
            .toString()
        val temporary = File(root, "active.json.tmp")
        io.write(temporary, pointer.toByteArray(Charsets.UTF_8))
        io.move(temporary, StorePaths.pointerFile(root))
        openLocked()
    }

    private fun openLocked(): OpenResult {
        val pointerFile = StorePaths.pointerFile(root)
        if (!io.exists(pointerFile)) {
            val generations = completeGenerations()
            return if (generations.isEmpty()) OpenResult.Empty else OpenResult.Recoverable(generations)
        }
        val pointer = readPointer()
        val generationId = pointer?.optString("generation_id", "")?.takeIf { it.isNotEmpty() }
        if (generationId == null) {
            return OpenResult.Recoverable(completeGenerations())
        }
        val files = loadGeneration(StorePaths.generationDir(root, generationId))
        if (files == null) {
            return OpenResult.Recoverable(completeGenerations())
        }
        return OpenResult.Ready(StorageSnapshot(generationId, files))
    }

    private fun loadGeneration(generationDir: File): Map<String, String>? {
        if (!io.exists(generationDir)) return null
        val files = mutableMapOf<String, String>()
        for (entry in io.list(generationDir)) {
            val name = entry.name
            if (!StorePaths.isCanonical(name)) continue
            try {
                files[name] = StoreValidator.decodeUtf8Strict(io.read(entry))
            } catch (error: Exception) {
                return null
            }
        }
        for (sub in io.list(generationDir)) {
            val directoryName = sub.name
            if (directoryName != "leave_years" && directoryName != "holiday_cache") continue
            for (entry in io.list(sub)) {
                val name = "$directoryName/${entry.name}"
                if (!StorePaths.isCanonical(name)) continue
                try {
                    files[name] = StoreValidator.decodeUtf8Strict(io.read(entry))
                } catch (error: Exception) {
                    return null
                }
            }
        }
        if (files.isEmpty()) return null
        val issues = StoreValidator.validate(files)
        return if (issues.isEmpty()) files else null
    }

    private fun completeGenerations(): List<GenerationRef> {
        val generationsDir = StorePaths.generationsDir(root)
        if (!io.exists(generationsDir)) return emptyList()
        return io.list(generationsDir)
            .mapNotNull { dir ->
                val files = loadGeneration(dir)
                if (files == null) null else GenerationRef(dir.name, committedAtOf(dir.name))
            }
            .sortedByDescending { it.generationId }
    }

    private fun committedAtOf(generationId: String): String? {
        val pointer = readPointer()
        return if (pointer?.optString("generation_id") == generationId) {
            pointer.optString("committed_at", null)
        } else {
            null
        }
    }

    private fun activeGenerationId(): String? {
        val pointer = readPointer() ?: return null
        val id = pointer.optString("generation_id", "")
        return id.ifEmpty { null }
    }

    private fun readPointer(): JSONObject? {
        val pointerFile = StorePaths.pointerFile(root)
        if (!io.exists(pointerFile)) return null
        return try {
            JSONObject(StoreValidator.decodeUtf8Strict(io.read(pointerFile)))
        } catch (error: Exception) {
            null
        }
    }

    private fun cleanupLocked(keep: List<String>) {
        val keepSet = keep.filterNotNull().toSet()
        val generationsDir = StorePaths.generationsDir(root)
        if (io.exists(generationsDir)) {
            for (dir in io.list(generationsDir)) {
                if (dir.name !in keepSet) io.delete(dir)
            }
        }
        val candidatesDir = StorePaths.candidatesDir(root)
        if (io.exists(candidatesDir)) {
            for (file in io.list(candidatesDir)) io.delete(file)
        }
    }
}
