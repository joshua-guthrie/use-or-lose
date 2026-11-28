# Specification: Remove pydantic completely and replace it with portable pure Python

**Status:** for owner review. **Author:** implementation worker. **Date:** November 27, 2026.
**Location of changes:** the repository root (`src/`, `pyproject.toml`, tests), NOT the Android tree — except one fixture-builder line and the later Android bridge cleanup described in Phase 6.

## 1. Purpose

Remove the `pydantic` dependency (and its compiled Rust engine `pydantic-core`) from the entire project, replacing every pydantic model with pure-Python standard-library code that produces **identical behavior**. After the change:

- The CLI behaves exactly as before: same commands, same JSON output, same errors and exit codes.
- The two desktop GUIs require **no changes** (they consume the CLI; the CLI is unchanged).
- Every Python module under `src/useorlose/` is importable inside the Android app (pure Python 3.13, stdlib only) — one source of truth for the accounting core on Linux, Windows and Android.
- `pydantic` no longer appears in `pyproject.toml`, in any `src/` import, or in a fresh environment's installed packages.

**Hard requirement:** no observable behavior change. The existing 1,000+ test suite is the proof and must pass **without modification**, except the new pure-model unit tests added by this change.

## 2. Non-goals

- Do not rename classes, modules or functions (public names stay: `Transaction`, `Config`, `UserConfig`, `DefaultsConfig`, `HolidaysConfig`, `*Rules`, `LeaveYear`, `Policy`).
- Do not change any CLI command, flag, message, JSON key, or file format.
- Do not restructure the GUI subprocess design; do not touch `useorlose_gui` or `useorlose_analytics` beyond what the inventory requires (nothing).
- Do not change accounting semantics in any way.

## 3. Current pydantic surface (complete inventory)

Verified November 27, 2026 by grep across the repository.

| # | File | Pydantic usage | Replacement notes |
| --- | --- | --- | --- |
| 1 | `src/useorlose/transaction_model.py` | `Transaction(BaseModel)`, `ConfigDict(extra="allow")`, 2 model validators, 5 field validators, `Field(default_factory=…)` timestamps | The only model Android's write path needs. Keep the module name and the `Transaction` class name. |
| 2 | `src/useorlose/ledger.py` | Lazy `from pydantic import ValidationError` inside `create_transaction` (line ~87); `add_transaction_to_leave_year` calls `transaction.model_dump()` | Rewire to the pure `Transaction`; same reformatting of errors. |
| 3 | `src/useorlose/config.py` | 12 `BaseModel` classes with defaults only (no validators): `UserConfig`, `DefaultsConfig`, `HolidaysConfig`, `AnnualRules`, `SickRules`, `CompRules`, `TravelCompRules`, `CreditRules`, `TimeOffAwardRules`, `RestoredAnnualRules`, `ReligiousCompRules`, `Config`, `LeaveYear` | Plain mutable containers. Attribute assignment (`config.user.display_name = …`) and recursive `model_dump()` must keep working. |
| 4 | `src/useorlose/mcp/policy.py` | `Policy(BaseModel)` with `ConfigDict(extra="forbid", frozen=True)`, `Literal` mode, `Path` fields, numeric bounds (`gt`, `le`, `ge`), one `model_validator` using `model_fields_set`, and `model_validate_json(…, strict=True)` | Frozen dataclass with `__post_init__` validation, a `fields_set` set, and the same `load()` classmethod contract. |
| 5 | `src/useorlose/wms_import.py` | Calls `LeaveYear(…).model_dump()` (two sites) | Unchanged if `LeaveYear` keeps its API. |
| 6 | `src/useorlose/planning.py` | `Config().model_dump()["rules"]` | Unchanged if `Config` keeps its API. |
| 7 | `src/useorlose/commands/transactions.py`, `commands/forced_balance.py` | `create_transaction(…)` then `.id` / `.model_dump()` | Unchanged if `create_transaction` keeps its return API. |
| 8 | `pyproject.toml` | `"pydantic>=2,<3"` dependency | Remove the line. |
| 9 | `android/qa/fixtures/build_calendar_fixture.py` | `version("pydantic")` in the manifest `generator_environment` | Remove that key (and tolerate older manifests missing it). |
| 10 | Docstrings in `src/useorlose/rule_defaults.py`, `transaction_model.py`, and prose in `android/README.md`, spec chapters that mention pydantic | Text only | Update wording to “pure standard library”; the absence gate below greps imports, not prose. |

Confirmed: **no test file imports pydantic**; no `.github`/`scripts`/`installer` file references it; the PyInstaller suite manifest has no pydantic hidden import.

## 4. Replacement design

### 4.1 One small toolkit (new module)

Create `src/useorlose/pure_models.py` — the only new module — providing:

```python
def model_dump(obj) -> dict            # recursive dump: nested models + dataclasses -> plain dicts
class PureModel:                       # tiny base: __init__ via dataclass fields, fields_set tracking, model_dump()
```

All rules below are exact contracts the implementer must honor.

### 4.2 `Transaction` (in `transaction_model.py` — same module, same class name)

Implement as a `@dataclass` (frozen or not — pydantic models are mutable; keep it mutable to match) with:

- The **same 15 declared fields and default values**, in the same declaration order:
  `id, date, category, direction, hours, description="", status="planned", source="manual", created_at=now-iso, updated_at=now-iso, expiration_date=None, expiration_pay_period=None, earned_transaction_id=None, expiration_extension_reason=None, expiration_extended_at=None`
  (`now-iso` = `datetime.now().isoformat()`, evaluated per instance, as today).
- **`extra="allow"` semantics:** accept unknown keyword arguments and preserve them in `model_dump()` output (pydantic keeps extras in the dumped dict). This must be verified against the current behavior with a test that constructs `Transaction` with an extra field.
- **Validation, in this exact order, with these exact messages** (copy from the current validators):

| Check | Condition | Exact message |
| --- | --- | --- |
| category | `value not in TRANSACTION_CATEGORIES` | `Invalid category: {value}. Valid categories: {', '.join(TRANSACTION_CATEGORIES)}.` |
| direction | `value not in TRANSACTION_DIRECTIONS` | `Invalid direction: {value}. Valid directions: {', '.join(TRANSACTION_DIRECTIONS)}.` |
| status | `value not in TRANSACTION_STATUSES` | `Invalid status: {value}. Valid statuses: {', '.join(TRANSACTION_STATUSES)}.` |
| hours | not finite | `Invalid hours: must be a finite number` |
| hours | `< 0` | `Invalid hours: must be zero or positive. Example: --used 4.0` |
| date | unparseable | `Invalid date: {value}. Use YYYY-MM-DD, for example 2026-01-11.` |
| parental | `category == "fmla"` and `direction != "used"` | `FMLA_USAGE_ONLY: FMLA has used transactions only` |
| parental | `category == "ppl"` and direction not in {earned, used}, unless `direction == "expired" and source == "expiration-engine"` | `INVALID_PPL_DIRECTION: PPL accepts earned and used transactions only` |

- The **date normalizer**: use `ledger._parse_iso_date(value).isoformat()` exactly as today (it canonicalizes `2026-1-5` → `2026-01-05`).
- The model raises **`ValueError`** with the single message above. (Today pydantic may aggregate several errors and `create_transaction` joins them with `; `; the current CLI behavior for a single error is one message. The multi-error aggregation is preserved as: raise the first failure message. Note this explicitly in the test plan.)
- `model_dump()` returns a dict of the 15 declared fields **plus any extras**, in declaration order then extras, with the same types.

### 4.3 `ledger.create_transaction` and `add_transaction_to_leave_year`

- Remove the lazy pydantic import. `create_transaction` builds the pure `Transaction`, catching `ValueError` and re-raising it **unchanged** (today it re-raises `ValueError("; ".join(errors))`; with one message the output is identical).
- `add_transaction_to_leave_year(leave_year, transaction)` keeps calling `transaction.model_dump()`.
- Result: `ensure_automatic_accruals`, `cross_year.seed_accruals`, and every write path become pydantic-free automatically.

### 4.4 `config.py` models (12 classes + `LeaveYear`)

Convert each to a `@dataclass` with:

- Identical field names, defaults, and declaration order (copy them verbatim).
- `default_factory` lambdas reproduced exactly (including the `deepcopy(DEFAULT_RULES)` factory).
- Attribute assignment must keep working (`config.user.display_name = "User"` in `init_config`).
- A `model_dump()` method (from the toolkit) returning the same recursive dict pydantic produced — nested `UserConfig`/`DefaultsConfig`/… become plain dicts, lists and `deepcopy`ed rules unchanged. Byte-compare against the current `Config().model_dump()` in a test.
- No validators existed; none to port.

### 4.5 `mcp.policy.Policy`

Implement as a **frozen dataclass** with:

- Identical fields/defaults (mode literal, Path fields, tuple fields, `timeout_seconds`, `lock_timeout_seconds`, `max_output_bytes`).
- `__post_init__` checks:
  - `mode` ∈ {full_access, read_only, unattended, confirm_writes}, else `ValueError` (mirror pydantic's strict Literal message where tests depend on it; at minimum a clear ValueError).
  - `timeout_seconds`: reject `<= 0` or `> 3600` (bounds `gt=0, le=3600`).
  - `lock_timeout_seconds`: reject `< 0` or `> 3600` (`ge=0, le=3600`).
  - `max_output_bytes`: reject `< 1024` or `> 16*1024*1024` (`ge=1024, le=16*1024*1024`).
  - Coerce `data_dir`, `executable`, `audit_log` and the tuple path fields from `str` or `Path` via `Path()` (matching pydantic's JSON-string acceptance); `allowed_data_dirs/file_read_roots/file_write_roots` accept lists of str/Path and become tuples.
  - The `unambiguous_full_access` validator: track `fields_set` (the constructor keys actually provided) and raise the **exact** message `full_access is incompatible with restrictive fields: {', '.join(sorted(restrictive))}` for the same field set.
- `load(path)` classmethod: same logic — `json.loads`, `setdefault("mode", "read_only")` for dicts, then construct with `fields_set` = the provided keys (strict mode semantics: no silent type coercion beyond the Path rules above).
- `extra="forbid"` semantics: unknown keys → `ValueError`.
- Frozen: assignments after construction raise (dataclass `frozen=True`); confirmed no code mutates `Policy` post-construction.

### 4.6 Dependency and metadata removal

- `pyproject.toml`: delete `"pydantic>=2,<3"` from `[project].dependencies`.
- `android/qa/fixtures/build_calendar_fixture.py`: remove the `"pydantic": version("pydantic")` manifest key. Existing fixture manifests (which contain the old key) remain valid — the manifest reader must not require the key (it never did; verify).
- Update the “pydantic-free” docstrings/prose listed in §3 #10 so the tree reads consistently.

## 5. Compatibility contract (what “no behavior change” means, concretely)

1. Every existing test file passes unmodified.
2. `Config().model_dump()` before vs after: byte-identical JSON.
3. `Transaction(...)` validation messages: byte-identical for each invalid-input case in §4.2.
4. `create_transaction(...)` output rows: byte-identical for the fixture inputs (IDs, timestamps aside — timestamps use `datetime.now()` on both sides and are excluded from comparison).
5. CLI subcommand outputs (`set-day --json`, `add --json`, `correct --json`, `force-balance --json`, `init`, MCP `tools/list`, `Policy.load`) unchanged.
6. `Policy` error messages for the full_access-restrictive case unchanged.

## 6. Phases (implementation order — one commit per phase)

- **Phase 0 — Baseline.** Record the green suite: `make PYTHON=.venv/bin/python check` (expect 1063 passed, 3 skipped). Capture `pip freeze | grep pydantic` (expect `pydantic==2.13.x`, `pydantic-core==…`) as the before-evidence.
- **Phase 1 — Toolkit.** Add `src/useorlose/pure_models.py` + unit tests for `model_dump` recursion and `fields_set`.
- **Phase 2 — Transaction.** Rewrite `transaction_model.py` (pure `Transaction`), rewire `ledger.py`; add parameterized unit tests for every §4.2 case and the extra-field preservation. Run the full suite.
- **Phase 3 — Config.** Convert the config models; add the `Config().model_dump()` byte-identical test; run the full suite (this exercises `init`, fixtures, WMS import, planning).
- **Phase 4 — Policy.** Convert `Policy`; run the MCP suites (`tests/test_mcp_*.py`) plus the full suite.
- **Phase 5 — Remove the dependency.** Edit `pyproject.toml`, the Android fixture builder, prose. Recreate the venv cleanly (`.venv` rebuild or `pip uninstall pydantic` after `pip install -e .`) and re-run everything.
- **Phase 6 — Android write path joins the shared core.** In `android/app/build.gradle.kts`, add `transaction_model.py` (and any new pure module) to the bridge include list. Replace the row-building in `bridge/src/useorlose_creation.py` and `bridge/src/useorlose_editing.py` with calls to the now-pure `useorlose.ledger.create_transaction`; delete the duplicated row builders. Run the bridge parity gate (phone ledger byte-identical to the CLI on `calendar-2026` / `carryover-2026` / `editable-day-2026`) and the full Android suite (56 JVM + 87 device). The creation/editing mirror modules eventually reduce to thin request/response adapters only.

## 7. Verification plan

### 7.1 No behavior change

| Gate | Command / evidence | Pass criterion |
| --- | --- | --- |
| G1 Baseline freeze | `make PYTHON=.venv/bin/python check` | Same count and names as Phase 0 (1063/3), **no test file modified** by this work |
| G2 Validator messages | New `tests/test_pure_models.py` | Every §4.2 message reproduced exactly |
| G3 Config dump identity | New test in `tests/test_pure_models.py` | `Config().model_dump()` equals the recorded golden (captured from the pydantic build before Phase 3) |
| G4 CLI outputs | Existing `tests/test_set_day.py`, `test_transactions.py`, `test_forced_balance.py`, `test_mcp_policy.py`, `test_leave_year_creation_*.py` | Unchanged and green |
| G5 GUI untouched | `git diff --stat` limited to §3 files; no changes under `src/useorlose_gui/`, `src/useorlose_analytics/` | Zero diffs in GUI trees |
| G6 Installer smoke | Distribution workflow development build (dispatch without a release) or local `installer/package.py validate-bundle` | Builds and smoke tests green; `DEPENDENCIES.txt` in the bundle no longer lists pydantic |
| G7 Android parity | Bridge parity script + `./gradlew :app:connectedDebugAndroidTest` | Phone ledgers byte-identical; 87 device tests green |

### 7.2 pydantic completely removed

| Gate | Command | Pass criterion |
| --- | --- | --- |
| G8 Source | `grep -rn "import pydantic\|from pydantic" src/` | Zero matches |
| G9 Manifest | `grep -n pydantic pyproject.toml` | Zero matches |
| G10 Fresh environment | In a new venv: `pip install -e .[dev]` then `pip freeze \| grep -i pydantic` | Zero matches |
| G11 Android bundle | `find android/app/build -name "*.py" \| xargs grep -l pydantic` | Zero matches |
| G12 Tests | `grep -rn pydantic tests/` | Zero matches |

### 7.3 Cross-platform

- CI (`ci.yml`) runs the suite on Linux and Windows — both must stay green.
- Android connected suite on the `fedleave_test` AVD must stay green (Phase 6).
- The Distribution workflow development build validates the Windows and Linux packages (optional but recommended once).

## 8. Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| A validation message drifts | §4.2 table is normative; G2 pins every message |
| Extra-field dump behavior differs | Explicit `extra="allow"` contract in §4.2 + dedicated test |
| Pydantic strict-mode nuance in `Policy.load` | Path/str coercion and unknown-key rejection specified in §4.5; MCP suite (G4) pins behavior |
| `fields_set` semantics wrong | Specified as “the constructor keys actually provided”; G4 covers the full_access case |
| Frozen `Policy` breaks a hidden mutation | G4 MCP tests exercise `authorize`/`child_environment`; inventory found no mutations |
| Multi-error aggregation difference | Single-message raise specified; G4 confirms no test depends on aggregated output |
| `datetime.now()` timestamps differ | Excluded from comparisons everywhere (existing convention) |
| Android mirrors left behind | Phase 6 removes them; G7 re-verifies parity |

## 9. Diagram

```mermaid
flowchart LR
    subgraph Before
        CLI[CLI shell] --> PYD[pydantic Transaction/Config/Policy]
        PYD --> CORE[Accounting core .py]
        AND1[Android app] --> MIRROR[Mirror modules (duplicated rows)]
        MIRROR --> CORE
    end
    subgraph After
        CLI2[CLI shell] --> PURE[Pure models: transaction_model.py, config.py, policy.py]
        PURE --> CORE2[Accounting core .py]
        AND2[Android app] --> BRIDGE[Chaquopy adapter]
        BRIDGE --> PURE
        GUIs[Desktop GUIs] --> CLI2
    end
```

## 10. Deliverables checklist

- [ ] `src/useorlose/pure_models.py` (+ tests)
- [ ] Pure `transaction_model.py`, reworked `ledger.py` (+ validator tests)
- [ ] Pure `config.py` models (+ dump-identity golden test)
- [ ] Pure `mcp/policy.py` (+ existing MCP suites green)
- [ ] `pyproject.toml` pydantic removed; venv reinstalled; G8–G12 green
- [ ] Android bridge include list + mirror cleanup; G7 green
- [ ] Phase evidence recorded in `untracked/` (baseline counts, before/after `pip freeze`, diff stat)
