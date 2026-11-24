# FedLeave for Android

The phone app for FedLeave. It shares the desktop accounting core, keeps its working data in private desktop-compatible JSON, and reaches reports through native Compose screens. This directory is the Android Gradle root and the home of the phone specification, implementation briefs, fixtures and evidence.

## Status

The product specification, acceptance plan, orchestration rules and the first implementation briefs are complete. I01 has landed: `android/` is a working Gradle project with the single `app` module, pinned toolchain, launch screen and device smoke test. Open the directory in Android Studio to work on it.

| Track | State |
| --- | --- |
| Product specification and parity | Complete; menu review approved November 23, 2026 |
| Orchestration specification | Complete; OR01 pilot not started |
| Implementation briefs | I01-01 complete; I02–I05 plus I06-02 drafts ready |
| Android project | I01 complete — builds, smoke test and offline launch pass on the API 36 emulator |
| Fixtures | Calendar 2026 store, golden outputs and malformed-store variants under `qa/` |

Keep this file current. Each milestone replaces planned commands and toolchain values with the tested values from its report.

## Open and run in Android Studio

1. Install the current stable **Android Studio** for your platform, including the Android SDK, Platform-Tools and an emulator image.
2. **Open the `android/` directory, not the repository root.** `android/settings.gradle.kts` is the Gradle root and Studio discovers the single `app` module.
3. Let the Gradle sync finish. On Linux, verify hardware virtualization if the emulator is slow.
4. Create a compact phone AVD for the development host's architecture, or connect a physical phone with USB debugging enabled.
5. Select the device and run the `app` configuration.

The beginner walkthrough, SDK details and device matrix are in [Developer setup and testing](specification/chapters/05-quality/05-01-developer-setup-and-testing.md). The provisional minimum is **API 26**. The permanent app ID, signing identity and final API floor are recorded by I01 and I02.

The pinned versions below were verified by I01's report. A clean-sync launch from the Android Studio GUI has not been run on this machine; do it once when Studio is installed and update the QA report if anything differs.

### Pinned toolchain

| Item | Value |
| --- | --- |
| Application ID | `com.westmouthbay.fedleave` (owner-approved) |
| Android Gradle Plugin / Gradle | 8.13.2 / 8.14.3 (wrapper) |
| Kotlin / Compose BOM / Material 3 | 2.2.20 / 2025.06.01 / from BOM |
| JDK | 21 to run Gradle; source/target 17 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |
| Emulator | `fedleave_test` AVD, pixel_5, API 36 `google_apis` `x86_64`, 1080x2340 @ 440 dpi |
| Bridge runtime and Chaquopy | Pending I02 |

## Compile and test

Run from `android/` with `JAVA_HOME` pointing at the pinned JDK. On Windows use `gradlew.bat` for the same tasks. All of these passed in I01:

```bash
./gradlew --version
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

The connected test needs a booted emulator or a phone. On this machine, stop the Gradle daemon before starting the emulator to avoid memory pressure:

```bash
./gradlew --stop
~/Android/Sdk/emulator/emulator -avd fedleave_test -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &
```

Run the shared desktop core checks from the repository root. These work today:

```bash
QT_QPA_PLATFORM=offscreen .venv/bin/python -m pytest -q
make quality          # Ruff format/check and scoped strict mypy
make test             # behavior suite
make package          # Python sdist and wheel
```

Rebuild the pinned Android fixtures instead of editing them by hand:

```bash
.venv/bin/python android/qa/fixtures/build_calendar_fixture.py
```

The builder prints the fixture tree digest. It must stay `03e9e1757f73015b356b900703a13a88390e854f274c7bce78bd2f9f00c7aea9`; see [the manifest](qa/fixtures/calendar-2026.manifest.json).

Release builds, signing, Play uploads and device release rehearsal belong to I19 and I20. Do not commit `local.properties`, keystores, APKs, AABs or machine-specific SDK paths. [Google Play delivery](specification/chapters/06-delivery/06-01-google-play.md)

## Architecture

```mermaid
flowchart LR
    UI[Kotlin and Compose UI] --> Store[App-private canonical JSON store]
    UI --> Bridge[Typed Kotlin bridge]
    Bridge --> Core[Shared Python accounting core]
    CLI[Desktop CLI and GUI] --> Core
    CLI --> Desktop[Desktop datastore]
```

| Layer | Responsibility | Governing design |
| --- | --- | --- |
| Compose UI | Screens, navigation, accessibility, view state, lifecycle, clock injection, chart rendering, Sharesheet and document picker. | [Calendar](specification/chapters/01-calendar/01-01-main-screen.md) and [Reports](specification/chapters/03-reports-data/03-02-report-screens.md) |
| Kotlin bridge | One typed in-process interface to the Python core. Provisional until the I02 packaging experiment passes. | [Core and compatibility](specification/chapters/04-architecture/04-01-core-and-compatibility.md) |
| Shared Python core | Accounting rules, eligibility, accrual, leave-year boundaries, carryover, lots, parental windows, report inputs. Lives in the repository's root `src/`. | [Responsibilities and data flow](specification/chapters/04-architecture/04-01-core-and-compatibility.md#responsibilities-and-data-flow) |
| Private store | Canonical `config.json`, `leave_years/<year>.json` and `holiday_cache/*.json` in internal no-backup storage. One serialized writer, immutable read snapshots, candidate validation and generation recovery. | [Device storage](specification/chapters/04-architecture/04-02-device-storage.md) |
| Desktop | Existing CLI and GUI entry points remain the compatibility baseline and share the same accounting rules and datastore schema. | [Architecture](../docs/ARCHITECTURE.md) |

Rules that every change must respect:

- Reads use an immutable snapshot. Previews and confirmations share a fingerprint; a changed store forces a new review.
- One writer publishes complete candidate generations. A crash leaves the prior or new complete store, never a partial one.
- The phone is offline-first. No terminal, MCP, AI, Qt or desktop chart packages enter the Android dependency graph.
- Calendar PDF and report sharing are user-initiated Sharesheet handoffs. Printing is out of scope.
- The canonical store stays desktop-compatible; restore is the only inbound file workflow.

## Repository organization

| Path | Contents |
| --- | --- |
| `specification/specification.md` | Product scope, pinned desktop reference and chapter index |
| `specification/chapters/` | Normative requirements, screens, architecture, quality and delivery |
| `specification/verification/` | Feature parity matrix, acceptance cases, fixtures and exact outcomes |
| `specification/passdowns/` | Numbered specification-writing checkpoints |
| `implementation/briefs/` | One self-contained execution brief per bounded task |
| `implementation/passdowns/` | Coding-milestone handoffs once implementation starts |
| `settings.gradle.kts`, `gradle/`, `gradlew` | Android Gradle root and pinned wrapper (created by I01) |
| `app/` | Kotlin, Compose, resources, manifest, JVM and device tests (created by I01) |
| `bridge/` | Optional Android-specific Python packaging module if I02 needs it |
| `qa/fixtures/` | Synthetic input stores, the fixture builder and defect variants |
| `qa/golden/` | Expected desktop-derived outputs used by bridge and core tests |
| `qa/evidence/`, `qa/reports/` | Screenshots, logs and the per-milestone QA gate report |
| `reviews/code/` | Separate AI code review for each candidate |
| `deliverables/` | Release notes, artifact manifests and Play Console evidence |

The full ownership rules are in [Project organization](specification/chapters/07-organization/07-01-project-organization.md).

## Documentation map

| If you want to… | Start here |
| --- | --- |
| Read the phone behavior and designs | [Specification index](specification/specification.md) |
| Pick or resume implementation work | [Milestones](specification/chapters/06-delivery/06-02-implementation-milestones.md) · [Briefs](implementation/briefs/README.md) · [Root passdown](../passdown.md) |
| Check desktop coverage and exact expected values | [Feature parity](specification/verification/feature-parity.md) · [Acceptance tests](specification/verification/acceptance-tests.md) |
| Verify or review a candidate | [QA gates](specification/chapters/05-quality/05-02-quality-gates.md) · [QA template](qa/reports/template.md) · [Review template](reviews/code/template.md) |
| Plan a model handoff or the offline pilot | [Orchestration index](specification/orchestration.md) |
| Find release identities and storage conventions | [Deliverables](deliverables/README.md) · [Project organization](specification/chapters/07-organization/07-01-project-organization.md) |
| Follow repository rules for commits and publication | [AGENTS.md](../AGENTS.md) |

## Keeping this README current

Update this file in the same change whenever setup, build or test commands, toolchain versions, architecture, or directory layout changes. Each milestone closeout checks it; I01 replaces the placeholder toolchain, and I02 records the final bridge module and runtime. Do not leave stale instructions for a future milestone that has already landed.
