# Android project organization checkpoint

November 21, 2026.

- The [specification index](../specification.md) groups requirements into numbered chapters. Planning, editable mockups, parity, acceptance, and specification passdowns live in named directories. [Project organization](../chapters/07-organization/07-01-project-organization.md)
- [Android's front page](../../README.md) points to code, specification, evidence, releases and restart notes. The project organization chapter defines where each goes. Android Studio will open the Android root after I01 adds its Gradle project files.
- Keep separate [QA reports](../../qa/reports/template.md) and [AI reviews](../../reviews/code/template.md) for each coding milestone or completed subset. Version synthetic evidence and use links for retained build artifacts. Keep signing credentials and private security audit working material local.
- The [I01 and I05 brief drafts](../../implementation/briefs/README.md) show how to assign exact behavior, files, fixtures, and tests to a smaller model. The [milestone input map](../../implementation/briefs/milestone-inputs.md) traces all twenty phases to reference sources and checks. Pin I01's approved input at assignment and bind I05 to the actual I01–I04 outputs before implementation.
- The organization review resolved the Calendar preference boundary. The balance picker continues to mark pay-period ends when the main Calendar hides them. The Calendar brief and acceptance tests specify the distinction.
- All 25 boards rebuild byte-for-byte. Verified 79 screen IDs, 51 parity and acceptance pairs, and 562 local links. The full desktop regression run passed with 1,013 tests and 3 skips. The five-hour window showed 58% remaining during the organization review.
- Resume with the open [owner decisions](../chapters/06-delivery/06-02-implementation-milestones.md#draft-decisions-for-owner-review), then I01. The approved Calendar layout, marker shapes and existing PDF reference are part of the baseline.

## Published checkpoints

- `ad135c4` defines milestone gates, iterative fixes and retests, and visual conformance. [QA checkpoint Distribution](https://github.com/joshua-guthrie/fedleave/actions/runs/44956626219)
- `e7d4700` records the selected Calendar and adds the eight-balance preview and scrollable detail. [Balances checkpoint Distribution](https://github.com/joshua-guthrie/fedleave/actions/runs/44957804957)
- `757fb48` defines the four Calendar date markers and the phone and printed report behavior. [Markers checkpoint Distribution](https://github.com/joshua-guthrie/fedleave/actions/runs/44959210943)
