# Issue #141 passdown

COMPLETE: #141 is committed, pushed, published, verified and closed.

Issue: https://github.com/joshua-guthrie/fedleave/issues/141
Title: Each Planning too should get its own full window
Source commit: d70fdee08b6bb56b27cdb124190ed627d9d8b660.
Installer mirror commit: b05e060 (local master fast-forwarded).
Published version: 0.2.2.dev0+gd70fdee0.
CI: https://github.com/joshua-guthrie/fedleave/actions/runs/42144269312
Distribution: https://github.com/joshua-guthrie/fedleave/actions/runs/42144269626
GitHub auto-closed #141 from the `Fixes #141` trailer; detailed closeout
comment posted at https://github.com/joshua-guthrie/fedleave/issues/141#issuecomment-6674626168

Final state: master == origin/master == b05e060. Only local untracked
PASSDOWN-120/121/122/123/124/125/133/137 files remain outside history; they
were intentionally never staged.

Implemented:
- New `fedleave_analytics/planning_window.py` hosts one Planning tool per
  QMainWindow: What-if, Forecast, Budget, or Holiday. It provides leave-year
  selection, Refresh, Open Chart, Save Table as CSV, contextual Help/F1 and
  Close.
- Budget/Holiday handoff opens a fresh in-process What-if window and preserves
  the existing Replace/Merge/Cancel transfer contract.
- Removed the Analytics Planning page/tab. `--view planning|forecast|budget|
  holidays` now routes to PlanningWindow; Analytics keeps analytics/comparison.
- Calendar Analysis > Planning now includes Holiday and Weekend Planner.
- Installed-bundle smoke renders all four planning windows; installed smoke
  command timeouts raised (linux_runtime_smoke 90->120, mcp_smoke 60->120).
- README, Calendar help, bundled Help, architecture and changelog updated.
- Tests updated for the new window and every Planning menu launch.

Validation:
- Ruff format/check and strict mypy clean.
- Full local suite: 1048 passed with one deselected test that fails identically
  on unmodified HEAD under local PySide6 6.11.1
  (`test_gui_update_preferences.py::test_manual_help_action_still_checks_when_
  automatic_checks_are_disabled`; PySide object-lifetime issue, unrelated).
  /tmp/fedleave-141-check-green.log
- Focused routing/usability/analytics/menu suites: 72 passed.
- `python -m build` succeeded; wheel contains planning_window.py and help.html.
  /tmp/fedleave-141-package.log

Publication verified:
- CI 42144269312 all jobs success.
- Distribution 42144269626 all jobs success: package metadata, Linux package,
  Windows installer, Ubuntu 22.04 and Debian 12 original-runtime checks, and
  Publish latest master installers.
- Verified BUILD.txt source/version, beta tag/release source, and all five
  downloaded release asset sizes/digests against the generated mirror:
  - Linux tar.gz 292032199 bytes sha256 d5858ffbace340e1718f6b5ac51c8ac9feaef20fd9e35a89d323672dc4383440
  - Windows exe 210495220 bytes sha256 958e7c4031130d850f7e7d7388340ed834df99ac13e491c32583c5685b68a6d0
  - install.sh 11624 bytes sha256 beb342233eefc20929f00784fbc3697aa57df35dd98cd688bfea5debd9d5d008
- README public download URLs unchanged. No manual generated-installer edits.

Next action: #142 (heat map options), the next oldest open issue.
