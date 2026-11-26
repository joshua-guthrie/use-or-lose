# Issue #125 passdown

COMPLETE: #125 is committed, pushed, published, verified and closed.

Issue: https://github.com/joshua-guthrie/fedleave/issues/125
Title: Plan efficient breaks around holidays and scheduled days off.
Source commit: 8e097f4925cb922578d625cc03ccd3f1d7fdc420.
Installer mirror commit: 63ad211b3e37630c5ad61f9c2459699edc53a142.
Published version: 0.2.2.dev0+g8e097f49.
CI: https://github.com/joshua-guthrie/fedleave/actions/runs/41963803536
Distribution: https://github.com/joshua-guthrie/fedleave/actions/runs/41963803627
GitHub auto-closed #125 from the `Fixes #125` trailer at 2026-11-05T03:47:45Z.
#138 remains explicitly deferred until the user supplies more GitHub details.

Final state: master == origin/master == 63ad211 (fast-forwarded). Only the local
untracked PASSDOWN-120/121/122/123/124/125/133/137 files remain outside history;
they were intentionally never staged.

Implemented (as shipped in 8e097f4):
- New pure fedleave.holiday_planner: validated future search bounds inside one
  initialized year, default minimum 4 calendar days, maximum 40 new hours,
  maximum 21 interval days and top 20 results (public limit capped at 100).
- Bounded workday-boundary enumeration, maximal extension through connected
  nonworking/already-away dates, deduplicated dates and boundary flags. Separate
  No leave required section; no standalone weekends in efficiency rankings.
- Canonical effective used absence; existing discretionary vs other-purpose
  context separated. FMLA/overtime ignored for required hours; ineffective rows
  excluded. Mixed-purpose dependencies hidden by default; raw overbooking kept.
- Daily calendar from shared project/working_calendar and backend-cached observed
  holidays/manual overrides, represented period checks, explicit regular-tour
  assumption and unknown context. No network holiday fetch or cache mutation.
- Funding reuses budget.DatedFunding (formerly private _Funding), canonical
  lot state and projection engine. Known lots ordered by expiration/category/
  earned date/ID, then nonexpiring banks and annual. Required increments,
  on-date earnings, lot links, credit usability/caps and reserves validated.
- DatedFunding.independent() shares immutable context and resets operations and
  failures per alternative; no cumulative reservation/spending between options.
- Feasible-first stable efficiency/least/longest/expiring rankings, unknown/
  infeasible hidden by default, exact hours plus N/A zero-hour efficiency.
- Only bounded top set receives full canonical projection summaries: daily
  balances, minima/dates, ending balances, reserves and annual use-or-lose.
- Analytics Planning > Holiday and Weekend Planner; --view holidays in existing
  companion. New holiday_view reuses BudgetWidget worker/revision lifecycle with
  cooperative cancellation and explicit Qt progress Slot. Cached controls,
  source/year/font/PDF routing, sorted source row identity and exact detail tabs.
- Option table leads with decision metrics (additional required hours, funding
  feasibility, efficiency) ahead of the full audit columns; all audit columns
  and stable row/source IDs retained.
- holiday_model CSVs include parameters/source/units/assumptions; holiday_charts
  renders selected daily existing/proposed hours, supporting table and standard
  PNG/PDF/Print controls. Shared horizontal renderer accepts optional height;
  existing defaults unchanged, long options get enough vertical space.
- Fresh-source Open in What-if uses existing atomic transfer, generalized with
  a label and accept_option method, preserving existing Leave Budget defaults.
  Replace/Merge/Cancel still protects current drafts; no ledger writer added.
- README, Calendar help, bundled Help/F1, architecture and changelog updated.
  Installed Analytics self-test enters the new view and checks chart if present.
  No public installer URL changes. No packaging workflow edits.

Files (17, all shipped in 8e097f4):
- New: src/fedleave/holiday_planner.py
- New: src/fedleave_analytics/holiday_{model,charts,view}.py
- New: tests/test_holiday_planner.py, tests/test_holiday_planner_gui.py
- Modified: src/fedleave/budget.py; src/fedleave_analytics/{app,budget_model,
  planning_view,charts}.py; tests/test_forecast_gui.py
- Modified: README.md, CHANGELOG.md, docs/ARCHITECTURE.md,
  help/fedleave-calendar-help.html, src/fedleave_analytics/help.html

Validation (final, on current source):
- Full make check PASSED: formatting (223 files), ruff check, strict mypy, and
  1100 tests in 323.53s (Python 3.11 / Qt). /tmp/fedleave-125-check.log, EXIT=0.
- Focused holiday domain + GUI: 33 passed in 50.54s (plus a focused GUI rerun
  after the column reorder).
- make package PASSED: /tmp/fedleave-125-package.log. Wheel contains all four
  new modules (holiday_model.py at the reordered-column revision) and the
  bundled help.html with the "Holiday and Weekend Planner" section.
- Domain tests cover the passdown's flagged edge cases: expiry/independent
  alternatives, deadline ordering, future earnings + intermediate reserve,
  mixed-purpose rejection, disabled expiration/extension, unknown provenance +
  unsupported credit, credit same-day/disabled bank, explicit-lot non-reuse,
  plus a determinism assertion (asdict equality across reruns).

Environment note (IMPORTANT for local reruns): this shell sets FORCE_COLOR=1 /
CLICOLOR_FORCE=1, which leaks ANSI into CLI stdout-capture assertions and makes
~9 CLI tests fail spuriously. Setting NO_COLOR instead breaks 2 Rich help-width
tests. The clean recipe is to UNSET CLICOLOR_FORCE and FORCE_COLOR (keep color
otherwise), which yields a fully green 1100-pass make check. CI sets neither and
was green. Same applies to `gh` JSON parsing in the verifier: run it with
CLICOLOR_FORCE/FORCE_COLOR unset and NO_COLOR=1.

Publication verified:
- CI 41963803536 SUCCESS: format/lint/type/build, ubuntu-latest tests,
  windows-latest tests all completed success.
- Distribution 41963803627 SUCCESS: package metadata, Linux package, Windows
  installer, Debian 12 runtime, Ubuntu 22.04 runtime, and Publish latest master
  installers all completed success (both platform packages + installed smoke +
  older Linux runtimes).
- /tmp/fedleave-124-verify-publication.py 8e097f4... verified BUILD.txt
  source/version, beta tag source, release notes source, and all five rolling
  asset sizes and SHA-256 digests against the git installer mirror:
  - FedLeave-Latest-Linux-x86_64.tar.gz  292048693 bytes  sha256:8f1f7358c349823e0421ab34afaf16f060c6632d6a7bb1025d16a54005c11b5e
  - FedLeave-Latest-Linux-x86_64.tar.gz.sha256  102 bytes  sha256:b786c536d4062dcd78a559bb3fb7db20e56f11619c47ab5483364ecbab64ff02
  - FedLeave-Setup-Latest-Windows-x64.exe  210515267 bytes  sha256:1a80871432b5072102e390819137f64dcb2f4dd7d7078b4b2f5ec6d9ad78f79c
  - FedLeave-Setup-Latest-Windows-x64.exe.sha256  104 bytes  sha256:5b3640ecbe78156172975b7081d065c6696320a4db8d1eee46c25d3abaefcc96
  - install.sh  11624 bytes  sha256:beb342233eefc20929f00784fbc3697aa57df35dd98cd688bfea5debd9d5d008
- README public download URLs unchanged. No manual generated-installer edits.

Next action: none for #125. Stop here. Do not start deferred #138 without new
user direction. No known blocker remains for this issue.

Live quota at resume was the primary 5-hour window at 100% used (reset
2026-11-05T03:33:29Z); the user switched models, so the Codex window no longer
constrained this session. Always recheck codex-usage --json before interpreting
future availability.
