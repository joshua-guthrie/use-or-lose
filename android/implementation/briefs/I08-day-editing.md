# I08 · Day editing

**Owner-approved.** Decisions D1–D6 recorded below (November 27, 2026). I08 is complete in the tree (56 JVM + 87 device tests green, visual loop PASS).

## Decisions (approved)

### D1 · Write surface — **approved: bridge mirror for now; single-point-of-truth refactor planned**

- Approved as implemented: the bundled `useorlose_editing` module mirrors the CLI's `set-day`/`add` row semantics with pydantic-free rows, parity-locked byte-for-byte against the CLI on the pinned fixtures.
- **Agreed follow-up — now complete:** pydantic was removed from the project (spec in `untracked/pydantic-removal-spec.md`) and the row builder plus the replace/add operations moved into the shared pure core (`src/useorlose/editing.py`, `pure_models.py`, the pure `transaction_model.py`). The CLI and the Android bridge import the same functions; the mirror modules were deleted; the CLI's behavior, output and every desktop test stay unchanged (they were the compatibility gate).

### D2 · Replacement scope — **approved: one leave type + direction per edit**

The ledger supports both Earning and Using the same leave type on one date. Editing replaces only the chosen type+direction pair; a same-type, different-direction row is never touched. The CLI's `set-day` behavior is unchanged (category-wide); the shared refactor (D1) will parameterize the scope so both callers use one implementation.

### D3 · Write path — **approved: recompute + generation fingerprint**

At Save the phone re-applies the edit to the current store (not the stale preview) and the store's generation fingerprint blocks overwriting any intervening change (a competing publish, a restore, a future cross-device edit). Cost: milliseconds. The visible **Review changes** screen (per the mockup board) stays as the summary step before Save.

### D4 · Uncovered-date edit handoff — **approved: A (setup offer + draft resume)**

Kept as a safety net; with D5 the primary path is the month prompt.

### D5 · Browsing past the stored years — **approved and implemented: create-year prompt**

An uncovered month shows no dates. It shows an unavailable panel: "No leave year covers this year, so holidays and accruals are unavailable" with **Set up leave year**. Cancel creates nothing; after creation the calendar returns to that month, now populated, and the day editor works. `T-CAL-YEAR-02` follows this flow.

### D6 · Wording — **approved: "starting balance"**

"Annual opening" is replaced by **"Annual starting balance"** everywhere user-visible (setup form, carryover review, mockup annotations, spec prose). The accounting concept ("opening transaction") is unchanged in ledger internals and CLI flags. Terminology throughout the docs is **the CLI** (not "desktop CLI").

## Verification

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest` — 56 JVM + 87 device tests green.
- Bridge parity: byte-identical ledgers vs `set-day --authoritative` / `add` on the pinned `calendar-2026` and `editable-day-2026` fixtures (timestamps normalized).
- Visual loop PASS on `DAY-EDIT`, `DAY-SAVE-REVIEW`, `ENTRY-ADD`.
- `T-CAL-YEAR-02`: browse Feb 2027 → prompt → cancel (nothing created) → prompt again → create 2027 → month populates → edit Feb 1.

## Handoff

- I09 consumes the mixed-metadata boundary and the exact-ID review surface.
- The D1 core refactor gets its own brief (proposed before I09) with the CLI call-site list and the desktop-test migration plan.
