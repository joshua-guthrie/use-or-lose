# Issue #123 passdown

Complete, committed, pushed, published and closed. No required work remains.
Do not start another issue without a new user request.

Issue: https://github.com/joshua-guthrie/fedleave/issues/123
Source: 3f8c857968749fdad911c57ff64145b5927f6bb3
Installer mirror: 78b3ba28d736e7203829ccd14c68dda4ce7c1fe6
Published version: 0.2.2.dev0+g3f8c8579
CI: https://github.com/joshua-guthrie/fedleave/actions/runs/41261780848
Distribution: https://github.com/joshua-guthrie/fedleave/actions/runs/41261780850

Both workflows succeeded on the first attempt. Both platform packages,
installed smoke tests, Ubuntu 22.04 and Debian 12 released-runtime checks,
stable beta publication and generated installer commit passed. Verified
BUILD.txt, beta tag/release source and version, and all five release asset
digests/sizes against repository mirrors/checksums with:

    .build/issue120-debug/venv/bin/python /tmp/fedleave-123-verify-publication.py 3f8c857968749fdad911c57ff64145b5927f6bb3

Linux archive: 291624895 bytes,
sha256 4a0cbbd5aaa1724e077ac3cbc321331c823ee7da69ab4b2ede18c3062bb6679f
Windows installer: 210100741 bytes,
sha256 bdef635ef188f8f795eae1ef56244261c0f2545f7736403021b35286e7a6959b
All five rolling assets, including checksums and bootstrap, matched.
Public download URLs are unchanged. Generated installers were never hand-edited.

Local master fast-forwarded to the generated mirror commit and matches
origin/master. No tracked changes remain. Only local passdown files are
untracked: PASSDOWN-120/121/122/123/133/137. Leave these local histories intact;
they were intentionally not included in the source commit.

The detailed commit message documents behavior, rationale and validation.
Fixes #123 automatically closed the issue on push; no separate issue comment
was posted.

## Implemented

- Cumulative Usage tab in the existing Analytics companion, reachable through
  Calendar Analysis > Analytics. Reuses exact backend/datastore, leave year,
  font, PDF folder, Refresh, CSV, chart popup and contextual offline Help/F1.
  No executable or CLI analytics command added.
- Pure usage model with actual period ordinal/zero-based offset and exact
  calendar month/day alignment. Missing 27th/short periods and February 29 are
  null, never clamped. Two Januaries use calendar-year offset from year start.
- Default annual category or explicit physical-absence category sum. Canonical
  status/void effects; openings, accruals, corrections, disposals, awards,
  overtime and FMLA tracking never become used hours. Original IDs preserved.
- Fingerprint-bound coverage shared with Forecast. Cumulative values require
  complete confirmed coverage from the actual year start through mapped date.
  Unknown/stale coverage is null; confirmed zero is zero. Exclusions explicit.
- Target Confirm through cutoff stops at the chosen date, including a partial
  current period, and clears future selected coverage. Extending cutoff requires
  new confirmation. References retain full-year/selected-period controls.
- Recorded Through Cutoff, Scheduled future target bookings, and historical
  later context remain separate. Planned/requested recorded statuses warn;
  optional confirmed filtering does not hide future effective target bookings.
- Exact median/linear p20/p80, counts, deltas and zero-median handling. Example
  104 vs 64/80/96 gives 80 median, +24 hours, +30%. Shading only at points with
  at least three references; limited/no history explicit, not a confidence band.
- Hours per 100 scheduled work hours requires complete backend historical
  roster or an explicit current-schedule assumption. Configured workdays/tour
  minus lunch and cached holidays determine exposure. Unknown/zero exposure
  stays N/A without hiding raw hours. Irregular historical rosters not inferred.
- Exact chart/table/CSV metadata and nulls. Sorted drilldowns preserve identity;
  derived statistics first show contributing year totals, then transactions.
- Source loading/progress and calculations run in revision-bound workers;
  controls reuse cached snapshots. Explicit Qt Slots keep rendering on GUI
  thread. Source change, Cancel and shutdown discard stale results.
- Guarded exports and analytics preferences live outside canonical data. Real
  read-only legacy data survives the full lifecycle byte-for-byte.
- README, offline Analytics/Calendar help, architecture and changelog updated.
  Installed Analytics smoke renders the new view in both platform bundles.

Shared helper details:
- history strict accrual-calendar validation remains the default. Comparison
  opts into contiguous numbered actual-period validation for unusual lengths/
  counts; accounting, rollover and Forecast calendar semantics are unchanged.
- Coverage assertions optionally include confirmed_through; Forecast respects
  partial covered dates without treating a partial period as complete.
- Generic snapshot/period fingerprints retain malformed nonobject records so
  descriptive analysis can warn and return unknown instead of crashing.
- Existing balance comparison commands and other Analytics definitions retain
  behavior; removed weekday/concentration metrics were not reintroduced.

## Validation and artifacts

- Final local make check: 1030 passed in 325.98s; format/lint/configured strict
  mypy passed. /tmp/fedleave-123-verified-check.log.
- Exact pushed-source CI: Linux 1030 passed (271.21s); Windows 1023 passed,
  7 platform-specific skips (418.01s). /tmp/fedleave-123-ci.log.
- Final Python 3.13/Qt 6.11.1 new domain/GUI tests: 40 passed in 43.51s.
  /tmp/fedleave-123-final-qt313.log.
- Explicit Qt Slot/render-thread assertion suite: 11 passed in 37.63s.
  /tmp/fedleave-123-final-threading.log. Exact-source CI also exercises these.
- Cutoff-bounded comparison/Forecast regression suite: 80 passed in 47.04s.
  /tmp/fedleave-123-final-domain-gui.log.
- Final make package passed; verified comparison modules, GUI Slots and offline
  help in wheel. /tmp/fedleave-123-verified-package.log.
- Real-backend all-views Analytics smoke and bundled help passed against
  read-only legacy files; complete canonical tree unchanged.
  /tmp/fedleave-123-smoke.log.
- Visual review: /tmp/fedleave-123-comparison.png and -chart.png; reproduce with
  /tmp/fedleave-123-visual.py. Summary immediately exposes 104/80/+24/+30%.
- Successful Distribution log: /tmp/fedleave-123-distribution.log.

One alternate-runtime test initially used a chained temporary QAction/QMenu
wrapper, which Qt deleted prematurely. Retaining explicit action/menu references
in the test, matching existing Forecast tests, resolved it. No product failure
or packaging/workflow workaround was needed.

## Final quota and stopping point

User requested work until complete or a safe checkpoint with 20% of five-hour
usage remaining, followed by a passdown; detailed commit/push if complete.
Initial remaining quota was 81%. Final LIVE reading: ok=true, allowed=true,
primary 61% used / 39% remaining, reset 2026-11-01T02:33:06Z; secondary 59%
remaining. Completed before the checkpoint. Recheck live codex-usage --json
with escalation before starting new work; never infer quota from old readings.
