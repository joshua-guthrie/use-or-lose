# Issue #137 passdown

Issue #137 is complete, committed, pushed, published and closed.

FINAL EVIDENCE / STOP FOR THE NIGHT:
- Source: 17fe0fa32fc9ea82ba64ed83278982df38591200
- Installer mirror: bb5a653fb2e2548836fb6fc380fae3fd965d0ea3
- CI and Distribution links below both succeeded. Linux CI983 passed; Windows976
  passed,7 platform-specific skips. Both installed platform packages, data
  preservation, Debian12 and Ubuntu22.04 runtime checks passed.
- Verified BUILD.txt, beta tag/release source and all5 asset sizes/digests against
  generated repository mirrors/checksums. Version0.2.2.dev0+g17fe0fa3.
- Detailed closeout posted and issue closed as completed.
- Local master fast-forwarded to bb5a653. Only local passdown files are untracked.
- FINAL LIVE QUOTA: ok=true, allowed=false, limit_reached=true; primary0% remaining,
  used100%, reset2026-10-31T01:07:47Z. Secondary69% remaining. This changed sharply
  from the earlier reading; never infer availability from previous percentages.
- STOP. Do not start another issue tonight. On explicit resume, recheck live quota.
- No required work remains for #137. The old checklist below is historical and
  completed. Public installer URLs are unchanged.

- Issue: https://github.com/joshua-guthrie/fedleave/issues/137
- Source: 17fe0fa32fc9ea82ba64ed83278982df38591200
- CI: https://github.com/joshua-guthrie/fedleave/actions/runs/41007228771
- Distribution: https://github.com/joshua-guthrie/fedleave/actions/runs/41007228620
- Branch master. Existing untracked PASSDOWN-120/121/122 files are local history;
  do not stage them or this passdown accidentally.

User authorized detailed commit, push, detailed issue comment and closure.
Stop near 15% remaining in the LIVE five-hour quota after a safe checkpoint.
Use codex-usage --json with escalation, never infer availability from old values.
Latest reading before commit: 82% remaining, reset 2026-10-30T09:42:44Z. Earlier
in this task the previous quota window had 44%; it reset during implementation.

Implemented:
- Date cell single-click or Space/Enter opens a calendar bounded strictly after
  cutoff through inclusive horizon. Cancel preserves text; Remove has no date
  editing; F2 ISO text input remains supported.
- Replace/Remove buttons open a checkbox booking chooser, without requiring a
  background row selection. Existing selections are prechecked. Stable IDs
  survive sorting; already-targeted originals are unavailable. Multiple bookings
  combine into one operation with their total hours and first date/category.
- Undo selected changes and Reset all changes clearly distinguish draft undo
  from omitting an original booking. Inline explanation and tooltips reinforce it.
- Contextual Analytics File > Help / F1 provides detailed offline instructions
  for What-if, Forecast and Budget, examples, action differences and troubleshooting.
  Self-contained help.html is Analytics package data in source/wheel/frozen apps.
- Installed Analytics smoke asserts the real bundled help is present and readable.
- Tooltips cover all Planning action buttons and main Analytics controls.
- README, Calendar help, architecture and changelog updated. No public installer
  URL changes. No calculation/ledger-writer changes.

Validation:
- make check with .build/issue120-debug/venv/bin/python (Python 3.11, Qt6.11.3):
  983 passed in 188.74s; lint/format/configured strict mypy passed.
  /tmp/fedleave-137-check.log
- Python3.13/Qt6.11.1 Planning GUI suite:39 passed,38.92s.
  /tmp/fedleave-137-qt313.log
- Final Python3.11 focused interaction/planner lifecycle:19 passed,12.68s.
  /tmp/fedleave-137-final-focused.log
- make package succeeded. Wheel contains full help.html; PyInstaller collect-all
  includes the same resource. /tmp/fedleave-137-package.log
- Real selected-backend Analytics smoke passed all Planning views and loaded
  bundled help, with canonical file hashes unchanged.
- Existing read-only ledger lifecycle now exercises the new dialogs.
- Visual review: /tmp/fedleave-137-planner.png, -calendar.png, -chooser.png,
  -help.png; script /tmp/fedleave-137-visual.py.

Publication checklist (completed; historical procedure):
1. Monitor CI/Distribution above. Both platform packages, installed smoke checks,
   older Linux runtimes, stable beta assets and installer mirror must pass.
2. Fix any actual failure; never bypass packaging or manually edit installers/.
3. Fetch/fast-forward the generated installer mirror commit.
4. Run /tmp/fedleave-137-verify-publication.py (expected source17fe0fa...):
   checks BUILD.txt, beta tag/release source, all5 asset digests/sizes and mirrors.
5. Post detailed issue closeout with behavior, tests, workflow and mirror evidence;
   then close #137 as completed. Update this passdown with final evidence.

One automatic permission review timed out on make check. The permitted one retry
succeeded; this was an approval-service timeout, not a code/test/safety failure.
