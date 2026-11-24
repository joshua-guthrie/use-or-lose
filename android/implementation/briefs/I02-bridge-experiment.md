# I02 · Bridge packaging experiment

**Handoff draft.** Bind the starting revision, actual I01 package paths, toolchain versions and bridge choice after I01 passes. This experiment decides whether the Android app can call the shared Python accounting core in process. It keeps the experiment behind a debug-only route and does not ship product UI. [Core experiment](../../specification/chapters/04-architecture/04-01-core-and-compatibility.md#proof-of-concept-exit-conditions) · [Bridge setup](../../specification/chapters/05-quality/05-01-developer-setup-and-testing.md#set-up-the-computer-and-first-run)

## Assignment

**Risk and ownership.** R2 exploratory integration. The design owner keeps this milestone because the bridge architecture is unapproved until its evidence is reviewed. Before delegated assignment, record the manifest identity and approvals from the [design and handoff gate](../../specification/chapters/07-organization/07-03-design-and-implementation-handoffs.md).

**Starting state.** I01's passed build at its recorded commit, QA report and toolchain record. The approved specification revision is the current checkpoint in [passdown 30](../../specification/passdowns/30-briefs-fixtures-and-readme.md). Record the exact values at assignment.

**Read in this order.**

1. [Shared core and compatibility](../../specification/chapters/04-architecture/04-01-core-and-compatibility.md), especially the recommendation, data flow and proof-of-concept exit conditions.
2. [Developer setup and testing](../../specification/chapters/05-quality/05-01-developer-setup-and-testing.md) for the host Python, device matrix and API floor.
3. [Milestone I02](../../specification/chapters/06-delivery/06-02-implementation-milestones.md#build-in-this-order) and [project organization](../../specification/chapters/07-organization/07-01-project-organization.md#directory-layout).
4. `calculate_balances` in [`src/fedleave/ledger.py`](../../../src/fedleave/ledger.py) and the effect rules in [`src/fedleave/transaction_effects.py`](../../../src/fedleave/transaction_effects.py).
5. The [Calendar fixture manifest](../../qa/fixtures/calendar-2026.manifest.json) and [`balance-2026-01-16.json`](../../qa/golden/calendar-2026/balance-2026-01-16.json).

**Expected changes.** Add Android-specific packaging configuration under `android/bridge/` (record the actual path and contract in the report) and the Chaquopy wiring in the I01 app module. Add a debug-only probe route and its instrumentation test. Copy or build-time sync the fixture from `android/qa/fixtures/calendar-2026/` into the Android test assets without hand-editing the source fixture. Record the fixture tree digest from the manifest in the report.

**Preserve.** Desktop CLI, GUI, packaging and `src/` behavior; the I01 launch screen and toolchain; the root `pyproject.toml` dependency set unless the owner approves a change; and the app's release manifest beyond new packaging configuration. Do not import terminal, MCP, Qt or desktop chart modules into the Android build graph.

## Behavior and contract

The experiment exposes one debug-only, read-only call. Treat the JSON shape as experimental and versioned; I03 replaces it with the final snapshot API.

```json
// Request, UTF-8 JSON, passed as one string
{"schema_version": 1, "as_of": "2026-01-16", "config": {"...": "..."}, "leave_year": {"...": "..."}}

// Success
{"schema_version": 1, "ok": true, "year": 2026, "as_of": "2026-01-16",
 "balances": {"annual": 90.0, "sick": 20.0, "credit": 0.0}}
```

The abbreviated response above shows two categories; the real `balances` object carries every category from the pinned golden file with exact finite hours. The probe calls the pure `calculate_balances` calculation on the supplied ledger and must not run `ensure_automatic_accruals`, write files, read the environment, use the network or spawn a process. The pinned ledger already contains its automatic accrual rows, so the result matches the golden file without seeding. A missing or malformed request, an unsupported `schema_version` or a non-object ledger returns a structured error:

```json
{"schema_version": 1, "ok": false, "error": {"code": "INVALID_REQUEST", "field": "leave_year", "message": "leave_year must be a JSON object."}}
```

Use only safe messages with no fixture paths, user data, stack traces or interpreter details. A request is never retried automatically; the debug route may retry on an explicit user action.

Record the bridge call path, Python version, the Chaquopy-supported runtime and every bundled native wheel. The app must start and complete the probe in airplane mode.

## Screen and resource checklist

This is not a product screen. Put the probe behind a debug build variant or an instrumentation test so release builds do not expose it. If a debug screen is used, label it **Bridge probe (debug)**, give the result a stable tag `bridge_probe_result`, show the pinned date and the annual and sick values, and state that it is not part of the release UI. No mockup or TalkBack product gate applies beyond basic readability.

Use the compact emulator profile and ARM64 handset recorded by I01. Record whether the emulator ABI is `x86_64` or `arm64-v8a`. Include a 16 KB page-size check on a compatible image or device.

## Verification

Run from `android/` with the I01-pinned JDK and the actual task names from its report:

```bash
./gradlew --version
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Add a packaged build (`bundleDebug` or the release task I01 established) and inspect its contents. Record actual output names and the signing limitation if release signing is not yet available.

| Check | Expected evidence |
| --- | --- |
| Bridge parity | The probe's full `balances` object equals the golden [January 16 balance](../../qa/golden/calendar-2026/balance-2026-01-16.json); annual is 90.0 h and sick is 20.0 h. |
| Offline startup | Cold launch and probe complete in airplane mode with no network permission or request. |
| Dependency boundary | The packaged app's Python modules contain no `typer`, `rich`, `mcp`, `PySide6`, `matplotlib` or desktop chart package; record the audit command and output. |
| Package and ABI | Record APK/AAB byte size, included ABIs and native libraries for `arm64-v8a` and the emulator ABI, and the 16 KB page-size result. |
| Performance | Record one cold dated lookup and the median of 20 warm lookups on the emulator and the handset, measured around the bridge call. |
| Process restart | Relaunch the app and repeat the probe; the result is stable and the fixture tree is unchanged. |
| Desktop reference | Reproduce the fixture with `python android/qa/fixtures/build_calendar_fixture.py`; the printed tree digest matches the manifest. |

Capture the probe result, resolved Python/Chaquopy versions, build log excerpt, native library list, package size and timing table in `android/qa/reports/I02.md`. Put the independent review in `android/reviews/code/I02.md` and resume notes in `android/implementation/passdowns/I02.md`. Baseline and comparison evidence must name the I01 commit, the fixture tree digest `03e9e1757f73015b356b900703a13a88390e854f274c7bce78bd2f9f00c7aea9`, the device profiles and the exact commands.

## Handoff

Deliver the tested bridge path and contract, the Python and native runtime record, size and timing evidence, the exact parity comparison and the dependency audit. The owner records one decision in the I02 report:

- **Confirmed:** I03 extracts the read-only core and reuses this bridge.
- **Rejected or blocked:** record the failing target or parity case, compare the repair cost with a Kotlin port under the same fixture contract, and do not start I03 until the owner records the replacement plan.

I03 depends on the recorded bridge path and execution contract. I04 depends on both I02 and I03. Leave I02 open if a required build, offline launch, parity or packaging check fails. [QA gates](../../specification/chapters/05-quality/05-02-quality-gates.md#required-gates)
