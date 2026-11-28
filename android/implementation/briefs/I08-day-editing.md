# I08 · Day editing

**Finalized draft, pending planner approval.** I08 is the first milestone that mutates existing ledger transactions on the phone. Everything that touches accounting semantics is delegated to the bundled pure core or parity-gated against the desktop CLI; the five proposed decisions (P-E1…P-E5) are flagged for the planner. Bound to the actual tree at the I07 closeout (commit `905451d`, spec v1.3).

## Assignment

- Status: **draft for planner approval**. Do not assign coding tasks until the planner approves P-E1…P-E5.
- Starting commit: `905451d` (I07 complete; 51 JVM + 82 device tests green; desktop 1.1.0 released).
- Approved spec surfaces: chapter [02-01 Edit and review leave](../../specification/chapters/02-ledger/02-01-ledger-and-leave-years.md#edit-and-review-leave) (`CAL-DAY-01`, `CAL-DAY-02`, shared editor rules), the [day-edit mockup](../../specification/artwork/mockups/calendar.json) (`DAY-EDIT`, `DAY-SAVE-REVIEW`, `ENTRY-ADD` boards), and the [ledger acceptance cases](../../specification/verification/acceptance-tests.md) (`T-CAL-DAY-01`, `T-CAL-DAY-02`, `T-CAL-YEAR-02`).
- Prerequisite evidence: I03 (day interface), I04 (publish contract), I05 (day route), I06/I07 (bridge write surface + setup resume). Read order: `src/useorlose/commands/transactions.py` (`add`, `_set_day_values`, `set_day`) → `src/useorlose/ledger.py` (`normalize_direction`, `generate_transaction_id`, `create_transaction`) → `src/useorlose/validation.py` (`sanitize_text`) → `bridge/src/useorlose_creation.py` (the I06/I07 bridge pattern) → `calendar/DayScreen.kt` + `ui/AppRoutes.kt` (the day route) → the edit mockup boards.

### Tasks and file ownership

| Task | Files owned | Delivers |
| --- | --- | --- |
| I08-01 Edit bridge | `bridge/src/useorlose_editing.py` (new), `core/EditingBridge.kt` (new typed write surface), `app/build.gradle.kts` (+`validation.py` include, +module), tests `app/src/androidTest/.../core/EditingBridgeTest.kt` | `edit_day` (replace one category+direction group) and `add_entry` operations with pydantic-free rows matching the desktop Transaction dump, protected-row exclusions, mixed-metadata detection, and previews with the exact replaced/created IDs |
| I08-02 Editor screens | `calendar/DayEditViewModel.kt` + `calendar/DayEditUiState.kt` + `calendar/DayEditScreen.kt` (+ add-entry form), route wiring in `ui/AppRoutes.kt`, tests `app/src/test/.../calendar/DayEditViewModelTest.kt`, `app/src/androidTest/.../calendar/DayEditFlowTest.kt` | The day editor entry from the I05 day route, the group selector, hours at the configured increment, comment, the review with before/after IDs, back-preserves-draft, save-once publish, and the failed-save draft retention |
| I08-03 Editor resume | `CalendarViewModel` additions (`pendingEditorResume`), setup resume wiring | `T-CAL-YEAR-02`: an edit for an uncovered date offers setup; cancel preserves the draft; confirm creates only the containing year and resumes the same edit |

I08 closes when `T-CAL-DAY-01`, `T-CAL-DAY-02` and `T-CAL-YEAR-02` pass on the final candidate with the reviews recorded. Individual correction and deletion by ID (`T-CAL-TX-01`) stay in I09; the editor's individual-correction routing just points there.

### Proposed decisions requiring planner approval

- **P-E1 · Editing bridge mirrors the CLI writes.** `commands/transactions.py` is the CLI layer (typer, not bundled) and its row writer is the pydantic `create_transaction`. A new `useorlose_editing` module implements `edit_day` (mirror of `_set_day_values` scoped to one category+direction group) and `add_entry` (mirror of `add`) with pydantic-free dict rows whose field set matches the desktop `Transaction` dump (id, date, category, direction, hours, description, status, source, created_at/updated_at, the expiration fields). `normalize_direction`, `generate_transaction_id` and `sanitize_text` come from the bundled pure modules (`validation.py` joins the bridge include list). The parity gate locks the mirror against `use-or-lose set-day --authoritative … --json` and `use-or-lose add … --json` on the pinned fixtures, byte-comparing the resulting ledgers.
- **P-E2 · Group scoping.** The phone editor replaces one group (category + direction), per the chapter's group model, while the desktop `set-day` replaces the category-wide set. The mirror therefore filters by direction; the parity gate runs on fixtures where the category holds a single direction of ordinary rows so the desktop CLI output and the phone ledger stay byte-identical. Rows with `carryover_generated`, `source == "expiration-rollover"`, `auto_accrual`, `forced-balance`, or differing status/source/comment metadata are excluded from the group and listed in the preview; when exclusion would leave effective rows behind, the review routes to individual correction (I09) instead of consolidating.
- **P-E3 · Edit preview and publish contract.** The preview computes the candidate ledger in memory and shows the exact replaced and created IDs plus the resulting balance; it never writes. Saving re-derives from the current store generation (stale base → refresh + re-confirm) and publishes the single changed year file merged over the reviewed base through the I04 writer, reusing the I06/I07 fingerprint guard. A failed or rejected publish keeps the draft and the review visible.
- **P-E4 · Editor resume after setup.** Opening an edit for a date whose year is missing shows the unavailable state with **Set up leave year** (reusing the I06 mechanism); the editor draft (date, category, direction, hours, comment) is preserved in the navigation state. After a successful setup publish, the editor reopens on the same date with the original draft; cancel preserves the draft and the store. `T-CAL-YEAR-02` exercises this with Feb 1, 2027 over the 2026 fixture.
- **P-E5 · Validation.** Hours are positive and at the configured increment (from the store config defaults); the comment goes through `sanitize_text`; the add form uses the stored default status and keeps denied/cancelled rows stored as entered (ineffective, per CAL-DAY-02). The dated heading uses the full weekday and date format from the mockup.

## Behavior and contracts

Inputs: year and date (resolved from the tapped day), category, direction, hours, comment, status. Outputs: the updated `leave_years/<year>.json` candidate and a preview with the replaced IDs, the created ID, the before/after hours for that group, and the resulting dated balance. Read is the existing I03 day interface. The editor is scoped to one leave year and date; returning to Calendar restores focus on the tapped date and retains the month and cutoff. Preview, cancel and back write nothing; save publishes once.

Desktop parity: for each case run `set-day --authoritative` / `add` with the same inputs on a fixture copy and compare the phone-produced ledger bytes.

## Screen and resource checklist

| Screen | Annotated mockup | Notes |
| --- | --- | --- |
| DAY-EDIT | `calendar-edit.png` (`DAY-EDIT`) | Group selector (full names), hours with increment suffix, comment, **View the N current entries**, unaffected rows listed, **Review changes** |
| DAY-SAVE-REVIEW | `calendar-edit.png` (`DAY-SAVE-REVIEW`) | Replace card with exact IDs, before → after hours, unchanged entries note, **Save changes**, **Back to draft** |
| ENTRY-ADD | `calendar-edit.png` (`ENTRY-ADD`) | Leave type, direction, hours, comment, **Add entry** |

Visible labels go into `strings.xml`; semantic tags `day_edit_group`, `day_edit_hours`, `day_edit_comment`, `day_edit_review`, `day_edit_save`, `day_edit_back`, `day_add_type`, `day_add_direction`, `day_add_hours`, `day_add_comment`, `day_add_submit`, `day_edit_setup`. TalkBack reads the replace card as one unit with both IDs and the hour change.

## Verification

From `android/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` and the repository `.venv/bin` on `PATH`:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

- Fixtures: `qa/fixtures/calendar-2026` (the Editable day fixture: Jan 16 annual use 2+1 h, sick 1 h; T-CAL-DAY-01/02 arithmetic) and the Carryover fixture where relevant.
- `T-CAL-DAY-01`: Jan 16 group edit to 3.5 h with a comment containing quotes and punctuation; review shows 3 h → 3.5 h and the two exact IDs; back preserves the draft and the saved 89 h balance; confirm yields annual 88.5 h and sick 19 h preserving the sick row and comment; clearing the group yields 92 h; mixed stored statuses/comments/provenance route to individual correction; stale review or failed save preserves the ledger and draft; return to Calendar keeps the tapped date.
- `T-CAL-DAY-02`: add 1 h annual use on Jan 22 and on a past date via the same form; Jan 21 stays 90 h and Jan 22 becomes 85 h; a denied row remains identifiable and ineffective; Today crossing does not change displayed entry data.
- `T-CAL-YEAR-02`: an edit for Feb 1, 2027 over the 2026 fixture offers setup; cancel preserves; confirm creates only 2027 and resumes the same edit; saving is a separate confirmation.
- Store hashes before and after every preview, cancel, back and failed save; parity byte-compare of the phone ledger against the desktop CLI output.
- Visual loop: capture DAY-EDIT, DAY-SAVE-REVIEW and ENTRY-ADD against the annotated boards, iterate with the image agent until PASS, and embed the finals in the QA report.

## Handoff

- Subset records under `android/qa/reports/I08-0N.md`, `android/reviews/code/I08-0N.md`, `android/implementation/passdowns/I08-0N.md`.
- I08 closes when `T-CAL-DAY-01`, `T-CAL-DAY-02` and `T-CAL-YEAR-02` pass on the final candidate with the reviews recorded.
- I09 consumes the individual-correction routing boundary and the exact-ID review surface.
- Update `android/README.md` (editing bridge module, editor routes) and the root `passdown.md` at closeout.

Before assigning any task, the planner approves or amends P-E1…P-E5 and removes this section's draft marker.
