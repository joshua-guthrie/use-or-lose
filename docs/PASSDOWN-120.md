# Issue #120 passdown — completed

## Completed after the next user-requested resume

Issue #120 is closed as completed with the detailed comment:
https://github.com/joshua-guthrie/fedleave/issues/120#issuecomment-6484165622

Final source `2b9abaff20af3dadf9a7174acc2a3008b69762b9` passed CI
`40615864697` on both platforms and all Distribution jobs `40615864699`,
including Windows installation and independent Ubuntu 22.04 / Debian 12
released-runtime checks. Local master was fast-forwarded to the generated
installer commit `4a167ab`. Verified BUILD.txt, beta tag, release source, and
all five release asset digests/sizes against committed mirrors. Public URLs
are unchanged. No remaining issue #120 work is required.

The user requested the next issue (#121, Usage Forecast), with the same
15%-remaining quota passdown/stop condition. Live quota at that resume was
99% remaining. The checkpoints below are retained as history only.

## Latest checkpoint — 2026-10-28 01:38 UTC

The user explicitly resumed the task and repeated the stop-at-15%-remaining
five-hour quota instruction. This resume supersedes the original nightly stop
recorded below. Current work is authorized; issue #120 is still open.

- Diagnosed both previous CI failures and Linux packaging failure as native
  PySide6 6.12.0 table-rendering reference corruption on Python 3.11. A
  synchronous rendering probe also crashes, excluding worker lifecycle as the
  cause. The same probe passes with Qt 6.11.1.
- Pushed **2b9abaff20af3dadf9a7174acc2a3008b69762b9** to master: GUI dependency
  bound `<6.12`, subprocess rendering/cleanup/exit regression, changelog entry.
  Detailed commit message records the diagnosis and validation. No workflow or
  installer checks were bypassed. Public URLs remain unchanged.
- New regression demonstrably fails with SIGABRT on Python 3.11 / Qt 6.12.0
  and passes with Qt 6.11.1 and 6.11.3.
- **886 tests passed** in each full `make check`: ordinary Python 3.13 / Qt
  6.11.1 and isolated Python 3.11.18 / Qt 6.11.3. All quality checks passed.
  Logs `/tmp/fedleave-120-resume-check.log` and
  `/tmp/fedleave-120-py311-check.log`. `make package` passed; wheel metadata
  includes the corrected dependency. Log `/tmp/fedleave-120-resume-package.log`.
- Final CI run **40615864697**: quality/package job passed; Ubuntu passed all
  886 tests; Windows tests still running at this checkpoint.
- Final Distribution run **40615864699**: metadata passed; Linux and Windows
  PyInstaller builds in progress. Require complete success before closeout.
- Updated release/mirror verifier:
  `/tmp/fedleave-120-verify-publication.py` expects source `2b9abaf...`.
- Detailed closeout draft `/tmp/fedleave-120-closeout.md` has a
  `PUBLICATION_VERIFICATION_PENDING` marker. Replace it with actual final CI,
  installer, runtime, beta/mirror verification and run links before posting.
- Isolated debug tools/environments are in ignored `.build/issue120-debug/`
  and `/tmp/fedleave-120-*`. The original `.venv` was not modified. `/tmp`
  filled during dependency download, so the package cache/environments moved
  to `.build`, where adequate disk space is available.

Remaining: require final Windows CI and all Distribution jobs to pass; fetch
and fast-forward to CI's installer commit; verify BUILD.txt/beta tag and all
five release asset digests/sizes against committed mirrors; post detailed
comment and close #120 as completed. Check live quota periodically and update
this passdown/stop if it reaches 15% remaining. Last live quota was 82% remaining.

---

## Original nightly stopping record (historical)

Local handoff only; leave this file uncommitted unless the user requests its
publication. The implementation itself is already committed and pushed.

Stopped at **2026-10-27 02:02 UTC (October 26, 10:02 PM EDT)** because the live
Codex five-hour quota reached **15% remaining**. The user explicitly requested:
“if you reach 15% of the 5 hr usage, find a good stopping point and create a
passdown, and stop your efforts for tonight.” We stated the interpretation as
15% remaining. **Do not resume tonight merely because the quota resets.** Wait
for the user to resume the task.

## User request and current status

Implement [#120 — Add a read-only what-if leave planner](https://github.com/joshua-guthrie/fedleave/issues/120),
commit/push with detailed comments, and close the issue with a detailed comment
when fully complete. The issue body and supplemental comments are substantial;
the newest placement instruction is **Analysis → Planning**, with a relevant
menu name.

Implementation, documentation, local verification, commits and pushes are done.
**Final hosted CI, installer publication verification, and issue closeout remain.**
Issue #120 was intentionally left open. No closeout comment has been posted.
No known local test failures remain.

Repository: `/home/jlguthri/bin/fedleave`; branch `master`.

- Feature commit: `692bf9be644362ad339c462e5170a161bfaaa6d4`
  ([GitHub](https://github.com/joshua-guthrie/fedleave/commit/692bf9be644362ad339c462e5170a161bfaaa6d4)).
  Detailed commit message explains behavior, architecture, safeguards, exports,
  tests, and installer completion requirements. Eighteen files changed.
- Final commit: `26d7e10cdb232025479582e5337b07f5edb4b089`
  ([GitHub](https://github.com/joshua-guthrie/fedleave/commit/26d7e10cdb232025479582e5337b07f5edb4b089)).
  Small test-only Windows portability correction: compare backend arguments to
  `str(selected_path)` rather than hardcoded Unix separators. The seven model
  tests and lint/format checks passed after this correction.
- Both commits are pushed to `origin/master`. The working tree was clean before
  writing this local passdown. This passdown should be the only untracked file.

Final-commit workflow runs at the stopping point:

- [CI run 40435978714](https://github.com/joshua-guthrie/fedleave/actions/runs/40435978714):
  **in progress**.
- [Distribution run 40435978702](https://github.com/joshua-guthrie/fedleave/actions/runs/40435978702):
  **pending** (the previous commit's distribution was being superseded).

Older feature-commit runs are CI `40435787138` and Distribution `40435787018`.
Use the final commit/run IDs for completion. Do not mistake a superseded run
for the final result.

## Implemented behavior and source map

- Calendar **Analysis → Planning → What-if Planner...** launches the existing
  `FedLeaveAnalytics` with `--view planning`. The existing Analytics tab also
  contains **Planning → What-if Planner**. Backend, resolved datastore, year,
  font size and PDF folder are forwarded. No new executable or planning CLI
  command was introduced. Existing analytics calculations remain compatible.
- `src/fedleave/planning.py`: pure, Qt-independent, defensive-copy engine.
  Schema-v1 scenario definitions and typed projection results; method version
  `daily-closing-canonical-v1`. The engine accepts snapshot data, definitions
  and an injected date, with no datastore path, repository or writer callback.
- Core entry points: `new_scenario`, `make_operation`, `validate_scenario`,
  `project`, `export_scenario`, `import_scenario`, `scheduled_transactions`.
  Typed dataclasses include scenario operations, category comparisons, daily
  balances, capacity days, lot effects, events and structured warnings.
- Add/replace/remove operations use stable scenario-only IDs, explicit original
  future-used IDs and material fingerprints. Replacements do not double count;
  removal suppresses originals only in the scenario. Conflicting targets,
  historical/accrual/synthetic targets and stale material references are
  rejected atomically. Unrelated source changes do not invalidate unchanged
  references. Delete restores originals; Reset clears operations and retains
  general controls/reserves.
- Calculations reuse canonical balance directions, status/void eligibility,
  automatic accrual posting/rate changes, parental rules, annual carryover and
  chronological expiration allocation. Derived disposals are recalculated on
  copies. Negative scenarios remain visible. Categories are never pooled.
- Results contain daily closing curves, cutoff/horizon balances, earliest
  minima, reserve breaches (strictly below; equality allowed), negative dates
  and shortfalls, actual-year-end annual pre-cap/carryover/forfeiture values,
  lot effects, provenance and capacity conflicts. Unknown metrics are null/N/A.
- Capacity uses configured workdays/tour/lunch, cached observed holidays, and
  scheduled physical absence across categories. It does not redistribute
  tentative dates/hours. Irregular rosters and missing context are disclosed.
  Purpose-restricted choices stay labeled; unmodeled entitlements are unknown.
- Historical cutoffs use current records, not historical knowledge. Past
  planned/requested absences remain unresolved and labeled Recorded Through
  Cutoff. That limitation is included in scenario definitions.
- `cli_helpers.read_snapshot_payload` extends the **existing** read-only
  `snapshot --json` command additively. It includes configured schedule and
  increment, cached holiday coverage, source identity, warnings and synthetic
  accrual IDs. It never creates a store/cache/backup or persists normalization.
- `src/fedleave_analytics/planning_model.py`: session/view model, exact table
  rows and metadata-rich CSV, JSON exchange, atomic export destinations.
- `src/fedleave_analytics/planning_view.py`: editable UI, stale-reference
  resolution, cancellation and revision-bound thread-pool workers. Edits reuse
  cached snapshots; file imports refresh through the selected CLI before
  validation and require explicit Replace/Merge. Rejected imports preserve the
  active scenario. Old worker results cannot replace newer controls/source.
- `charts.py`: baseline/scenario line chart, reserve/cutoff markers, scheduled
  circles vs tentative squares, supporting data and reused chart popup. CSV,
  PNG, PDF and Print are available. File exports are atomically replaced
  outside the canonical datastore, protecting existing hard links too.
- `app.py` installed smoke mode now opens Planning, adds tentative use, and
  resets it. Existing distribution smoke scripts already verify datastore
  hashes and now exercise the new view automatically on both platforms.
  Windows offscreen smoke explicitly registers Arial regular/bold fonts; Qt's
  Windows offscreen backend otherwise uses missing-font box glyphs.
- README, calendar help, architecture documentation and changelog are updated.
  Stable installer/download URLs were not changed.

## Completed validation

- **Final `make check`: 885 tests passed**, formatting/lint passed, scoped strict
  mypy passed. Full check ran outside the sandbox because sandboxed MCP stdio
  tests time out. Log: `/tmp/fedleave-issue-120-final-check.log`.
- **Final `make package`: source distribution and wheel built successfully.**
  Log: `/tmp/fedleave-issue-120-final-package.log`. Verified the wheel contains
  `fedleave/planning.py`, `fedleave_analytics/planning_model.py` and
  `fedleave_analytics/planning_view.py`.
- After the Windows-path test-only correction, all **7 model tests** and
  Ruff lint/format checks passed. Production source is unchanged from the full
  885-test check.
- Focused calculation, snapshot, view-model and Qt regressions cover the
  92/68-hour add example, 84-hour replacement, 108-hour removal, reset, minimum
  and negative dates, zero/equal reserves, fractional/nonfinite/out-of-range
  input, ineffective statuses, same-day cross-category capacity, configured
  tours/holidays, lot spanning/links/expiry/extensions/disposal recalculation,
  uncertain policy/provenance, PPL/FMLA parity, stale/atomic import, worker
  cancellation and ledger nonmutation.
- Real CLI/GUI lifecycle test verified unchanged hashes for a legacy datastore
  with OS-read-only files, through load/edit/export/import/reset/failure/
  cancellation/close. Missing stores remain absent. Engine tests prohibit
  filesystem opening and assert unchanged input dictionaries.
- Source `FedLeaveAnalytics --smoke-test` ran through the real sibling CLI:
  Seasonality and Planning rendered, tentative use/reset worked, and the
  datastore tree hashes were unchanged.
- Final planner UI, chart PNG and PDF were visually inspected. Temporary
  artifacts: `/tmp/fedleave-issue-120-planner.png`,
  `/tmp/fedleave-issue-120-chart.png`, `/tmp/fedleave-issue-120-chart.pdf`,
  `/tmp/fedleave-issue-120-chart-pdf.png`. No clipping was observed.
- `git diff --check` passed. No generated installer was manually edited.

## Resume and finish

1. Honor the user's stop-for-tonight instruction; resume only when asked.
   Read this passdown, `AGENTS.md`, and check current `git status`/remote state.
   The issue specification is also saved in `/tmp/fedleave-issue-120-spec.md`
   if still present; fetch issue #120 and supplemental comments if needed.
2. Check final CI `40435978714` and Distribution `40435978702`. Fix genuine
   failures within scope, commit with a detailed message and push, then require
   successful **new final-commit** runs. GitHub connector tools support job
   status/logs; `gh run view/list` supports push-run discovery. `gh` needs
   network escalation in this environment.
3. Distribution must build/test Windows and Linux, run released Linux runtime
   checks on Ubuntu 22.04 and Debian 12, replace the five stable `beta` assets,
   and commit matching rolling installers. Do not bypass/defer publication or
   manually modify `installers/`. Source publication is incomplete until this
   succeeds; user-directed stopping does not mean the issue is complete.
4. After publication, `git fetch --no-tags origin master` and fast-forward local
   master to the CI installer commit (`git merge --ff-only origin/master`).
   Preserve this untracked local passdown. Verify `installers/BUILD.txt` names
   the final validated source SHA and the remote `beta` tag points to that SHA.
5. Verify release assets match committed mirrors: compare GitHub release asset
   `digest`/`size` to the Git LFS pointer `oid`/`size` for the Linux archive and
   Windows EXE, plus the committed checksum files. Compare SHA-256/size of the
   other three ordinary files too. The five names are the two rolling binaries,
   their `.sha256` files, and `install.sh`. Local `git lfs` is not installed;
   `git show origin/master:installers/...` reads pointer blobs without needing
   to download binaries. The previous task's helper is available at
   `/tmp/fedleave-issue-136-verify-publication.py`; copy/adapt its expected SHA,
   rather than using its old issue #136 source SHA.
6. Only once final CI/publication/mirrors are verified, post a detailed #120
   closeout comment covering navigation, scenario semantics, the no-write
   architecture, assumptions/unknowns, validation counts and run/commit links,
   installer mirror verification and unchanged download URLs. Then close as
   completed. No issue-close keywords were used in commit messages, so it
   should still be open.

The relevant GitHub tools are `github_fetch_workflow_run_jobs`,
`github_fetch_workflow_job_logs`, `github_add_comment_to_issue` (uses
`pr_number: 120` for the issue comment) and `github_update_issue` with
`repository_full_name`, `issue_number: 120`, `state: closed`,
`state_reason: completed`. Discover their current schemas before calling.

If checking quota after the user resumes, use the `codex-usage` skill and live
`codex-usage --json`. The sandbox reports `codex_state_unwritable`; running the
helper with escalation succeeds. Never infer availability from earlier values
or copy credentials. Last observed quota was exactly 15% remaining, with reset
at `2026-10-27T02:14:07Z`; that reset does not revoke the user's nightly stop.
