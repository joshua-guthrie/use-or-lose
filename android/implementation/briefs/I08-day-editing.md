# I08 · Day editing

**Implemented; awaiting your sign-off on D1–D6.** I08 is complete in the tree (56 JVM + 87 device tests green, visual loop PASS). This brief restates each design decision in plain terms with options and trade-offs. The acceptance cases are `T-CAL-DAY-01`, `T-CAL-DAY-02`, `T-CAL-YEAR-02`.

## What the editor does, in one paragraph

The Calendar is a ledger. Tapping a day opens a read-only view of that day's leave entries. **Edit activity** lets you change one leave type + direction on that date (for example, the Annual-leave **Use** entries on Jan 16: 2 h + 1 h → 3.5 h), and **Add entry** appends one new row (for example, 1 h of annual Use on Jan 22). Changing one combination never touches the others — a Sick row on the same date stays exactly as it was. Nothing is written until you save; cancelling or backing out writes nothing; a failed save keeps your draft on screen.

The term **group** appears in the mockup ("Edit group: Annual leave · Use"). It is only a shorthand for "one leave type + one direction on this date" — not a new accounting concept. If you prefer, I can rename it to "Leave type and direction" throughout.

## Decisions needing your sign-off

### D1 · How does the phone write ledger rows?

The phone must add/replace rows without the desktop CLI or pydantic.

| Option | Pros | Cons |
| --- | --- | --- |
| **A. Bundled mirror module (implemented)** — `useorlose_editing` mirrors the desktop `set-day`/`add` logic using the same pure core for IDs, directions, categories and text | The phone's ledger is **byte-identical** to what the desktop CLI produces (parity-locked on the pinned fixture); follows the I06/I07 pattern | One mirrored surface that must keep matching the CLI (the parity gate catches drift) |
| B. Add writes to the shared read facade | One module instead of two | The facade is read-only by design; pydantic still needed |
| C. Run the CLI on-device | Zero mirroring | Impossible on Android |

**Ask:** approve A (or, if you want zero duplication, authorize a shared-core builder in `src/` — I would not make that core change without your sign-off).

### D2 · What does "edit this entry" replace?

| Option | Pros | Cons |
| --- | --- | --- |
| **A. One leave type + direction on the date (implemented)** — editing Annual · Use leaves a same-day Sick row alone | Matches the mockup and the chapter's group model; no surprises | Slightly narrower than the CLI's category-wide `set-day` (parity gate uses fixtures with a single direction of rows per category) |
| B. Whole category on the date, like the CLI | Literal CLI parity | Editing Annual · Use would silently delete a same-day Annual · Earn row |

Rows that can't be safely merged (different stored status/comment/source, or generated rows like accruals/carryover) are shown separately and route to individual correction in I09.

**Ask:** approve A.

### D3 · What happens between tapping Save and the ledger changing? (invisible plumbing, not user friction)

You're right that ledger edits don't need heavy confirmation — the visible **Review changes** screen only *summarizes* ("replace these two rows, 3 h → 3.5 h") because the mockup board calls for it; the technical question is the write path:

| Option | Pros | Cons |
| --- | --- | --- |
| **A. Recomputed write with a race guard (implemented)** — at Save, the phone re-runs the change against the *current* store (not the stale preview) and the store's generation fingerprint blocks overwriting anything that changed meanwhile | Two writes can't silently clobber each other; a restore or competing edit forces a fresh review | A few extra milliseconds of recomputation |
| B. Write the previewed bytes blindly | Simplest | Last-writer-wins clobbering |

Separately, the **visible** flow is your call: keep the mockup's review screen (shows the change before saving), or make the form **save immediately** with no review step. I implemented the review screen because the board specifies it.

**Ask:** approve A for the write path; and say "keep the review screen" or "save immediately" for the UX.

### D4 · An edit on a date whose year doesn't exist yet

With D5's change, the editor can only open for dates inside a stored year, so this case is now mostly unreachable — but the handshake remains as a safety net:

| Option | Pros | Cons |
| --- | --- | --- |
| **A. Offer setup and resume the draft (implemented)** | Never lose typed input | Small stash/consume handshake |
| B. Error out | Simpler | Dead end for the user |

**Ask:** approve A (or approve removing the safety net entirely).

### D5 · What happens when the user browses past the stored years?

Per your direction, revised and implemented: an uncovered month does **not** render dates. It shows an unavailable panel explaining that **no leave year covers this month** (so holidays and accruals don't exist yet) with a **Set up leave year** action that creates the covering year. After creation the calendar returns to that month — now populated — and the day editor works. Cancel writes nothing and the prompt remains.

**Ask:** approve the revised behavior (replaces the empty-grid approach I had before your feedback).

### D6 · What do the review rows mean?

"Annual opening 240 h" = **2027's starting annual balance** (2026 closed at 280 h; the 240 h carryover cap applies; the remaining 40 h is reported as a reduction). "Sick opening 600 h" = 2027's starting sick balance carried from 2026's close. The phone shows these with full leave-type names ("Annual leave opening"); the mockup annotation has been synced to that wording. I will also switch the labels to **"starting balance"** phrasing if you prefer it over "opening".

**Ask:** approve the wording (or request "starting balance" labels).

## Verification

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest` — 56 JVM + 87 device tests green.
- Bridge parity: byte-identical ledgers vs `set-day --authoritative` / `add` on the pinned `calendar-2026` and `editable-day-2026` fixtures (timestamps normalized).
- Visual loop PASS on `DAY-EDIT`, `DAY-SAVE-REVIEW`, `ENTRY-ADD`.
- `T-CAL-YEAR-02` now follows the month-prompt flow: browse Feb 2027 → prompt → cancel (nothing created) → prompt again → create 2027 → month populates → edit Feb 1.

Reply with your decisions (for example "approve D1–D6, save immediately, rename group to leave type") and I'll finalize.
