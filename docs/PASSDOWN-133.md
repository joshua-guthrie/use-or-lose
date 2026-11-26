# Issue #133 passdown

Complete, committed, pushed, published and closed. No required work remains.

Issue: https://github.com/joshua-guthrie/fedleave/issues/133

Source committed and pushed: 5ec3fb4c7321455b7bb8ff2e531d803d31533cef.
Generated installer mirror: 14578e1fa7ead1fdf5a69e5877dbea4d9f5bc651.
Version: 0.2.2.dev0+g5ec3fb4c.
Issue automatically closed via Fixes #133 in the detailed commit message.
Distribution: https://github.com/joshua-guthrie/fedleave/actions/runs/41257254809
CI: https://github.com/joshua-guthrie/fedleave/actions/runs/41257254889
Both workflows succeeded. Both platform packages, installed smoke tests, Ubuntu
22.04 and Debian 12 released-runtime checks, stable beta publication and the
generated installer mirror commit all passed.
Verified BUILD.txt, beta tag, release source/version, and all five release asset
digests/sizes against the generated repository mirrors/checksums with
`/tmp/fedleave-133-verify-publication.py 5ec3fb4c7321455b7bb8ff2e531d803d31533cef`.
Local master fast-forwarded to the generated installer mirror commit. Only local
passdown files remain untracked. Public download URLs are unchanged.

Final live quota: ok=true, allowed=true, primary 18% used / 82% remaining,
reset 2026-11-01T02:33:06Z; secondary 65% remaining. Task completed before the
requested usage checkpoint. Recheck live usage before starting any new work.

CI totals: Linux 990 passed (221.12s); Windows 983 passed, 7 skipped (298.84s).
CI log saved at /tmp/fedleave-133-ci.log.
Successful Distribution log: /tmp/fedleave-133-distribution.log.

Publication retry history (resolved):
Windows Distribution job passed, including installer compilation, installed
CLI/MCP, uninstall, user-data preservation, and artifact upload.
Linux provisioning stalled >14 minutes (previous successful build: 13 seconds).
Cancelled the stalled workflow attempt and reran only Linux job 124703066193
and its dependent checks using `gh run rerun --job 124703066193`; this preserves
the successful Windows result. Saved cancelled-job log at
/tmp/fedleave-133-linux-stalled.log: apt-get update hung downloading
jammy-updates/main from azure.archive.ubuntu.com, before application build.
Fresh Linux retry (job 124706410242) cleared provisioning and completed normally.
No source/workflow changes were needed for the retry.

User requested another issue, work to completion or a safe checkpoint at 20% of
the five-hour allowance, a passdown, and a detailed commit and push if complete.
Interpreted the checkpoint as 20% remaining, consistent with previous passdowns;
communicated that interpretation. Initial live quota was 99% remaining.
Use `codex-usage --json` with escalation for live quota; do not infer it.

Branch: master. Existing PASSDOWN-120/121/122/137 files are unrelated untracked
local history; leave them intact and do not stage them or this passdown.

Implemented:
- Persistent `GuiSettings.automatic_update_checks`, enabled by default for new
  users and existing settings files without the key.
- File > Preferences > Software updates > Check for updates automatically.
  Save takes effect immediately and survives restart; Cancel preserves settings.
- Both the startup callback and recurring timer honor the current preference,
  including a startup callback queued before the option was disabled.
- Disabled callbacks do not invoke the backend or update saved check timestamps.
  Help > Check for Updates remains available and keeps the option disabled.
- Re-enabling uses the existing at-most-daily throttle. The existing six-hour
  timer remains in place; callbacks are harmless while the preference is disabled.
- README, offline Calendar help and changelog updated. Public download URLs
  are unchanged. No ledger, CLI, Analytics or packaging-workflow changes.

Validation:
- `make check PYTHON=.build/issue120-debug/venv/bin/python`: 990 passed in
  235.29s; formatting, lint and configured strict mypy passed.
  Log: /tmp/fedleave-133-verified-check.log. Seven new regression cases.
- The first restricted-sandbox full run stalled/failed in existing MCP subprocess
  tests and was stopped. The first affected MCP test passed in 3.59s with
  escalation; the complete rerun with escalation passed all 990 tests. No
  source changes were needed. Partial original log: /tmp/fedleave-133-check.log.
- `make package PYTHON=.build/issue120-debug/venv/bin/python` passed after
  allowing PyPI access for isolated build dependencies;
  log: /tmp/fedleave-133-package.log.
- Verified final wheel includes preference, checkbox and automatic-check guard.
- Visually reviewed /tmp/fedleave-133-preferences.png: checkbox fits without
  clipping and existing controls remain visible.

All publication and verification work is complete. Do not start another issue
without a new user request. The detailed source commit documents behavior and
validation, and Fixes #133 closed the issue automatically; no separate issue
comment was posted.
