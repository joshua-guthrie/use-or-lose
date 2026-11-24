# Checkpoint 31 · Implementation approved through I06

## Completed

The owner approved implementation for milestones I01 through I06 on November 23, 2026. This satisfies the owner-approval item in the [specification review checklist](../chapters/06-delivery/06-02-implementation-milestones.md#specification-review-checklist). Coding may now begin with I01 and proceed in milestone order until I06, subject to each milestone's QA gates and the five-hour checkpoint rule.

Probed the development machine before starting I01. JDK 21 is installed; no Gradle, Android SDK, Android Studio or emulator is present. Hardware virtualization is available (`/dev/kvm`), the host is `x86_64` with 12 cores, about 118 GB free disk and 5.6 GB RAM.

## Verification

Documentation and fixture changes from [checkpoint 30](30-briefs-fixtures-and-readme.md) pass the full desktop suite (`make PYTHON=.venv/bin/python check`, 1045 passed, 3 skipped). The Android Gradle project does not exist yet, so no Gradle or emulator command has run.

## Where to resume

Start I01 from the [startup brief](../../implementation/briefs/I01-01-start-the-android-project.md):

1. Record the owner's application ID decision, or an explicit prototype ID approved for this milestone.
2. Install the Android SDK command-line tools or Android Studio, the platform, build tools and platform-tools, and create the project's pinned Gradle wrapper.
3. Create the single `app` module, run the launch smoke test on an emulator image (RAM is limited, so prefer a lean image and profile) or a physical phone.
4. Replace the [README toolchain placeholders](../../README.md#pinned-toolchain) and the planned commands with the tested versions, per the repository working rule.

The distribution rule still applies to any future source push to `master`; local checkpoint commits do not publish installers. The five-hour window and the 30% closeout rule govern each I01 session.
