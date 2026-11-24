# I04 · Private JSON store and recovery

**Handoff draft.** Bind the actual I01 package path, I02 bridge and I03 facade before assignment. This milestone adds the app-private canonical store, one serialized writer, generation recovery and a developer-only fixture entry. It does not add restore, share or product screens. [Canonical storage](../../specification/chapters/04-architecture/04-02-device-storage.md#canonical-private-storage) · [Write and recovery model](../../specification/chapters/04-architecture/04-02-device-storage.md#write-and-recovery-model)

## Assignment

**Risk and ownership.** R3. Storage generations and recovery stay design-owner and Sol-led through the pilot. Require explicit design admission and the delegated manifest before assignment. [Handoff gate](../../specification/chapters/07-organization/07-03-design-and-implementation-handoffs.md) · [Risk classes](../../specification/chapters/07-organization/07-02-multi-model-development-workflow.md#roles-and-delegation)

**Starting state.** I02's bridge decision and I03's read facade at their recorded commits. The [Calendar fixture manifest](../../qa/fixtures/calendar-2026.manifest.json) and the [storage defect manifest](../../qa/fixtures/storage/manifest.json). Record the exact revisions at assignment.

**Read in this order.**

1. [Canonical private storage](../../specification/chapters/04-architecture/04-02-device-storage.md#canonical-private-storage) and [write and recovery model](../../specification/chapters/04-architecture/04-02-device-storage.md#write-and-recovery-model).
2. The [read/write boundary](../../specification/chapters/04-architecture/04-01-core-and-compatibility.md#responsibilities-and-data-flow) and the I03 report.
3. [Developer setup and testing](../../specification/chapters/05-quality/05-01-developer-setup-and-testing.md) for device and restart testing.
4. [Storage and interchange acceptance](../../specification/verification/acceptance-tests.md#storage-and-interchange-acceptance), limited to the storage cases that apply here.
5. [`src/fedleave/storage.py`](../../../src/fedleave/storage.py) for canonical file handling and [`src/fedleave/config.py`](../../../src/fedleave/config.py) for schema defaults.

**Expected changes.** Add storage code under the I01 application package, for example `storage/StorePaths.kt`, `StorageSnapshot.kt`, `StoreCandidate.kt`, `StoreValidator.kt`, `StoreCoordinator.kt`, `Recovery.kt` and `AtomicPointer.kt`. Add unit tests under `app/src/test/` and device tests under `app/src/androidTest/`. Add a debug-only developer fixture entry and its test assets. Add the backup exclusion to the manifest and XML rules. Record every actual path in the I04 report.

**Preserve.** The I03 read contract, the I02 bridge, desktop CLI and GUI behavior, the canonical JSON schema, and all release UI. Do not add document-picker, share, restore, migration or setup flows. Do not rewrite the active store merely to display data.

## Behavior and contract

Use `context.noBackupFilesDir/fedleave/` for the working store and recovery data. Exclude it from cloud backup and device transfer with `android:allowBackup="false"` and the version-appropriate extraction rules. Keep local journals, candidates, presentation settings and temporary artifacts outside the canonical store.

```text
no_backup/fedleave/
  active.json                         # {"schema_version":1,"generation_id":"<sha256>","committed_at":"<ISO>"}
  generations/<generation_id>/
    config.json
    leave_years/<year>.json
    holiday_cache/<cache-name>.json
  candidates/<candidate_id>/
  recovery.json                       # optional unresolved recovery metadata
```

Define the **generation identity** as the manifest tree digest: SHA-256 over the sorted lines `relative_path + "\0" + sha256(file bytes) + "\n"`. The pinned fixture's identity is `03e9e1757f73015b356b900703a13a88390e854f274c7bce78bd2f9f00c7aea9`. Store generations under that identity so identical candidates are idempotent. Keep the active pointer and recovery metadata outside the canonical payload.

Expose one process-wide coordinator with serialized writes and immutable reads:

```kotlin
sealed interface OpenResult {
    data class Ready(val snapshot: StorageSnapshot) : OpenResult
    data class Recoverable(val choices: List<GenerationRef>) : OpenResult
    data object Empty : OpenResult
}

data class StorageSnapshot(
    val generationId: String,
    val files: Map<String, String>,
)

class StoreCoordinator(...) {
    suspend fun open(): OpenResult
    suspend fun beginCandidate(baseGenerationId: String?): StoreCandidate
    suspend fun publish(candidate: StoreCandidate, expectedBase: String?, fingerprint: String): PublishResult
    suspend fun recover(generationId: String): OpenResult
    // debug/test source set only
    suspend fun importDeveloperFixture(assetPath: String): PublishResult
}
```

Rules:

- `files` keys are canonical relative paths only: `config.json`, `leave_years/<digits>.json` and `holiday_cache/<safe-name>.json`. Reject absolute paths, `..`, unknown directories, non-JSON names and duplicate normalized keys.
- `beginCandidate` copies or overlays the base generation into staging. Edits are candidate-only until `publish`.
- `publish` validates the complete candidate before writing. Require strict UTF-8 JSON objects, `schema_version` 1, a config object, at least one leave-year file with filename/year agreement, unique year-scoped transaction IDs, finite numeric hours, parseable ordered start/end dates and matching pay-period bounds. The first setup write creates the config and its first year together.
- Write and flush every candidate file, then publish the pointer with an atomic replacement and durable flush. The prior validated generation remains readable.
- If `expectedBase` differs from the current active generation, return `StaleBase` without writing. This is the same fingerprint rule previews use.
- A crash before pointer publication leaves the prior generation active. A crash after publication exposes the new complete store and must not replay the write.
- On open, validate the pointer target. If it is missing or invalid, return `Recoverable` with every complete generation. Preserve both candidates while a choice is unresolved. Never auto-apply an operation during recovery.
- Retain the active generation plus one prior complete generation. Clean abandoned candidates and older generations only after a successful publication. On low space or an I/O failure, leave the current store usable and report the failed step.
- Reads never write: `open()` may read the pointer and files but must not repair, migrate or update timestamps.
- The developer fixture entry exists only in the debug source set or a debug-only test. It imports `android/qa/fixtures/calendar-2026` or a named defect fixture into a candidate and publishes it. It injects the fixed local date `2026-01-16` through `java.time.Clock`; no fixture entry is reachable in a release build.

Use a test-only filesystem or fault-injection seam to fail at staging, before pointer publication, after pointer publication but before success, and on low space. Production code must not read a test control flag to choose failure.

## Screen and resource checklist

No product screen. The only reachable surface is the debug developer entry, labeled **Developer fixture** and absent from release. It may show the imported generation identity and the resolved fixture date without exposing filesystem paths. I05 starts product navigation.

## Verification

Run the desktop fixture builder and comparison first:

```bash
.venv/bin/python android/qa/fixtures/build_calendar_fixture.py
QT_QPA_PLATFORM=offscreen .venv/bin/python -m pytest -q
```

Then run the I01/I02 Android tasks from `android/` plus the storage test classes:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

| Case | Expected result |
| --- | --- |
| Fixture publication | Importing `android/qa/fixtures/calendar-2026` yields generation `03e9e1757f73015b356b900703a13a88390e854f274c7bce78bd2f9f00c7aea9`; the canonical files equal the fixture bytes and the I03 facade returns the golden balances. |
| Read-only | Opening and reading the store, then resolving balances, leaves the tree bytes unchanged. |
| Process restart | A new coordinator instance opens the same generation; the imported store survives process death and relaunch. |
| Update retention | Publishing a second validated candidate keeps the previous generation recoverable, then retains exactly the active plus one prior after cleanup. |
| Stale base | A candidate based on the old generation is rejected after another publish; no bytes change. |
| Interruption | Staging, pre-pointer and post-pointer failures yield the prior or new complete store respectively; no operation applies twice. |
| Defect fixtures | Every case in the [storage manifest](../../qa/fixtures/storage/manifest.json) is rejected at the documented stage; the active store and both recovery candidates stay unchanged. |
| Recovery | A corrupt or missing pointer exposes complete generations for an explicit choice; unresolved recovery preserves both; selecting one opens a complete store. |
| Low space | A simulated `IOException` or full disk leaves the current data readable and reports retryable failure. |
| Backup exclusion | The canonical path is under `noBackupFilesDir`; the manifest and extraction rules exclude it. Record the platform-level backup check as pending for I19 if no device matrix is available. |

Record the case table, hashes, fault points and generation identities in `android/qa/reports/I04.md`; the independent review in `android/reviews/code/I04.md`; and resume notes in `android/implementation/passdowns/I04.md`. I19 later reruns the install, uninstall and real backup/transfer matrix. [QA gates](../../specification/chapters/05-quality/05-02-quality-gates.md#required-gates)

## Handoff

Deliver the coordinator, snapshot and recovery API, the canonical layout, the generation identity algorithm, the validated fixture store and the defect results. I05 reads only through `open()` and the I03 facade and never touches storage paths. I06 publishes setup through `publish`. I16 and I17 reuse the same candidate and publisher for backup and restore. Record any schema or recovery contract change as a specification escalation before accepting it.
