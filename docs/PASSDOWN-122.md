# Issue #122 passdown

Issue #122 is complete, committed, pushed, published and closed.

Final evidence:
- Detailed closeout: https://github.com/joshua-guthrie/fedleave/issues/122#issuecomment-6512587836
- Source: 1a6a42613f8e7cb9b74383edaf9baf9d7f632c21
- Generated installer mirror: de1b9d2cca1b9d419fe906221c85ff8d57662a97
- CI: https://github.com/joshua-guthrie/fedleave/actions/runs/40983640027
  Linux 975 passed; Windows 968 passed, 7 platform-specific skips; quality/package
  job passed.
- Distribution: https://github.com/joshua-guthrie/fedleave/actions/runs/40983640029
  Both platform packages, installed smoke/data-preservation tests, Debian 12 and
  Ubuntu 22.04 runtime checks, beta publication and installer mirrors passed.
- Verified beta source/tag, BUILD.txt and all five published asset sizes/digests
  against repository mirrors using /tmp/fedleave-122-verify-publication.py.
- Version: 0.2.2.dev0+g1a6a4261. Public download URLs unchanged.
- Local master was fast-forwarded to the generated mirror commit.
- Final live five-hour quota: 47% remaining (53% used), reset
  2026-10-30T04:42:38Z. This does not satisfy the earlier OVER 60% condition for
  automatically starting another issue. Stop after this completed issue.
- No required work remains for #122. Earlier remaining-work checklist below is
  historical and has been completed; obtain fresh authorization for another issue.

- Repository: /home/jlguthri/bin/fedleave; branch master.
- Issue: https://github.com/joshua-guthrie/fedleave/issues/122
- Source commit: 1a6a42613f8e7cb9b74383edaf9baf9d7f632c21
- User authorized a detailed commit, push and detailed issue closeout. Every
  source push must finish the Distribution workflow, both platform packages,
  stable beta assets and matching generated installer mirrors before completion.
- Public README installer URLs are unchanged. Never hand-edit installers/.
- Live five-hour quota after push: 60% remaining, reset 2026-10-30T04:42:38Z.
  This is historical; recheck `codex-usage --json` with escalation. User's stop
  condition is near 15% remaining: find a safe checkpoint, update passdown, stop.
  Earlier authorization to start another issue required OVER 60% remaining after
  completion; the last live value was exactly 60%, so do not automatically start
  another issue from that reading.

## Implemented

Calendar Analysis > Planning > Leave Budget opens the existing Analytics
companion with --view budget. Analytics has a third Planning tab. No new
executable or budget CLI command. Purpose eligibility defaults to none.

Pure fedleave.budget contract (schema 1, deadline-budget-v1) receives only data
and defensive copies. Annual excess uses shared year-end pre-cap accounting
after scheduled use/accruals/disposals. Canonical lot allocations are audited
exactly once; explicit links, FIFO, inclusive deadlines, future-earning releases,
extensions, disabled expiry, unknown openings, past dispositions and later
outside-horizon deadlines remain distinct. Unsupported credit/payout policies
produce specific unknown limitations without disabling supported categories.

Four deterministic EDD strategies: Even (default), Earliest, Latest and
Capacity-weighted. Stable ties; capped largest-remainder period quotas with
redistribution; configured increments; real pay periods/current partial period;
shared actual work/holiday capacity; dated balances and reserves. Suggestions
cannot spend an unfunded proposed date or increase later baseline deficits.
Cumulative checks expose both actual feasible placement and a dated maximum-flow
capacity upper bound. Shared reserve/cap interactions are validated on the actual
schedule; a chosen-strategy shortfall is not called a proof against all schedules.
After-risk is recalculated canonically. Funding audit identifies failing dates,
candidate balances and reserve thresholds. Partial suggestions are retained.

Credit cap replay is explicitly opt-in via schema-1 scenario assumption
budget_credit_caps. It replays future actual PP end caps once, posts simulated
loss on the next day, subtracts known next-day disposals and carries earlier
use/loss into later cap calculations. Normal CLI/What-if defaults are unchanged.
Transfer preserves the assumption and reproduces budget balances on Replace.

Exact deadline/credit/date/period/capacity/cumulative/balance/audit/warning tables
and CSV consume the same result. Double-click a sorted row for exact details.
Existing chart popup supports CSV/PNG/PDF/Print. Revision-bound workers discard
late results. Source/strategy changes use cached snapshots; Refresh reloads.
Strategy preference is outside the ledger and merges other open-view keys.
Transfer checks a fresh complete source identity and uses explicit Replace,
Merge or Cancel for existing What-if drafts. Failed merge leaves drafts intact.

A new text-sort drilldown regression exposed a shared Qt native recursion in
SortableTableItem.__lt__. Fixed fallback using direct text comparison rather
than reentering Qt's virtual operator. Verified on both local runtimes.

## Validation

- make check PYTHON=.build/issue120-debug/venv/bin/python: 975 passed, 188.79 s;
  formatting, lint and configured strict mypy checks passed.
  /tmp/fedleave-122-verified-check.log
- Python 3.13 / Qt 6.11.1 focused budget/Planning GUI suite: 60 passed, 46.37 s.
  /tmp/fedleave-122-verified-qt313.log
- Last preference malformed-schema guards additionally verified with the shared
  preference test and Forecast model tests: 9 passed; lint/format clean.
- make package: source and wheel succeeded; all five new core/GUI modules are
  present in the wheel. /tmp/fedleave-122-verified-package.log
- Real source Analytics smoke opened What-if, Forecast and Leave Budget against
  the exact selected backend; all canonical ledger file hashes unchanged.
  /tmp/fedleave-122-source-smoke.py
- Dedicated end-to-end tests cover legacy/read-only ledger files, all strategies,
  export, fresh-source transfer, missing stores, cancellation and failure paths.
- Screen, chart PNG and rendered PDF visually inspected:
  /tmp/fedleave-122-ui.png, /tmp/fedleave-122-chart.png,
  /tmp/fedleave-122-chart.pdf, /tmp/fedleave-122-pdf.png

## Publication checklist (completed; retained as procedure)

1. CI https://github.com/joshua-guthrie/fedleave/actions/runs/40983640027 has passed on Windows/Linux; Linux 975 passed. Distribution https://github.com/joshua-guthrie/fedleave/actions/runs/40983640029 is creating installers/running smoke checks after both bundle builds passed.
2. Wait for both platform builds, installed smoke tests and beta publication.
3. Fix any real failure, commit/push a follow-up and repeat all publication checks.
4. Fetch master and fast-forward the bot installer mirror commit. Verify
   installers/BUILD.txt source_commit, beta tag/release body, and all five asset
   sizes/digests against LFS pointer/checksum or plain mirror bytes.
   Adapt /tmp/fedleave-121-verify-publication.py to the final source SHA.
5. Add a detailed issue comment with source, behavior, limitations, validation,
   CI/Distribution URLs and installer verification; then close #122 as completed.
6. Update this passdown with final evidence. Do not start the next issue unless a
   fresh usage reading satisfies the earlier over-60% condition.

The first push attempt had a temporary GitHub DNS failure; retry succeeded.
Temporary sandbox-only full check attempts also failed/hung on existing network
integration tests. Superseded leftover processes were stopped; use the clean
verified logs above. No source workaround was made for environmental failures.

Older docs/PASSDOWN-120.md and docs/PASSDOWN-121.md are untracked local history;
this passdown is also local. Do not accidentally include them in source commits.
