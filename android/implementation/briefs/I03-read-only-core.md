# I03 · Read-only core boundary

**Handoff draft.** Bind the actual I02 bridge path, package names and toolchain after the bridge decision passes. This milestone extracts the Calendar, dated balance, day, pay-period and month reads behind one typed Python facade and keeps every desktop adapter on the same result. [Core contracts](../../specification/chapters/04-architecture/04-01-core-and-compatibility.md#responsibilities-and-data-flow) · [Parity fixtures](../../specification/chapters/04-architecture/04-01-core-and-compatibility.md#parity-fixtures)

## Assignment

**Risk and ownership.** R2 integration. Require an approved design admission and the delegated manifest before assignment. Keep desktop compatibility and the write boundary under design-owner review. [Handoff gate](../../specification/chapters/07-organization/07-03-design-and-implementation-handoffs.md)

**Starting state.** I02's passed commit and bridge report; the approved specification checkpoint; the [Calendar fixture manifest](../../qa/fixtures/calendar-2026.manifest.json) with tree digest `03e9e1757f73015b356b900703a13a88390e854f274c7bce78bd2f9f00c7aea9`. Record the exact revisions at assignment.

**Read in this order.**

1. [Shared core and compatibility](../../specification/chapters/04-architecture/04-01-core-and-compatibility.md), especially responsibilities, the read/write boundary and parity fixtures.
2. The [device storage write boundary](../../specification/chapters/04-architecture/04-02-device-storage.md#write-and-recovery-model) so the facade never owns storage.
3. `calculate_balances`, `calculate_daily_activity`, `calculate_pay_period_activity` and `ensure_automatic_accruals` in [`src/fedleave/ledger.py`](../../../src/fedleave/ledger.py).
4. The read commands and private helpers in [`src/fedleave/commands/balances.py`](../../../src/fedleave/commands/balances.py), [`src/fedleave/cli_helpers.py`](../../../src/fedleave/cli_helpers.py), [`src/fedleave/config.py`](../../../src/fedleave/config.py) and [`src/fedleave/payperiods.py`](../../../src/fedleave/payperiods.py).
5. The existing reference tests [`tests/test_month_command.py`](../../../tests/test_month_command.py), [`tests/test_daily_balance.py`](../../../tests/test_daily_balance.py), [`tests/test_accruals_pay_period.py`](../../../tests/test_accruals_pay_period.py) and [`tests/test_compare_leave_balances.py`](../../../tests/test_compare_leave_balances.py).
6. The golden files under `android/qa/golden/calendar-2026/`.

**Expected changes.** Add `src/fedleave/read_api.py` as the versioned read facade. Refactor the existing read commands to call it while preserving presentation. Update the I02 bridge adapter to call it. Add `tests/test_read_api.py` and Android bridge contract tests. Add `src/fedleave/read_api.py` to the strict mypy file list if the module is clean under strict mode; record the decision.

**Preserve.** Existing CLI command names, options, text output, JSON field names and values, exit codes and GUI callers. Existing desktop tests and fixtures. The canonical JSON schema. No new runtime dependency. Do not make the facade read paths, open files, lock, publish or mutate the datastore.

## Behavior and contract

Every function receives a complete read-only snapshot and an explicit local date. The snapshot is a mapping of canonical relative filename to parsed JSON:

```python
files = {
    "config.json": {...},
    "leave_years/2026.json": {...},
    "holiday_cache/federal_holidays_2026.json": {...},
}
local_date = "2026-01-16"
```

Expose these typed functions and one bridge dispatcher:

```python
SCHEMA_VERSION = 1

def dated_balance(files, request, *, local_date) -> dict
def pay_period(files, request, *, local_date) -> dict
def day_activity(files, request, *, local_date) -> dict
def month_view(files, request, *, local_date) -> dict
def read(files, request, *, local_date) -> dict
```

`read` takes `request = {"operation": "...", ...}` and returns `{"schema_version": 1, "ok": true, "result": {...}}` or `{"schema_version": 1, "ok": false, "error": {"code": "...", "field": "...", "message": "..."}}`. The typed functions return `result` directly and raise `ReadApiError`.

| Operation | Request keys | Result |
| --- | --- | --- |
| `dated_balance` | `as_of` optional ISO date; omission means `local_date` | JSON matching [`balance-<date>.json`](../../qa/golden/calendar-2026/balance-2026-01-16.json) |
| `pay_period` | `date` required ISO date | JSON matching [`pay-period-2026-01-24.json`](../../qa/golden/calendar-2026/pay-period-2026-01-24.json) |
| `day_activity` | `date` required ISO date | JSON matching [`activity-2026-01-16.json`](../../qa/golden/calendar-2026/activity-2026-01-16.json) |
| `month_view` | `year` and `month` required; `calendar_year` optional | JSON matching [`month-2026-01.json`](../../qa/golden/calendar-2026/month-2026-01.json) |

Rules:

- Resolve the leave year from the requested date using the existing desktop rule. The January 4, 2027 request resolves to the 2026 ledger.
- The month result's `today`, `balance_as_of_today`, `projected_balance` and `is_today` fields use the supplied `local_date`, never the machine clock.
- `dated_balance` without `as_of` returns the `local_date` balance. Explicit dates preserve the current projected and history behavior.
- Category keys, warning codes and enumeration values stay identical. Finite hours use the desktop values exactly. Preserve `None` and unavailable meanings.
- The functions are pure with respect to their inputs. Do not mutate `files`, its nested mappings or their lists. The underlying core may synthesize or deep-copy internally; expose only computed output.
- `config.json` is required for current desktop parity and supplies carryover policy and category rules. A missing or invalid config file raises `MISSING_CONFIG`, not a silent default.
- Unknown operation or field values raise structured errors. Define `ReadApiError` with `code`, `field` and a safe `message`, and use at least `MISSING_CONFIG`, `LEAVE_YEAR_NOT_FOUND`, `INVALID_DATE`, `INVALID_MONTH`, `INVALID_SNAPSHOT` and `UNKNOWN_OPERATION`. Do not change the CLI's existing messages or exit codes; adapt them in the command layer.

Concrete checks:

- January 16, 2026 returns annual 90.0 h and sick 20.0 h, with every other category exactly as the golden file.
- January 13 returns annual 92.0 h and sick 20.0 h; January 22 returns 86.0/20.0; January 24 returns 92.0/24.0; January 25 returns 89.0/24.0.
- January 4, 2027 returns year 2026, annual 233.0 h and sick 120.0 h.
- Period 1 on January 24 returns January 11–24, payday January 30, annual 6.0 h earned and 14.0 h used, and closing balances 92.0 h annual and 24.0 h sick.
- January 16 day activity reports 2.0 h annual used and 2.0 physical absence hours.
- The January 2026 month view reports `today` January 16, only January 16 marked `is_today`, Today balances 90.0/20.0, and the period-end and payday dates in the golden file.

The Android bridge dispatcher returns the `read` wrapper unchanged. This becomes the versioned contract I04 passes a snapshot to and I05 consumes through typed Kotlin state.

## Screen and resource checklist

No new product screen. The I02 debug probe stays debug-only and switches to the new dispatcher. Record its visible result is unchanged. I05 owns the product screens.

## Verification

Use the repository virtual environment from the repository root:

```bash
QT_QPA_PLATFORM=offscreen .venv/bin/python -m pytest -q
QT_QPA_PLATFORM=offscreen .venv/bin/python -m pytest -q tests/test_month_command.py tests/test_daily_balance.py tests/test_accruals_pay_period.py tests/test_compare_leave_balances.py tests/test_read_api.py
make quality
```

Run the Android JVM and instrumentation tasks from `android/` using the I01/I02 task names. Rerun the I02 probe and record its parity and timing.

| Check | Expected evidence |
| --- | --- |
| Golden parity | Every manifest case is semantically equal to its golden JSON after excluding no business fields. Compare with `json.loads`; do not compare formatted text. |
| Desktop parity | `fedleave balance`, `pay-period`, `activity` and `month --json` results match the new facade for the same snapshot and local date. Existing command tests pass unchanged. |
| Read-only | Hash the snapshot's file bytes before and after every case; the tree digest stays `03e9e1757f73015b356b900703a13a88390e854f274c7bce78bd2f9f00c7aea9`. |
| Purity | Reuse the same Python objects for two calls and confirm no input mutation. A late or repeated call returns the same result. |
| Errors | Missing config, missing year, invalid date, invalid month, malformed snapshot and unknown operation each return the documented code without writing or leaking paths. |
| Regression | The full desktop suite and `make quality` pass. If strict mypy cannot cover the new module cleanly, record the specific errors and leave typing coverage unchanged rather than adding ignores. |

Save the case table and commands in `android/qa/reports/I03.md`, the independent review in `android/reviews/code/I03.md`, and resume notes in `android/implementation/passdowns/I03.md`. Record the exact source revisions, fixture digest and bridge identity. [QA gates](../../specification/chapters/05-quality/05-02-quality-gates.md#required-gates)

## Handoff

Deliver the versioned facade, the exact snapshot/request/result/error contract, the desktop comparison evidence and the updated debug bridge. I04 consumes the snapshot shape and never bypasses the facade for domain reads. I05 builds typed Kotlin state on the bridge operations. Record any deviation from a golden file as a specification escalation before accepting it.
