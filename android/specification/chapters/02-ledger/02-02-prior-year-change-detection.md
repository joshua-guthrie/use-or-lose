# Chapter 02.02 · Prior-year change detection (proposal for review)

**Status: proposal.** The mechanism below is a recommendation, not yet approved. The detection key already exists in shipped data; the new pieces are a read-only status surface, the acknowledgment marker, and the prompt UX. Review, then I11/I10 wire the apply side onto the existing carryover reconciliation.

## The problem

A linked leave year takes its starting balance from the previous year's closing balance. If the user later edits the *previous* year (for example, unexpected 2026 leave), the implied starting balance of 2027 no longer matches what was carried over. Today nothing tells the user; the 2027 ledger silently disagrees with its source until someone runs a manual reconciliation.

Goal: detect that a linked year's source (or its policy) changed, and let the desktop and Android apps offer a single, non-repeating prompt: **update the starting balance to match the changed previous year?** If the user declines, stay silent until the source changes *again*.

## Detection — already in the data

Every carryover-created year stores this in `leave_years/<year>.json`:

```json
"initialization": {
  "mode": "carryover",
  "source_leave_year": 2026,
  "source_fingerprint": "<canonical fingerprint of the 2026 ledger + config at creation>"
}
```

The fingerprint is the core's existing canonical `fingerprint({"ledger": source, "config": config})` — sorted-key JSON hashed once. **Detection is a pure, read-only comparison:**

1. Load the target's stored `source_fingerprint` and `acknowledged_source_fingerprint` (below).
2. Compute the current fingerprint of the source ledger + config.
3. If the current value equals the stored `source_fingerprint` → in sync, silent.
4. If it differs from `source_fingerprint` but equals `acknowledged_source_fingerprint` → a known change the user declined; silent.
5. Otherwise → **stale**: the source changed since the target was created and the user has not reviewed it. Surface the warning.

No polling and no new hashing scheme: the same pure `fingerprint()` is already bundled on the phone and used by the desktop, so both surfaces compute identical values from the same store bytes. Hashing one small JSON file on year-open is effectively free.

## Read-only status surface (new)

The CLI gains a read-only status command so scripts, the GUI, and tests share one detector:

```bash
use-or-lose carryover-status --year 2027 --json
```

Output per linked year (walking the chain when `--year` is omitted):

```json
{
  "ok": true,
  "stale": [
    {
      "year": 2027,
      "source_year": 2026,
      "state": "stale",                    // stale | acknowledged | in_sync | broken
      "source_changed": true,
      "policy_changed": false,
      "stored_source_fingerprint": "...",
      "current_source_fingerprint": "...",
      "recomputed_opening_balances": { "annual": 232.0, "sick": 600.0, "credit": 24.0 },
      "reductions": [ {"category": "annual", "hours": 8.0, "reason": "source_use_after_creation"} ]
    }
  ],
  "broken": [ {"year": 2028, "reason": "MISSING_SOURCE_YEAR"} ]
}
```

The recomputed openings reuse `cross_year.derive_carryover` (read-only; never writes) so the prompt can show the *exact* new numbers before the user decides. `T-YEAR-REC-01`'s existing reconciliation review remains the apply path; this chapter only adds detection and prompting.

## The prompt (desktop and Android)

When a surface reads a linked year whose status is `stale`:

> **2026 changed after 2027 was created.**
> 2027's starting balances were carried over when 2026 closed. Since then, 2026's records changed (for example, new leave use).
> **Update 2027's starting balance to match?** · Review the change · Keep 2027 as-is

- **Review the change** opens the existing carryover reconciliation review (shows before/after openings, reductions and affected rows — the I10 surface).
- **Keep 2027 as-is** records the acknowledgment (below) and dismisses the prompt.

Surfaces: the desktop GUI shows the warning on the affected year's calendar/report entry; the Android app shows it as a warning card on the affected year's balance/Calendar and as a row in leave-year maintenance. The CLI itself stays non-interactive: `carryover-status` reports; `carryover-reconcile` applies.

## Acknowledgment — "don't nag me again"

When the user declines, the app writes one field on the **target** ledger (the same single-writer publish used for every edit):

```json
"initialization": {
  "acknowledged_source_fingerprint": "<the fingerprint the user reviewed and declined>"
}
```

Rules:

- The marker lives on the target year file, not in per-device settings. The canonical store is the one artifact restored and shared across devices, so a decline made on the phone is honored by the desktop after the next restore, and vice versa. No separate hash store, no device-local state.
- The marker only suppresses the *exact* fingerprint reviewed. A later source change produces a new current fingerprint → the prompt returns (the user only ever declines a specific, reviewed state).
- Creating the target already sets `source_fingerprint`; acknowledging never alters it — the true creation identity is preserved for reconciliation.
- If the source changes again *after* a decline, the prompt returns; declining again simply records the new fingerprint.

## Recommendation summary

| Question | Recommendation |
| --- | --- |
| How is a change detected? | Compare the stored creation fingerprint with a freshly computed canonical fingerprint — no new hashing scheme; the key already ships in every carryover year |
| Where do desktop and Android agree? | Both compute the same pure `fingerprint()` over the same store files; the marker and identity live in the target ledger, so the shared store is the single source of truth |
| Where does the "don't nag" state live? | `initialization.acknowledged_source_fingerprint` on the target year — durable across restores and devices |
| How often do we check? | Lazily, on year-open/status/balance reads — no background polling |
| What counts as a change? | Source ledger edits *and* policy/config changes (the fingerprint covers both), reported separately by the status |
| What about chains? | `carryover-status` walks 2026→2027→2028; a stale upstream year marks every downstream linked year |
| Manual (non-linked) years | No source fingerprint exists → never flagged |

## Proposed acceptance additions (for the milestone that implements this)

| ID | Case | Expected |
| --- | --- | --- |
| `T-YEAR-CHG-01` | Create 2027 by carryover from the pinned fixture; then add 8 h of 2026 annual use. Read 2027's status, decline once, read again, change the source again, read again. | Status reports `stale` with the recomputed opening (232 h annual for the fixture delta); after decline the state is `acknowledged` and silent; a second source change returns `stale` again; the source and target ledgers are unchanged throughout |
| `T-YEAR-CHG-02` | Acknowledge on the phone store, restore that store on the desktop profile, read the status. | The desktop reports `acknowledged`, not `stale` — the marker traveled with the store |

## Apply path

Confirming **Review the change** enters the existing `YEAR-REC-01` flow (`carryover-reconcile` / `reconciliation_plan`), which already reviews and applies recomputed openings while preserving genuine destination activity. This chapter changes nothing about that flow; it only decides *when* to invite the user into it.
