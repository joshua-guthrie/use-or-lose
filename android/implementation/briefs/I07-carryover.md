# I07 · Carryover from a previous leave year

**Finalized draft, pending planner approval.** I07 reuses the I06-approved write surface and publish contract. Everything that touches accounting semantics is delegated to the bundled desktop core; the three proposed decisions (P-C1…P-C3) are flagged for the planner. Bound to the actual tree at the internal-identifier rename (commit `ee22434`, spec v1.3).

## Assignment

- Status: **draft for planner approval**. Do not assign coding tasks until the planner approves P-C1…P-C3.
- Starting commit: `ee22434` (internal-identifier rename complete, spec v1.3, 1.1.0 release builds).
- Approved spec surfaces: chapter [02-01 Carry over from a previous leave year](../../specification/chapters/02-ledger/02-01-ledger-and-leave-years.md) (`YEAR-CARRY-01`, category table), the [carryover review mockup](../../specification/artwork/mockups/calendar.json) (`LEAVE-YEAR-CARRYOVER / LEAVE-YEAR-REVIEW` board), and the [ledger acceptance cases](../../specification/verification/acceptance-tests.md) (`T-YEAR-CARRY-01`).
- Prerequisite evidence: I06 (creation bridge, setup flow, publish contract). Read order: `bridge/src/useorlose_creation.py` (I06 write surface) → `src/useorlose/cross_year.py` (`derive_carryover`, `fingerprint`, `creation_plan`/`apply_creation`) → `calendar/SetupViewModel.kt` (method choice, begin/confirm, stale and midnight guards) → `core/CreationBridge.kt` → `storage/StoreCoordinator.kt` (publish) → the carryover mockup board.

### Tasks and file ownership

| Task | Files owned | Delivers |
| --- | --- | --- |
| I07-01 Carryover preview | `bridge/src/useorlose_creation.py` (`carryover_create` operation), `core/CreationBridge.kt` (typed `CarryoverPreview`), tests `app/src/androidTest/.../core/CreationBridgeTest.kt` | The device-safe carryover preview: candidate `leave_years/<year>.json`, source/destination dates and schedule, per-category openings, reductions with reasons, carried lots, warnings, and the preview fingerprint |
| I07-02 Carryover flow | `calendar/SetupViewModel.kt` + `calendar/SetupUiState.kt` + `calendar/CarryoverReviewScreen.kt` (or sections of `SetupScreen.kt`), route wiring in `ui/AppRoutes.kt`/`ui/UseOrLoseApp.kt`, tests `app/src/test/.../calendar/SetupViewModelTest.kt`, `app/src/androidTest/.../calendar/CarryoverFlowTest.kt` | The method screen gains **Carry over from previous leave year** (suggested when the adjacent source validates), the review with the source card, opening rows, reductions, lots/warnings detail, **Create leave year** and **Back to balance date**; confirm re-derives and publishes once through the I04 writer with the source bytes untouched |

I07 closes when `T-YEAR-CARRY-01` passes on the final candidate with the reviews recorded. I08 wires the editor return after setup; reconciliation (`YEAR-REC-01`) is its own milestone.

### Proposed decisions requiring planner approval

- **P-C1 · Direct delegation, no mirror.** `cross_year.py` and every module it imports are already pydantic-free and already bundled for the read path (verified against the bridge include list). The new `carryover_create` operation therefore calls `cross_year.derive_carryover` and `cross_year.fingerprint` directly — unlike I06's `manual_ledger` mirror, no accounting logic is duplicated. `src/` stays untouched. The write-side parity gate locks the phone preview against the CLI output (`use-or-lose init --from-year 2026 --year 2027 --preview --json`).
- **P-C2 · Fingerprint contract.** The preview fingerprint is `fingerprint({"target": target, "source": <raw source file bytes>, "config": config})`, matching `cross_year.creation_plan` semantics (source hashed as raw JSON bytes, config as the parsed store config). The bridge receives the source raw string and the parsed config from the store. Confirmation re-derives from the current store generation: a changed source or policy yields `STALE_PREVIEW` and forces a fresh preview plus a new confirmation, and a target year that now exists blocks the publish (`DESTINATION_EXISTS`). Preview and cancel write nothing; the source file stays byte-identical on success.
- **P-C3 · Schedule handling.** Carryover always uses the validated source end as the destination start and proposes the canonical next schedule from the bundled `leave_year_period_count`. The review shows the derived first pay period and count; a pay-periods selector (26/27) is offered only as the explicit agency-schedule escape hatch (chapter 02-01), passing `pay_periods` through to `derive_carryover` exactly like the CLI. Blocked carryover (no valid adjacent source, conflicting carryover records, unsupported policy) keeps the manual option available and requires an explicit choice.

## Behavior and contracts

Inputs: source year (must equal `to_year - 1`), optional explicit `pay_periods` in {26, 27}, the source ledger raw bytes, the stored config (parsed dict or absent), and the reviewed destination year. Outputs: the candidate ledger file and a preview summary with source identity (year, dates), destination dates, first pay period, period count, openings by category, reductions (category, hours, reason), carried lots (category, origin year/transaction, remaining hours, deadline, extension status), warnings, and the preview fingerprint.

Normal flow: the setup method screen lists **Carry over from previous leave year** first when the adjacent source validates, else last with the blocked reason. Preview renders the review from the summary. Confirmation re-derives against the current generation, checks the fingerprint and destination absence, publishes the single new year file merged over the reviewed base, and resumes the originating balance lookup for the requested date. Stale preview refreshes and re-asks. Cancel and preview write nothing.

Desktop parity: for each case run `use-or-lose init --from-year <src> --year <dst> --preview --json --data-dir <dir>` and compare openings, reductions, lots, warnings and the schedule; the phone's fingerprint input tuple matches `creation_plan`'s.

## Screen and resource checklist

| Screen | Annotated mockup | Notes |
| --- | --- | --- |
| SETUP-METHOD | `calendar.json` (`LEAVE-YEAR-SETUP-METHOD` board extended) | Carryover choice appears above the balance-date choices, suggested first when valid |
| CARRYOVER-REVIEW | `calendar.json` (`LEAVE-YEAR-CARRYOVER / LEAVE-YEAR-REVIEW` board) | Source card, opening rows, reduction text, lots/warnings detail, Create / Back actions |

Visible labels go into `strings.xml`; semantic tags `setup_carryover_choice`, `carryover_review`, `carryover_source_card`, `carryover_opening_<category>`, `carryover_reduction`, `carryover_lots`, `carryover_create`, `carryover_back`. TalkBack reads the source card as one unit ("Carry over from 2026. Jan 10, 2027 – Jan 8, 2028 · 26 periods. Source closes Jan 9, 2027.") and each reduction row announces category, hours and reason.

## Verification

From `android/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` and the repository `.venv/bin` on `PATH`:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

- Fixtures: `qa/fixtures/calendar-2026` (Carryover fixture: annual 280 h, zero annual accrual, sick 496 h @ 4 h, credit 30 h, no usage). The desktop parity command above runs against the same fixture tree.
- `T-YEAR-CARRY-01`: the 2027 preview has annual 240 h, sick 600 h, credit 24 h, with reductions of annual 40 h and credit 6 h; stale confirmation after a source change is rejected without a destination write; success makes the new ledger and preserves the source bytes; the 2023 Jan-1 source with 27 periods ends Jan 13, 2024 and its successor starts Jan 14, 2024; an inconsistent schedule or a duplicate destination cannot be applied.
- Store hashes before and after every preview, cancel and failed publish; the source file hash must be unchanged in every path except none.
- Visual loop: capture SETUP-METHOD and CARRYOVER-REVIEW against the annotated boards, iterate with the image agent until PASS, and embed the finals in the QA report.

## Handoff

- Subset records under `android/qa/reports/I07-0N.md`, `android/reviews/code/I07-0N.md`, `android/implementation/passdowns/I07-0N.md`.
- I07 closes when `T-YEAR-CARRY-01` passes on the final candidate with the reviews recorded.
- I08 consumes the setup return context for the editor; `YEAR-REC-01` consumes the source identity and lot-listing surface.
- Update `android/README.md` (carryover operation, route) and the root `passdown.md` at closeout.

Before assigning any task, the planner approves or amends P-C1…P-C3 and removes this section's draft marker.
