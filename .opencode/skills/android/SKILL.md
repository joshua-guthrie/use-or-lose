---
name: android
description: FedLeave Android app development. Use when working on the android/ directory, Gradle or Android SDK commands, Kotlin/Compose screens, the Chaquopy Python bridge, the app-private JSON store, or Android milestones I01-I20 and their QA/review/passdown flow.
---

# FedLeave Android development

## Toolchain on this machine

- Android SDK: `~/Android/Sdk` (cmdline-tools `latest`, platform-tools, `platforms;android-36`, `build-tools;36.0.0`, `emulator`, `system-images;android-36;google_apis;x86_64`)
- JDK: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`
- SDK tools: `~/Android/Sdk/cmdline-tools/latest/bin/{sdkmanager,avdmanager}`, `~/Android/Sdk/platform-tools/adb`
- Pinned versions: Gradle wrapper 8.14.3, AGP 8.13.2, Kotlin 2.2.20, Compose BOM 2025.06.01, Chaquopy 17.2.0 with Python 3.13, compileSdk/targetSdk 36, minSdk 26, application ID `com.westmouthbay.fedleave`
- Export `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` and put the repo `.venv/bin` on `PATH` before Gradle builds: Chaquopy's host Python must match the app Python 3.13.
- The bridge syncs a pinned file subset from `src/fedleave` into the app via the `prepareBridgePython` task. If the shared core's read path adds an import, extend the include list in `android/app/build.gradle.kts`. Keep pydantic out of the bundled subset.
- AVD: `fedleave_test` (pixel_5, API 36 `google_apis` x86_64, 1080x2340 @ 440 dpi, 1536 MB RAM). Start headless:
  `~/Android/Sdk/emulator/emulator -avd fedleave_test -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &`
- The host has 5.6 GB RAM. Stop the Gradle daemon (`./gradlew --stop`) before booting the emulator; System UI may show ANR dialogs under memory pressure (tap **Wait**, not Close). Dismiss airplane-mode ANRs the same way.

## Build and test

Run from `android/`:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest   # emulator or phone via adb
```

Desktop/shared core from the repository root:

```bash
QT_QPA_PLATFORM=offscreen .venv/bin/python -m pytest -q
make PYTHON=.venv/bin/python check
```

Rebuild pinned fixtures (never hand-edit generated files):

```bash
.venv/bin/python android/qa/fixtures/build_calendar_fixture.py
```

Expected fixture tree digest: `03e9e1757f73015b356b900703a13a88390e854f274c7bce78bd2f9f00c7aea9`.

## Workflow rules

- Read `android/README.md` and root `AGENTS.md` first. Update `android/README.md` in the same change when setup, build/test commands, toolchain, architecture, or layout changes.
- Milestones I01-I20: follow the brief in `android/implementation/briefs/`, record QA in `android/qa/reports/Ixx.md`, independent review in `android/reviews/code/Ixx.md`, resume notes in `android/implementation/passdowns/Ixx.md`, and update root `passdown.md`.
- Commit each verified increment locally. A source push to `master` requires the Distribution workflow and stable `beta` assets per AGENTS.md.
- Accounting, storage generations, restore and release stay with the stronger model and the owner; bounded UI/plumbing tasks can use the orchestration briefs.
- Fixture clock: the Calendar fixture freezes the local date to 2026-01-16 in `America/New_York`.
