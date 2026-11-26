# Issue #146 passdown

COMPLETE: #146 is committed, pushed, published, verified and closed.

Issue: https://github.com/joshua-guthrie/fedleave/issues/146
Title: Holiday Planner Chart
Source commit: f6d40fa016bd30b83273073d606834bd6bad0d9f.
Installer mirror commit: d241b60 (local master fast-forwarded).
Published version: 0.2.2.dev0+gf6d40fa0.
CI: https://github.com/joshua-guthrie/fedleave/actions/runs/42429403067
Distribution: https://github.com/joshua-guthrie/fedleave/actions/runs/42429403179
GitHub auto-closed #146 from the `Fixes #146` trailer; detailed closeout
comment posted at https://github.com/joshua-guthrie/fedleave/issues/146#issuecomment-6715147148

Final state: master == origin/master == d241b60. Only local untracked
PASSDOWN-120/121/122/123/124/125/133/137/141/142/143/145 files remain outside
history; they were intentionally never staged.

Implemented:
- Rewrote `holiday_charts.render_holiday_option` as two full-leave-year
  calendar heatmaps: planned future leave (existing Scheduled after cutoff,
  blue) and suggested leave (selected option tentative hours, green).
- Shared intensity scale across both panels; observed nonworking holidays
  from the snapshot calculation context get a gold fill/border in both.
- Whole leave year rendered from the snapshot (partial first/last months
  handled); no planning result or ledger data changed.
- Chart caption keeps interval, metrics, assumptions, holiday coverage and
  the not-booked warning. Open Chart still uses the option.days table and
  CSV/PNG/PDF/Print.
- `HolidayWidget._detail` now passes the snapshot to the renderer.
- README, Calendar help, bundled Help, changelog and GUI tests updated.

Validation:
- Ruff format/check and strict mypy clean.
- Full local suite: 1051 passed with one deselected test that fails identically
  on unmodified HEAD under local PySide6 6.11.1
  (`test_gui_update_preferences.py::test_manual_help_action_still_checks_when_
  automatic_checks_are_disabled`). /tmp/fedleave-146-check-green.log
- Focused Holiday Planner domain/GUI suites: 34 passed.
- `python -m build` succeeded. /tmp/fedleave-146-package.log
- Visual review: /tmp/fedleave-146-holiday.png (1610x1594).

Publication verified:
- CI 42429403067 all jobs success.
- Distribution 42429403179 all jobs success: package metadata, Linux package,
  Windows installer, Ubuntu 22.04 and Debian 12 original-runtime checks, and
  Publish latest master installers.
- Verified BUILD.txt source/version, beta tag/release source, and all five
  downloaded release asset sizes/digests against the generated mirror:
  - Linux tar.gz 292037204 bytes sha256 e1b1a26208013c450d1b009eb605615a01839af9ee087526411d4382943ba7f5
  - Windows exe 210509074 bytes sha256 8300ab9bfb919d4210821a0388d7bb926fd8bd5691d4058cdd309f249a47c7cb
  - install.sh 11624 bytes sha256 beb342233eefc20929f00784fbc3697aa57df35dd98cd688bfea5debd9d5d008
- README public download URLs unchanged. No manual generated-installer edits.

Next action: no open GitHub issues remain at completion. Do not start new work
without a new user request.
