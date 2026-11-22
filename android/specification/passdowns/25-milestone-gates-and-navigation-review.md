# Milestone QA gates and navigation review

The Codex five-hour window reported **30% remaining** at the closeout decision. Finish this documentation checkpoint and resume in a fresh window.

## Completed specification work

- I05 tests the working Today command and read-only day details. I09 verifies Transactions, I10 verifies Force Balance and I15 tests the complete Calendar dropdown and PDF actions. I05 injects marker preferences and I18 tests the actual Settings controls.
- I06 tests first-use setup and resuming a balance lookup. I08 tests missing-year setup from the day editor and return to the original edit. I17 tests the full Welcome screen with the restore path. Today setup has a desktop archive check in I06 and separate Android Share and Restore checks in I16 and I17.
- The owner requested a dedicated review of navigation before app coding. The detailed tap paths, annotated navigation boards and decisions remain open. [Navigation review](../chapters/06-delivery/06-02-implementation-milestones.md#review-menu-navigation-before-i01) · [Acceptance cases](../verification/acceptance-tests.md)

Verified 35 reproducible mockup boards, 96 screen IDs, 57 parity and acceptance pairs, and 686 local links. These checks validate the specification. Android app tests begin with the implementation milestones.

## Resume work

1. Walk through the Calendar dropdown, bottom tabs, Settings, Help and the paths into and out of Reports. Write and review the route inventory and boards with the owner before I01.
2. Draft the agreed I02 bridge probe, I03 read-only accounting contract, and I04 private JSON store briefs. Specify inputs, outputs, stable errors, and fault cases. Use a reproducible synthetic copy of the [January fixture](../verification/acceptance-tests.md#calendar-balance-fixture) with a second valid generation for recovery tests. Verify expected outputs on the desktop reference first. [Milestone inputs](../../implementation/briefs/milestone-inputs.md)
3. Review shared backup conflict handling. Two branches of the same ledger start at 20 h annual. One sets a date to 5 h and one to 8 h. The desktop merge helper accepts both independently identified adjustments, resulting in −7 h. General ledger validation accepts that result. Android describes the same ID-based rule. Recommend a shared planner that reports a blocking conflict before any writes and feeds the CLI, desktop GUI and Android restore review. Keep both originals and the schema-v1 envelope. Review whether selective resolution is needed beyond a safe cancel and explicit Replace. [Desktop merge](../../../src/fedleave/commands/data.py#L351) · [Android restore review](../chapters/04-architecture/04-02-device-storage.md#restore-review)
4. Review Edit Day policy. Recommend one explicitly selected group of compatible ordinary entries at a time, with individual review for different status, comments or provenance. Preview affected IDs and old and new amounts. Zero clears only the selected group. Preserve accounting-generated rows and handle forced adjustments through their own flow. [Edit Day requirements](../chapters/02-ledger/02-01-ledger-and-leave-years.md#edit-and-review-leave)
