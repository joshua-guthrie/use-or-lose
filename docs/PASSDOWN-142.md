# Issue #142 passdown

COMPLETE: #142 is committed, pushed, published, verified and closed.

Issue: https://github.com/joshua-guthrie/fedleave/issues/142
Title: heat map options
Source commit: 8cd99cd26927609f8e4cd03fc00b999cbade2777.
Installer mirror commit: ce73cd2 (local master fast-forwarded).
Published version: 0.2.2.dev0+g8cd99cd2.
CI: https://github.com/joshua-guthrie/fedleave/actions/runs/42147013337
Distribution: https://github.com/joshua-guthrie/fedleave/actions/runs/42147013369
GitHub auto-closed #142 from the `Fixes #142` trailer; detailed closeout
comment posted at https://github.com/joshua-guthrie/fedleave/issues/142#issuecomment-6674989047

Final state: master == origin/master == ce73cd2. Only local untracked
PASSDOWN-120/121/122/123/124/125/133/137/141 files remain outside history;
they were intentionally never staged.

Implemented:
- Calendar Heatmap keeps the existing continuous week-column heatmap
  unchanged and makes it the default "Year at a Glance" view.
- New "Monthly Calendars" view renders the whole leave year as separate
  month-by-month calendar grids, Monday-first, with day numbers, shared
  intensity shading and the existing red future-scheduled marker/"F".
- New `render_monthly_heatmap` in charts.py handles full, partial and empty
  leave years; existing `render_heatmap` is untouched.
- The leave-type selector applies to both layouts. Open Chart exports the
  active pixmap with a distinct monthly title/base name.
- Installed Analytics smoke switches between both layouts.
- README, bundled Help and changelog updated. Public installer URLs unchanged.

Validation:
- Ruff format/check and strict mypy clean.
- Full local suite: 1049 passed with one deselected test that fails identically
  on unmodified HEAD under local PySide6 6.11.1
  (`test_gui_update_preferences.py::test_manual_help_action_still_checks_when_
  automatic_checks_are_disabled`). /tmp/fedleave-142-check-green.log
- Focused analytics/chart tests: 116 passed. Added partial-year, empty-data,
  view-switch, unchanged-year-height and renderer-size coverage.
- `python -m build` succeeded. /tmp/fedleave-142-package.log
- Visual review: /tmp/fedleave-142-monthly.png (1610x1240) and
  /tmp/fedleave-142-year.png (1610x323).

Publication verified:
- CI 42147013337 all jobs success.
- Distribution 42147013369 all jobs success: package metadata, Linux package,
  Windows installer, Ubuntu 22.04 and Debian 12 original-runtime checks, and
  Publish latest master installers.
- Verified BUILD.txt source/version, beta tag/release source, and all five
  downloaded release asset sizes/digests against the generated mirror:
  - Linux tar.gz 292049821 bytes sha256 c11437e327ebf0697d6f359757f1f62f6c0fdee78be4c53da356208881a6e43c
  - Windows exe 210495886 bytes sha256 0acf8f68572207a000a5260bf2e0bdd22409e352eec563a56810a588ba27b92e
  - install.sh 11624 bytes sha256 beb342233eefc20929f00784fbc3697aa57df35dd98cd688bfea5debd9d5d008
- README public download URLs unchanged. No manual generated-installer edits.

Next action: no open GitHub issues remain at completion. Do not start new work
without a new user request.
