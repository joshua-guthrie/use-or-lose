# Issue #124 passdown

COMPLETE: #124 is closed, source committed/pushed, CI and Distribution green,
all five published assets verified, and local master synchronized with GitHub.

Issue: https://github.com/joshua-guthrie/fedleave/issues/124
Base: master at 78b3ba28d736e7203829ccd14c68dda4ce7c1fe6.
Source commit: ab23314f1e76cc9b6e6f94fc62f68288c28d8091.
Generated installer commit: 62246bee8ba348be354caaa61418808a21b6915c.
Published version: 0.2.2.dev0+gab23314f.
CI: https://github.com/joshua-guthrie/fedleave/actions/runs/41938002826
Distribution: https://github.com/joshua-guthrie/fedleave/actions/runs/41938002748
GitHub auto-closed #124 from Fixes trailer. #138 remains open/deferred.
User requested next issue, detailed commit and GitHub push when complete.
Initially investigated #138, but user explicitly deferred it pending more
detail in GitHub. No #138 source changes were made; leave it open and deferred.
Current task is solely #124, Break Patterns. #125 remains separate.

Existing local PASSDOWN-120/121/122/123/133/137 files are unrelated untracked
history. Leave them intact and do not stage them or this passdown accidentally.

Implemented:
- Pure fedleave.break_patterns over private read-only snapshots, canonical
  transaction effects and shared fingerprint-bound coverage.
- Discretionary grouping defaults to annual/restored/comp/travel comp/credit/TOA;
  sick stays separate context. Explicit Sick, All physical absence and custom
  groups. FMLA tracking, overtime and non-used/rollover/disposal rows excluded.
- Daily scheduled hours, full/partial/nonworking/unknown classification, raw
  selected/other context hours, ratios capped only at 1, overbooking warnings,
  original IDs, source years and schedule/holiday assumptions.
- Maximal recorded intervals include adjoining nonworking dates but need at
  least one fully absent workday. Partial/no selected absence interrupts them.
  Unknown coverage terminates intervals and covered gaps; cross-year joins
  require actual contiguous known dates. Truncation flags retain lower bounds.
- Recorded/Upcoming intervals are scanned separately. Future continuations link
  back to recorded portions but never complete/reset recorded metrics. Ongoing
  qualifying intervals at cutoff show Break in progress; unknown completion
  has its own status instead of falsely claiming no qualifying break.
- Fully bounded completed-length median and distribution; separate provisional
  observed lengths. Day classification and month/quarter timing tables. No
  removed weekday/concentration metric reintroduced.
- Since-last-known-completed break reports elapsed calendar and scheduled days;
  unknown intervening coverage gives N/A. Largest covered scheduled-workday gap
  retains smaller/sick absence context and explicit partial-history boundaries.
  It never claims actual attendance.
- Analytics Break Patterns tab reuses exact backend/store/year/font/PDF context,
  read-only worker loading/progress, cached recalculation, explicit GUI Slots,
  cancellation/stale-result guards, shared coverage preferences, protected
  exports, sorted source drilldowns, CSV and existing chart PNG/PDF/Print.
- Default regular-schedule assumption is visible and can be disabled. Complete
  backend historical rosters take precedence; unsupported schedules/context are
  N/A. Raw confirmed usage remains visible without known schedule context.
- Extracted history.analysis_calendar from Cumulative Usage's helper; its
  existing calculation behavior remains unchanged. No reader/backend workaround
  for deferred #138. No new executable or general CLI analytics command.
- Offline Help/F1, README, Calendar help, architecture and changelog updated.
  Installed Analytics smoke exercises the view and both charts on both platforms.
  Public download URLs unchanged; no packaging workflow edits.

Validation:
- Initial core/usage-comparison regression suite: 53 passed in 2.04s.
- Focused new GUI/domain plus existing Analytics/usage: 79 passed in 11.72s
  before final boundary/status and metadata refinements.
  /tmp/fedleave-124-verified-focused.log.
- Latest Python 3.13/Qt new-domain/GUI + usage suite: 66 passed in 11.13s.
  /tmp/fedleave-124-final-qt313.log.
- make package passed; /tmp/fedleave-124-package.log.
- Whole Analytics real backend smoke + bundled help + both Break Patterns
  charts passed against read-only legacy files; canonical tree unchanged.
  /tmp/fedleave-124-smoke.log.
- Final make check PASSED: formatting/lint/mypy and 1,067 tests in 232.55s.
  Python 3.11; /tmp/fedleave-124-check.log.
- Final package rebuilt after Help wording refinement; all four new modules and
  final bundled Help verified in dist/fedleave-0.2.2-py3-none-any.whl.
- Visual review passed at 1320x860 and full chart sizes:
  /tmp/fedleave-124-break-patterns.png, -distribution.png, -timeline.png.
  Reproduce using /tmp/fedleave-124-visual.py.
- Tests include real read-only cross-year legacy storage, source/period identity
  after sorting, failed preference saves, exports, GUI-thread rendering, cutoff
  limits, cancellation/source-change/close guards and refresh control preservation.

Publication verified:
- CI 41938002826 succeeded: formatting/lint/mypy/package green, Linux 1,067
  passed in 309.09s, Windows 1,060 passed and 7 skipped in 334.89s.
  Evidence: /tmp/fedleave-124-ci.log.
- Distribution 41938002748 succeeded on its first attempt. Both packages,
  Windows install/uninstall/data preservation and installed MCP smoke, Linux
  bootstrap, and released runtime tests on Ubuntu 22.04 and Debian 12 passed.
  Evidence: /tmp/fedleave-124-distribution.log.
- Publication replaced all five beta assets, moved beta to the source SHA,
  updated release notes/version, and committed matching installers through CI.
- /tmp/fedleave-124-verify-publication.py verified BUILD.txt source/version,
  beta source tag/release notes, all five asset sizes and SHA-256 digests against
  Git installer mirrors/LFS pointers, and both package checksum files.
- Linux archive: 291991782 bytes,
  SHA-256 5b0cdc2eef09ad5b48ae0821619e08440b4b61297f669483cd73e16cd395f6cf.
- Windows installer: 210465778 bytes,
  SHA-256 53e813855200f25a3ce22bcb4d3729eddf0b6c749f53f5b9664d024715f4e30d.
- README public download URLs unchanged. No manual generated-installer edits.
- Local master fast-forwarded to 62246bee8ba348be354caaa61418808a21b6915c;
  master...origin/master is 0/0. No staged or unstaged tracked changes remain.
  Local untracked passdown files remain intentionally outside the commit.

Next action: none for #124. Stop here. Do not start #125 or resume deferred
#138 without new user direction. No known blocker remains for this issue.

Latest live quota at completion: 44% used / 56% remaining in the five-hour
window, reset 2026-11-05T03:33:29Z; weekly 78% remaining. Always recheck
codex-usage --json before interpreting future availability.
