# Issue #145 passdown

COMPLETE: #145 is committed, pushed, published, verified and closed.

Issue: https://github.com/joshua-guthrie/fedleave/issues/145
Title: two "open charts" that do the same thing on cumulative usage
Source commit: 232dcc34434bf17d834bd2c551e829c8fd34f0f3.
Installer mirror commit: 5d5d44d (local master fast-forwarded).
Published version: 0.2.2.dev0+g232dcc34.
CI: https://github.com/joshua-guthrie/fedleave/actions/runs/42428135945
Distribution: https://github.com/joshua-guthrie/fedleave/actions/runs/42428135987
GitHub auto-closed #145 from the `Fixes #145` trailer; detailed closeout
comment posted at https://github.com/joshua-guthrie/fedleave/issues/145#issuecomment-6714979910

Final state: master == origin/master == 5d5d44d. Only local untracked
PASSDOWN-120/121/122/123/124/125/133/137/141/142/143 files remain outside
history; they were intentionally never staged.

Implemented:
- Removed the duplicate Open Chart button from ComparisonWidget (the
  Cumulative Usage tab). The Analytics window-level Open Chart button remains
  and routes to the same chart with CSV/PNG/PDF/Print.
- Calculate, Cancel and Save Table as CSV remain in the tab.
- Added a regression test that the tab has no Open Chart button.
- Changelog updated. Public installer URLs unchanged.

Validation:
- Ruff format/check and strict mypy clean.
- Full local suite: 1050 passed with one deselected test that fails identically
  on unmodified HEAD under local PySide6 6.11.1
  (`test_gui_update_preferences.py::test_manual_help_action_still_checks_when_
  automatic_checks_are_disabled`). /tmp/fedleave-145-check-green.log
- Focused usage-comparison/analytics suites: 42 passed.
- `python -m build` succeeded. /tmp/fedleave-145-package.log

Publication verified:
- CI 42428135945 all jobs success.
- Distribution 42428135987 all jobs success: package metadata, Linux package,
  Windows installer, Ubuntu 22.04 and Debian 12 original-runtime checks, and
  Publish latest master installers.
- Verified BUILD.txt source/version, beta tag/release source, and all five
  downloaded release asset sizes/digests against the generated mirror:
  - Linux tar.gz 292054353 bytes sha256 5dc4c18dfda30146e5a23f4b3cdd7542136d7bdb54bab0ce7f9eb8d47db31e3b
  - Windows exe 210506231 bytes sha256 6e3c3e8cfb75c5548a3b8d134a52a0a591132f8ddeac992edbae85c8ca689b1a
  - install.sh 11624 bytes sha256 beb342233eefc20929f00784fbc3697aa57df35dd98cd688bfea5debd9d5d008
- README public download URLs unchanged. No manual generated-installer edits.

Next action: #146 (Holiday Planner Chart), the oldest remaining open issue.
