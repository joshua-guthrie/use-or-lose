# Checkpoint 30 · Preparation briefs, fixtures and developer README

## Completed

Added the missing [I02 bridge experiment](../../implementation/briefs/I02-bridge-experiment.md), [I03 read-only core](../../implementation/briefs/I03-read-only-core.md) and [I04 private store](../../implementation/briefs/I04-private-store.md) briefs. Each records starting dependencies, risk and ownership, exact interfaces, preserved desktop contracts, verification commands and the next milestone's dependency. The briefs index and milestone inputs link them.

Added a reproducible Calendar 2026 fixture builder under [`android/qa/fixtures/build_calendar_fixture.py`](../../qa/fixtures/build_calendar_fixture.py). It generates:

- The canonical [input store](../../qa/fixtures/calendar-2026) with normalized manual and holiday-cache timestamps.
- The [fixture manifest](../../qa/fixtures/calendar-2026.manifest.json) with the source identity, frozen local date, per-file hashes, tree digest and exact case requests.
- Golden dated balances, period detail, day activity and the frozen January month view under [`android/qa/golden/calendar-2026`](../../qa/golden/calendar-2026).
- Six [malformed store variants](../../qa/fixtures/storage/manifest.json) for decode, schema, value, uniqueness and completeness rejection in I04.

Rewrote [`android/README.md`](../../../README.md) as the developer entry point: current status, Android Studio open and run steps, planned build and test commands, the shared-core architecture, the directory layout, the documentation map and the upkeep rule. Placeholder toolchain values remain until I01 records the tested versions. Added the same upkeep rule to the repository [working rules](../../../AGENTS.md).

## Verification

The fixture tree digest is `03e9e1757f73015b356b900703a13a88390e854f274c7bce78bd2f9f00c7aea9`. The builder ran twice and produced the same digest and the same file set. Its assertions confirmed January 13, 16, 22, 24 and 25, 2026 and January 4, 2027 balances; period 1 earned, used and closing values; January 16 day activity; and the frozen January month view. The storage variants decode or validate as documented, including the invalid UTF-8 case.

Link checking covered the new briefs, fixture documents, README and updated chapter references. The Android Gradle project does not exist yet, so no Gradle command ran. The starting source identity for this documentation increment was `507e5a5`. A reliable five-hour usage reading was unavailable in this environment; this was a bounded local documentation checkpoint with no paid or network work.

## Where to resume

For product work, the last specification checklist gate is the owner's approval to start implementation. With that approval, run I01 from its [startup brief](../../implementation/briefs/I01-01-start-the-android-project.md), replace the README toolchain placeholders, then run I02 against the committed fixture. I02's owner decision gates I03 and I04.

For orchestration work, prepare the OR01 pilot brief and choose the Linux isolation backend. [OR01](../chapters/06-delivery/06-03-orchestrator-pilot-milestones.md#or01-offline-mock-pilot)
