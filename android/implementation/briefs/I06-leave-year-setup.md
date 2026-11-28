# I06 · Open or create a leave year

**Finalized draft, pending planner approval.** I06 is the first milestone that writes to the canonical store. Everything that touches accounting semantics or the shared core surface is flagged as a proposed decision (P-D1…P-D6) and must be approved by the planner before coding starts; the rest is bound to the actual I05 tree (`c1cd9fe` + `8df4a4b`).

## Assignment

- Status: **draft for planner approval**. Do not assign coding tasks until the planner approves P-D1…P-D6.
- Starting commit: `8df4a4b` (I05 complete, spec v1.1).
- Approved spec surfaces: chapter [02-01 Start or find a leave year](../../specification/chapters/02-ledger/02-01-ledger-and-leave-years.md) (`CAL-YEAR-01`, `YEAR-MAN-01`, `YEAR-TODAY-01`), [setup mockups](../../specification/chapters/01-calendar/01-03-navigation-and-screens.md#first-use-and-leave-year-setup) (`calendar-setup.png`, `calendar-setup-today.png`), and the [ledger acceptance cases](../../specification/verification/acceptance-tests.md#ledger-and-leave-year-acceptance) (`T-CAL-YEAR-01`, `T-YEAR-MAN-01`, `T-YEAR-TODAY-01`).
- Prerequisite evidence: I04 (store writer, generations, fingerprints), I05 (VM, routes, unavailable-state handoff, fixture harness). Read order: `storage/StoreCoordinator.kt` → `calendar/CalendarViewModel.kt` (unavailable + snapshot handling) → `bridge/src/fedleave_probe.py` → `src/fedleave/cross_year.py` (`manual_ledger`, `seed_accruals`), `src/fedleave/ledger.py` (`upsert_accrual_rate_change`, `calculate_balances`), `src/fedleave/commands/forced_balance.py`, `src/fedleave/commands/data.py` (`init`) → `app/build.gradle.kts` (bridge Python include list) → the two setup mockup JSONs (`calendar.json` CALENDAR-SETUP boards, `calendar-setup-today.json` if present — otherwise `calendar.json`).

### Tasks and file ownership

| Task | Files owned | Delivers |
| --- | --- | --- |
| I06-01 Independent setup | `core/CreationBridge.kt` (typed write surface over a new bridge module, P-D1), `calendar/SetupViewModel.kt` + `calendar/SetupUiState.kt` + `calendar/SetupScreen.kt` + `calendar/SetupReview.kt`, route wiring in `ui/AppRoutes.kt`/`ui/FedLeaveApp.kt`, `CalendarViewModel` additions (uncovered-date setup prompt state + resume-after-create), `MainActivity` unchanged; tests `app/src/test/.../calendar/SetupViewModelTest.kt`, `app/src/androidTest/.../calendar/SetupScreenTest.kt`, `app/src/androidTest/.../core/CreationBridgeTest.kt` | First-use and uncovered-date entry to **Set up leave year**, the schedule/rates/opening form, the review with exact proposed year metadata, confirm/cancel with the I04 writer (fingerprint + revalidation), resume of the originating balance lookup |
| I06-02 Today balances | `calendar/TodayBalancesForm.kt` + `calendar/TodayReview.kt` (or sections of SetupScreen), `CreationBridge` Today operation (P-D1), tests extending both suites | The **Balances as of Today** choice with dated targets, the zero pre-Today window, same-day-accrual handling, and the forced-balance difference rows; `T-YEAR-TODAY-01` |
| I06-03 Welcome and gaps | `ui/WelcomeScreen.kt` (Set up leave year active; Restore backup placeholder until I17), consecutive-missing-year guidance state in `SetupViewModel` | The first-use welcome, per-year sequential guidance, and the empty-store recovery entry |

I06 closes after I06-01/02 pass and the three acceptance cases pass. I08 wires the editor return after setup; I17 replaces the Restore placeholder.

### Proposed decisions requiring planner approval

- **P-D1 · Write surface is an Android-side bridge module, not a facade change.** The read facade stays read-only. Add `bridge/src/fedleave_creation.py` (bundled like `fedleave_probe`) exposing one JSON-in/JSON-out function `create(request)` that calls the existing pure core builders — `cross_year.manual_ledger`, `ledger.upsert_accrual_rate_change`, `cross_year.seed_accruals`, `ledger.calculate_balances` and the forced-balance row shape from `commands/forced_balance.py` — and returns the candidate files plus a preview summary. No new accounting logic is written on either side; `src/fedleave` is untouched. Rejected alternative (for the planner's visibility): adding a write operation to the shared facade.
- **P-D2 · Holiday cache is deferred to I18.** I06 creates `config.json` and `leave_years/<year>.json` only. A missing holiday cache cannot create estimated markers (chapter 01-04), so the new year simply shows no holiday markers until I18's offline regeneration. This keeps the Python bundle unchanged in I06. Rejected alternative: bundling `holidays.py` + the `holidays` dependency now.
- **P-D3 · config.json on first use.** With an empty store, the setup flow generates the default config by calling the desktop `config.init_config` into a temporary directory inside the bridge and returning its bytes, then publishes config + ledger together through `StoreCoordinator`. The existing config (with user defaults, increment and sick rate) is passed into `manual_ledger` when present.
- **P-D4 · Stale-preview and duplicate-creation contract.** The review records the store generation id (fingerprint) plus the reviewed inputs. Confirmation calls `StoreCoordinator.beginCandidate(expectedGenerationId)` → rebuilds the ledger from the reviewed inputs → `publish(candidate, expectedBase, fingerprint)`. `StaleBase` or a target year that now exists forces a fresh preview; repeat confirm can only publish once. Cancel and preview never open a candidate.
- **P-D5 · Setup routes and return context.** New routes `setup`, `setup/today`, `welcome` (names final at implementation). The Calendar VM gains `pendingSetupFor(date)` state consumed by the unavailable card ("Set up leave year") and by first launch (store `Empty`); after a successful publish the VM reopens the store and re-runs the interrupted read for the originating date. Month browsing and reports never trigger setup.
- **P-D6 · Consecutive gaps.** When the requested date is more than one year beyond the last ledger, the setup flow proposes each year in order (adjacent successor schedule) and reviews each before creation; confirmed years persist even if the user stops midway.

## Behavior and contracts

### Creation preview (shared by both balance choices)

Inputs: leave-year identifier, first pay-period date, pay-period count (26 default, 27 offered), annual accrual hours per period, sick rate shown from the stored config defaults, and the opening balances by category (start-of-year) or dated targets (Today). Validation mirrors `manual_ledger`: identifier range, nonnegative hours at the configured increment, count in {26, 27}, and the contiguous schedule. The preview shows: year identifier, full date range, first pay period, schedule count, rates, and the resulting opening or dated targets. PPL/FMLA are excluded from opening forms (they require dated events); travel comp and other undated expiring openings warn that earning date and expiration are unknown. Preview and Cancel write nothing.

### Today balance choice (I06-02)

Offered only when the proposed year's stored date range includes the injected local day. The candidate starts with zero `starting_balances` and zero base annual/sick rates; both configured rates are added as dated `accrual_rate_changes` effective Today via `upsert_accrual_rate_change`, accruals are seeded through year-end, `calculate_balances` runs through Today, and the difference per supported category becomes one canonical `forced-balance` row (increase or decrease) dated Today with a setup reason and unique ID. No zero-hour rows and no invented historic earning events. `initialization.baseline_as_of` records the additive ISO date. If an accrual is scheduled for Today, the target includes it and must still be exact. The review shows the zero-balance window from the year start through the day before Today and the next scheduled accrual.

### Confirmation

**Create leave year** publishes the reviewed candidate through the I04 writer once. If the local date rolled over between preview and confirmation, the preview refreshes and a new confirmation is required. Errors surface without publishing or erasing the draft. After success the originating context resumes (balance lookup for the requested date; the editor in I08). A competing creation triggers a reload and revalidation.

## Screen and resource checklist

| Screen | Annotated mockup | Notes |
| --- | --- | --- |
| WELCOME | `calendar-setup.png` (first-use board) | **Set up leave year** active; **Restore backup** shown disabled with an interim note (I17) |
| SETUP-FORM | `calendar-setup.png` (form board) | Year start, pay periods, accrual rate, openings; balance-date choice appears when the schedule contains Today |
| SETUP-TODAY | `calendar-setup-today.png` | Dated targets per category, blank = zero, inline validation |
| SETUP-REVIEW | both boards (review boards) | Year id, range, first period, count, rates, targets or openings, zero window, next accrual, provenance warnings, **Create leave year**, Cancel |
| Uncovered-date prompt | `calendar-date-states.png` (unavailable board) | Adds **Set up leave year** beside Retry/Choose date |

Visible labels go into `strings.xml`; semantic tags `setup_form`, `setup_review`, `setup_year`, `setup_start_date`, `setup_pay_periods`, `setup_accrual`, `setup_today_target_<category>`, `setup_create`, `welcome_setup`, `welcome_restore`. Numeric entry uses the configured increment; forms stay visible above the keyboard; large text keeps every field reachable.

## Verification

From `android/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` and the repository `.venv/bin` on `PATH`:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

- **Desktop parity is the accounting gate.** For each setup case, run the CLI with the same inputs in a disposable directory (`fedleave init … --data-dir …`, plus the Today flow equivalent for I06-02) and compare the phone-produced ledger's semantic fields (bounds, periods, rates, openings, forced rows) with the CLI output; the round-trip through the desktop JSON archive is part of `T-YEAR-TODAY-01`.
- Fixtures: reuse `qa/fixtures/calendar-2026` for existing-store contexts; a fresh empty store (no fixture import) for first use; the frozen clock `2026-01-16` plus a mutable test clock for the midnight-rollover case.
- `T-CAL-YEAR-01`: Jan 9 2027 resolves to 2026 without setup; browsing creates nothing; an uncovered Today shows the unavailable balance with the setup action; cancel preserves; confirm creates once and resumes the lookup; empty store offers setup; consecutive gaps are guided in order; an invalid existing year warns and preserves its file.
- `T-YEAR-MAN-01`: 2027 starting Jan 10, 26 periods, 1.25 h annual accrual, 40 h undated travel comp; cancel preserves; 1.10 h at quarter-hour increments is rejected; one authoritative opening per amount, zero unspecified, no predecessor; repeated creation preserves the first ledger.
- `T-YEAR-TODAY-01`: 2026 starting Jan 11, 26 periods, rates 6/4, Today Feb 10; blanks/zeros then 80/24/2.25 targets; Jan 24 zero in both; Feb 10 equals the targets; Feb 21 = 6/4/0 and 86/28/2.25; accrual-date Today includes the accrual once; year-end rates still resolve; restore round-trip; out-of-range Today unavailable; midnight rollover invalidates the preview; repeated apply creates one ledger.
- Store hashes: hash the store tree before and after every preview, cancel and failed publish.
- Visual loop: capture WELCOME, SETUP-FORM, SETUP-TODAY and SETUP-REVIEW against the annotated boards, iterate with the image agent until PASS (mockup is the source of truth), and embed the finals in the QA report.

## Handoff

- Subset records under `android/qa/reports/I06-0N.md`, `android/reviews/code/I06-0N.md`, `android/implementation/passdowns/I06-0N.md`.
- I06 closes when `T-CAL-YEAR-01`, `T-YEAR-MAN-01` and `T-YEAR-TODAY-01` pass on the final candidate with the reviews recorded.
- I07 consumes: the source identity/fingerprint contract, the exact carried-lot expectations, and the destination-absence checks. I08 consumes the setup return context for the editor. I17 replaces the Welcome restore placeholder.
- Update `android/README.md` (bridge write module, setup routes) and the root `passdown.md` at closeout.

Before assigning any task, the planner approves or amends P-D1…P-D6 and removes this section's draft marker.
