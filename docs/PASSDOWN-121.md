# Issue #121 — completed

## Completed — 2026-10-29 01:29 UTC

Issue #121 is closed as completed with the detailed comment:
https://github.com/joshua-guthrie/fedleave/issues/121#issuecomment-6485345037

- Feature/source commits `ba104c6d6f86ac0629f4750f3b502bedf20180f3` and
  `1fd9e2ac9050967a860f501855e687b2de81e3a5` are pushed with detailed messages.
- Final local make check passed **937 tests**; final source/wheel package passed.
- Final CI **40798883111** passed: Linux 937, Windows 930 with 7 POSIX-only
  skips, plus quality/package/CLI-MCP inventory checks.
- Final Distribution **40798883121** succeeded: both installers, installed-app
  smoke, Windows uninstall/data preservation, Linux GLIBC/bootstrap checks,
  released runtimes on Ubuntu 22.04/Debian 12 and stable beta publication.
- Local master was fast-forwarded to generated installer commit
  `0cb89034dc7df8f1f664431043ef303698bae467`. Verified BUILD.txt, beta tag,
  release body and all five asset digests/sizes against committed mirrors for
  source `1fd9e2a...`, version `0.2.2.dev0+g1fd9e2ac`.
- Public download URLs are unchanged. No source work or issue closeout remains.
- Last live quota was **35% remaining**, above the user's nightly stop point
  and below their threshold for starting another issue. No next issue started.

Only local untracked PASSDOWN-120.md and PASSDOWN-121.md remain; source and
installer mirrors are committed and synchronized with origin/master. This
handoff remains intentionally uncommitted. Earlier working notes below are
historical and superseded by this completion record.

---

## Current final head and publication checkpoint

Final source **1fd9e2ac9050967a860f501855e687b2de81e3a5** is committed and
pushed with detailed messages. Feature commit is
**ba104c6d6f86ac0629f4750f3b502bedf20180f3**; follow-up clears stale coverage
context before refresh. Its new regression failed before the fix with five
old rows and passes after it. **All 937 tests pass** in final full Python 3.11
/ Qt 6.11.3 `make check`; all 11 forecast Qt tests pass on Python 3.13.
Final logs `/tmp/fedleave-121-cancel-check.log` and
`/tmp/fedleave-121-cancel-package.log` both passed. Source work is complete.

Final-head hosted CI **40798883111** and Distribution **40798883121** are in
progress; mirror verification and issue closeout remain. Initial feature runs
CI **40797990472** / Distribution **40797990363**
are superseded: do not accept them for the final head. Discover new runs with
`gh run list --commit 1fd9e2ac9050967a860f501855e687b2de81e3a5`. GitHub API
shell connections have been intermittent; connectors work for job inspection
once run IDs are known. Retry transient gh failures or use a suitable read-only
fallback. Do not bypass publication.

`/tmp/fedleave-121-verify-publication.py` expects final source `1fd9e2a...`.
Detailed draft `/tmp/fedleave-121-closeout.md` records both commits and 937 tests,
with a `PUBLICATION_VERIFICATION_PENDING` marker to replace after real success.
The only untracked files should be local PASSDOWN-120.md and PASSDOWN-121.md.

Last live quota: **49% remaining**, reset 2026-10-29T03:32:54Z. The user's
previous over-60%-remaining condition for another issue does not apply after
#121. Finish #121 publication/closeout, unless near 15% remaining first.

The older working checkpoint below is retained as implementation history.

The user explicitly requested the next issue, detailed commit/push and issue
closeout when complete. Check the live five-hour Codex quota with the
`codex-usage` skill. Near **15% remaining**, finish a safe checkpoint, update
this handoff and stop for the night. Do not resume after a reset unless asked.
This file is a local handoff only; leave it uncommitted.

## Current state

Issue #120 was fully completed and closed before starting #121. Its source is
`2b9abaff20af3dadf9a7174acc2a3008b69762b9`; CI/Distribution passed and all five
beta asset digests/sizes matched installer mirrors. Local master is at bot
installer commit `4a167ab`. Closeout:
https://github.com/joshua-guthrie/fedleave/issues/120#issuecomment-6484165622

**Issue #121 implementation is currently uncommitted.** No #121 push, hosted
CI/Distribution, or issue closeout has happened yet. Master source changes and
new files are the work described below. Do not include this handoff or the
untracked `docs/PASSDOWN-120.md` in the feature commit.

Repository rules require successful Distribution for every master source push:
both installers, released Linux runtime tests, stable beta publication and
matching committed mirrors. Never bypass this or manually edit installers.
Public URLs are unchanged.

## Specification

Original issue and supplement are saved in `/tmp/fedleave-121-issue.md` and
`/tmp/fedleave-121-comments.md`. The supplement is ~37 KB and authoritative
alongside the original. Latest placement override:
**Analysis → Planning → Usage Forecast...**. Existing Analytics also contains
Planning → Usage Forecast. No new executable or general forecasting CLI.

## Implementation

- `fedleave/history.py`: pure shared historical coverage/eligibility. Uses
  canonical `validate_pay_periods`; confirmation records identify source,
  period dates and material transaction fingerprints. Whole-year/selected-PP
  confirmation and revocation. Unconfirmed/stale coverage is missing, confirmed
  zero usage is zero. Display-only edits preserve confirmation. Historical
  exposure uses current schedule with a warning unless explicit historical
  schedule context exists. Canonical effective-used eligibility; confirmed
  status option; malformed usage excludes affected category observations.
- `fedleave/planning.py`: extracted public `working_calendar` from the existing
  capacity implementation; both historical exposure and planner capacity reuse
  this configured-tour/cached-observed-holiday logic. Existing results retained.
- `fedleave/forecast.py`: pure typed/versioned forecast and retrospective
  result dataclasses. Month/exposure seasonal rates, three-year seasonal
  threshold, independently eligible whole-year fallback, median and linearly
  interpolated p80. January segments at both year edges combine for source
  rates; target segments remain distinct. Per-category/per-segment scheduled
  subtraction prevents double counting. Annual/sick/both modes; proportional
  category quotas and capped largest remainders share date capacity without
  capping to leave balance. Deterministic increments/date ties; sub-increment
  residuals remain explicit unallocated demand. Shared `project` supplies all
  balances/accruals/expiration. Estimated provenance is distinct. Allocated
  curves and hypothetical full-demand balances are separate; timing-dependent
  effects/unknown estimates yield N/A. Retrospective checks train only on
  selected earlier years, compare total estimated use with later records,
  report both scenario errors/MAE/sample counts/whole-year comparison, and
  explicitly disclaim reconstructed historical bookings.
- `fedleave_analytics/snapshots.py`: one cached read-only CLI acquisition class
  reused by planning and forecasting. Exact selected backend/data dir,
  `years --json` then `snapshot --json`, 30-second bounded reads, defensive
  cached copies, no fallback or store initialization. `planning_model` now
  delegates to it; corresponding mock targets updated in model tests.
- `preferences.py`: versioned `analytics.json` beside existing GUI settings,
  scoped by source. Coverage/reference/exclusion data only, atomically written
  outside the canonical datastore. Existing display/backend settings reused.
- `forecast_model.py`: same-result summary/monthly/daily/source/allocation/
  event/lot/warning tables and metadata-rich CSV. Unknown categories are N/A
  consistently; result JSON retains full metadata/provenance/coverage.
- `forecast_view.py`: existing Analytics widget with cutoff/horizon/scope/
  category/status/reserves, reference/exclusion checkboxes, side-by-side year
  and pay-period coverage tables, confirm/revoke controls, retrospective checks,
  chart/table exports. `_Task` worker reused from planner; revision checks
  reject late load/calculate/confirmation/retrospective results. Edits reuse
  cached snapshots. Coverage preferences are only saved after current results.
  Source drilldown is keyboard-activatable; chart windows use existing export
  guards and clean themselves from the owner's list when deleted.
- `charts.py`: existing renderer accepts configurable series; forecasting
  renders Scheduled/Typical/Higher with distinct line styles. Raw rate cells
  show sufficient precision. Existing PNG/PDF/Print windows reused.
- `app.py`/Calendar: Usage Forecast in Planning, `--view forecast` startup,
  global Refresh/Open Chart/Save CSV route to active planning tab. Close/source
  changes invalidate both views. Installed Analytics smoke now opens Forecast,
  verifies Scheduled, changes to combined mode and renders without ledger
  writes. Existing package MCP smoke hashes cover this flow.
- README/help/architecture/changelog updated. Installer URLs unchanged.

## Verification

- Initial full `make check` **935 tests passed** on both Python 3.13/Qt 6.11.1
  and Python 3.11/Qt 6.11.3, all quality checks green. Logs:
  `/tmp/fedleave-121-check.log`, `/tmp/fedleave-121-py311-check.log`.
- After final canonical coverage-validator and per-category N/A fixes,
  **111 focused tests passed**, including existing planner/model/Analytics Qt
  suites and all forecast tests. `/tmp/fedleave-121-final-focused.log`.
- Final full Python 3.11 `make check` is running at checkpoint time. Expected
  total **936** after adding the N/A consistency regression. Log:
  `/tmp/fedleave-121-final-check.log` (exec session 19246 at checkpoint).
- `make package` passed initially; final rebuild in progress in
  `/tmp/fedleave-121-final-package.log`. Verified wheel includes all six new
  core/companion modules.
- Real backend/Qt lifecycle test verifies unchanged complete datastore hashes
  for OS-read-only legacy ledgers through coverage preferences, forecasting,
  retrospective checks, export/cancel/close. Missing stores remain absent;
  canceled in-flight loading cannot install late data or create preferences.
- Calculation cases include double-counting examples, seasonal/fallback
  populations, January edges/leap days/27 periods, zero/missing exposure,
  canonical statuses, source drift, competing capacity, negative/recovering
  balances, unallocated demand and unknown year-end sensitivity.
- UI/chart PNG/PDF visually inspected. Fixed collapsed pay-period coverage by
  placing year/PP tables side by side, and put balance outcomes first in the
  Summary. Artifacts `/tmp/fedleave-121-ui.png`, `-chart.png`, `-chart.pdf`,
  `-chart-pdf.png`; generator `/tmp/fedleave-121-visual.py`.
- Ruff/check-format and `git diff --check` passed before final full runs.

## Finish

1. Inspect final local check/package logs and resolve any failures. Review diff
   and artifact inclusion; do not write generated installers.
2. Commit source/tests/docs with a detailed message and push master (authorized).
   `.git` writes and network require tool escalation. Preserve local handoffs.
3. Discover exact source-SHA CI and Distribution runs (`gh run list --commit
   FULL_SHA`); connectors can inspect jobs/logs. Require full success on final
   head, fixing genuine failures rather than bypassing tests/publication.
4. Fetch and fast-forward to generated installer commit. Adapt
   `/tmp/fedleave-120-verify-publication.py` to the new source SHA; verify
   BUILD.txt/beta tag/release body and all five asset digests/sizes vs mirrors.
5. Post detailed #121 closeout comment with behavior, read-only architecture,
   coverage/algorithm/incomplete-demand assumptions, validations, commit/run
   links and unchanged URLs; close as completed. Then update this handoff.
6. If live quota approaches 15% remaining first, record exact state and stop
   for the night, leaving issue open if publication/acceptance is incomplete.

Quota at last live check: **66% remaining**, reset 2026-10-29T03:32:54Z. This is
historical; always check live rather than infer current availability.
