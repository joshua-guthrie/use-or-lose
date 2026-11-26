# Issue #143 passdown

COMPLETE: #143 is committed, pushed, published, verified and closed.

Issue: https://github.com/joshua-guthrie/fedleave/issues/143
Title: fine print
Source commit: d4f037cf76ce1d178abf29e1ba6834d1a05559fc.
Installer mirror commit: b5ed701 (local master fast-forwarded).
Published version: 0.2.2.dev0+gd4f037cf.
CI: https://github.com/joshua-guthrie/fedleave/actions/runs/42425835084
Distribution: https://github.com/joshua-guthrie/fedleave/actions/runs/42425834926
GitHub auto-closed #143 from the `Fixes #143` trailer; detailed closeout
comment posted at https://github.com/joshua-guthrie/fedleave/issues/143#issuecomment-6714668618

Final state: master == origin/master == b5ed701. Only local untracked
PASSDOWN-120/121/122/123/124/125/133/137/141/142 files remain outside history;
they were intentionally never staged.

Implemented:
- Removed the explanatory disclaimer drawn at the bottom of
  `render_cumulative_usage` in charts.py.
- Expanded the plot into the freed vertical space (`bottom = height - 60`).
  Chart size (1610x1000), curve, labels, exports and accounting are unchanged.
- The same definitions remain in the on-screen explanation above the controls,
  offline Help, CSV metadata and documentation.
- Changelog updated. Public installer URLs unchanged.

Validation:
- Ruff format/check and strict mypy clean.
- Full local suite: 1049 passed with one deselected test that fails identically
  on unmodified HEAD under local PySide6 6.11.1
  (`test_gui_update_preferences.py::test_manual_help_action_still_checks_when_
  automatic_checks_are_disabled`). /tmp/fedleave-143-check-green.log
- Focused usage-comparison/analytics suites: 41 passed.
- `python -m build` succeeded. /tmp/fedleave-143-package.log
- Visual review: /tmp/fedleave-143-before.png and /tmp/fedleave-143-after.png.

Publication verified:
- CI 42425835084 all jobs success.
- Distribution 42425834926 all jobs success: package metadata, Linux package,
  Windows installer, Ubuntu 22.04 and Debian 12 original-runtime checks, and
  Publish latest master installers.
- Verified BUILD.txt source/version, beta tag/release source, and all five
  downloaded release asset sizes/digests against the generated mirror:
  - Linux tar.gz 292053747 bytes sha256 5aca9ee3dde864f5962105dbe4512e81765ae0f3e66f8d84c096d857371140d1
  - Windows exe 210504814 bytes sha256 792abe0cd6479f5f6b6451f8bcddc2b28c2b881c8437a9c9b8ef14ae7bada538
  - install.sh 11624 bytes sha256 beb342233eefc20929f00784fbc3697aa57df35dd98cd688bfea5debd9d5d008
- README public download URLs unchanged. No manual generated-installer edits.

Next action: oldest remaining open issue is #145, then #146. Do not start
another issue without a new user request.
