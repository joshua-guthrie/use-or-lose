# I08 · Day editing

**Implemented; awaiting your sign-off on D1–D6.** I08 is complete in the tree (56 JVM + 87 device tests green, visual loop PASS), but five design decisions were made while building it and one behavior change (D5) goes beyond the original brief. This brief restates each decision as a question with the options, trade-offs, and what is implemented, so you can approve as-is or redirect. Everything else (acceptance cases `T-CAL-DAY-01/02`, `T-CAL-YEAR-02`, the mockup boards, the parity gate) is bound and verified.

## Decisions needing your sign-off

### D1 · How does the phone write day edits?

The phone must add/replace ledger rows without the desktop CLI or pydantic.

| Option | Pros | Cons |
| --- | --- | --- |
| **A. Bridge mirror (implemented)** — a new bundled module `useorlose_editing` mirrors the desktop `set-day`/`add` writes with pydantic-free rows, validated by the same pure core (IDs, directions, categories, text) | No pydantic in the app; the phone ledger is byte-identical to the desktop CLI output (parity-locked on the pinned fixture); follows the I06/I07 pattern | One more mirrored surface to keep in sync (the parity gate catches drift) |
| B. Add a write operation to the shared read facade | One module instead of two | The facade is read-only by design; pydantic still needed for row building |
| C. Run the desktop CLI as a child process on-device | Zero mirroring | Impossible on Android (no subprocess); rejected outright |

**What to decide:** approve the mirror (A), or if you object to duplicated surface, we'd need a core `Transaction`-free builder in `src/` — a shared-core change I would not make without your sign-off.

### D2 · What does "replace this group" replace?

The phone editor replaces one group; the desktop `set-day` replaces the whole category on a date.

| Option | Pros | Cons |
| --- | --- | --- |
| **A. Category + direction (implemented)** — "Annual leave · Use" is one group | Matches the chapter's group model and the mockup; a Sick row on the same date is untouched | Differs slightly from the CLI's category-wide replace (parity gate runs on fixtures where the category has one direction of rows) |
| B. Category-wide, like the CLI | Literal desktop parity | Would silently remove a same-date Earn row when editing Use — surprising on a phone |

**What to decide:** approve (A). Rows that can't be safely consolidated (mixed status/source/comment, generated rows) are shown separately and route to individual correction in I09.

### D3 · How is a save confirmed?

| Option | Pros | Cons |
| --- | --- | --- |
| **A. Re-derive from the current store, then publish once (implemented)** — the bridge re-runs against the live ledger, the generation fingerprint guards races, and one candidate is published | A changed store or a competing edit forces a fresh review instead of clobbering; matches the I06/I07 contract | Slightly more work than trusting the preview |
| B. Trust the preview and publish | Simpler | Silent clobber of concurrent changes |

**What to decide:** approve (A). Preview, cancel and back write nothing.

### D4 · What happens when an edited date has no year yet? (`T-CAL-YEAR-02`)

| Option | Pros | Cons |
| --- | --- | --- |
| **A. Hand off to setup and resume the editor (implemented)** — the editor stashes its draft, offers Set up leave year, and reopens on the same date with the draft after creation | The user never retypes; matches "resumes editing the same date" | A small stash/consume handshake in the navigation state |
| B. Ask the user to create the year manually, then reopen the editor empty | Simpler code | Draft lost; worse UX |

**What to decide:** approve (A). Saving the day remains a separate confirmation after the resume.

### D5 · What does the Calendar show for a month with no stored year? (NEW behavior)

Previously an uncovered month showed an "unavailable" panel, which meant the day editor could never open for an uncovered date. To make `T-CAL-YEAR-02` reachable, I changed it:

| Option | Pros | Cons |
| --- | --- | --- |
| **A. Empty grid with tappable dates (implemented)** — the month renders plain date cells with no markers or entries; tapping a day opens the read-only day route and the editor | The editor and its setup entry are reachable for any date; "passive browsing writes nothing" still holds (no reads of missing years, no markers invented) | A month with no data looks like a real month; a user might expect balances that don't exist (the day route shows "no entries", and the editor offers setup) |
| B. Keep the unavailable panel | No misleading empty month | The uncovered-date editor flow (`T-CAL-YEAR-02`) has no UI path; the acceptance case would be un-verifiable on-device |

**What to decide:** approve (A) or revert to (B) (then I'd need you to say how an uncovered-date edit should be reached instead).

### D6 · Exact copy on the editor screens

The boards say "Annual opening"/"Annual use:"/short labels; the phone's standing rule is full leave-type names.

| Option | Pros | Cons |
| --- | --- | --- |
| **A. Full names, sentence-form unchanged rows (implemented; mockup annotation synced)** — "Annual leave opening", "Annual leave Use: 3 h → 3.5 h", "An unchanged Sick leave entry stays at 1 h." | Consistent with every other phone screen (the full-name rule from chapter 02-01) | The static board wording differs unless the annotation is updated (done) |
| B. Literal board copy | Pixel-exact with the board | Inconsistent with the rest of the app |

**What to decide:** approve (A).

## State and verification

- Starting commit `905451d` (I07 closeout); spec v1.3; desktop 1.1.0.
- Files: `bridge/src/useorlose_editing.py`, `core/EditingBridge.kt`, `calendar/DayEditViewModel.kt`, `calendar/DayEditUiState.kt`, `calendar/DayEditScreen.kt`, route wiring in `ui/AppRoutes.kt`/`UseOrLoseApp.kt`, `CalendarViewModel` draft stash, `qa/fixtures/build_calendar_fixture.py` (`--only editable`).
- Checks: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest` — 56 JVM + 87 device tests green; bridge parity byte-identical to `set-day --authoritative`/`add`; visual loop PASS on `DAY-EDIT`, `DAY-SAVE-REVIEW`, `ENTRY-ADD`.
- Handoff: I09 consumes the mixed-metadata boundary and the exact-ID review surface.

Reply "approve D1–D6" (or list which to change) and I'll mark the brief finalized, or adjust the implementation.
