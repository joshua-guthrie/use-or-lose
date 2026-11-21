# I01-01 · Start the Android project

**Draft pending owner approval and toolchain selection.** This milestone gives later briefs a reproducible Kotlin/Compose entry point. The implementation owner records an approved permanent application ID or uses an explicit prototype ID approved for this milestone. Bind the starting source and spec revisions in the task before it is assigned. [Developer setup and devices](../../specification/chapters/05-quality/05-01-developer-setup-and-testing.md)

## Files and deliverables

Open the repository's `android/` directory in Android Studio. Create the single `app` application module in that directory. Use Kotlin and Compose with Material 3. Put project configuration in `android/settings.gradle.kts`, root and app `build.gradle.kts` files, and Gradle wrapper files in `android/gradle/`, `android/gradlew`, and `android/gradlew.bat`. Use the toolchain combination reviewed for the development machine and record the exact Gradle, AGP, JDK, Kotlin and SDK versions, emulator image/API and ABI in `android/README.md` and QA evidence. API 26 is the provisional minimum. Confirm the chosen compile and target SDKs at assignment.

The Android manifest declares a launchable main activity. In the app namespace, add a main activity and a Compose theme and render a single screen titled **FedLeave** with readable text. Give the screen heading a stable ID for the device smoke test. This screen verifies the toolchain and app launch. Calendar starts in I05 and the Python bridge starts in I02. Leave the desktop entry points and packaging alone. The prototype needs no permissions, network access, shared files, production signing key, or user ledger.

## Verification

From `android/`, run these commands with Android Studio's pinned JDK and a booted emulator of the recorded profile. On Windows use `gradlew.bat` for the same tasks. Use the device actually selected for the test and record its model, Android version, density, and ABI.

```bash
./gradlew --version
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Add a single instrumentation smoke check that starts the real launch activity and finds the visible FedLeave heading. Confirm Android Studio can launch it from a clean Gradle sync. Reopen the app once with the emulator offline. Capture the screen and commands on the QA report. The snapshot is evidence for the prototype. Calendar visual acceptance begins with the I05 mockups.

## Handoff

Record source identity, toolchain and commands in `android/qa/reports/I01-01.md`, the separate review in `android/reviews/code/I01-01.md`, and resume notes in `android/implementation/passdowns/I01-01.md`. Link I02 to the actual build and device setup. Leave I01 open if a required build or launch check fails. [QA gates](../../specification/chapters/05-quality/05-02-quality-gates.md#required-gates)
