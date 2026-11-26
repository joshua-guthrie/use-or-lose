# Chapter 07.04 · Orchestrator execution

Implement a deterministic, local Python CLI separate from the Android app and the Use or Lose ledger CLI. The initial target is Linux using one task at a time. Record the supported Python and Git versions and the actual isolation backend in the pilot brief before implementation. Mock mode uses local fixtures and needs no external service or Android tooling. [Staged delivery](../06-delivery/06-03-orchestrator-pilot-milestones.md)

## Trusted settings and task manifest

The operator controls the check registry, provider endpoint and model, isolation backend, limits, and protected paths. Document versioned example settings and keep local settings and run data under ignored `.orchestrator/` or a configured private location. Pin the effective policy digest at prepare time. Configuration and approval records must be outside worker and verifier write access. Explicit task limits can tighten trusted limits. Changes to the effective policy require revalidation.

Use a versioned manifest and reject unknown fields, missing inputs, and unresolved placeholders. Pin the schema version and an implementation-compatible validator. JSON is sufficient for the pilot. The following fields are required unless the table provides an explicit reason to omit one.

| Fields | Content and validation |
| --- | --- |
| `schema_version`, `task_id`, `risk` | Exact schema version, a unique safe filename component for the task, and an admitted risk class. Each run also gets a separate unique run ID. |
| `base_commit`, `spec_commit`, `brief_ref` | Full Git object IDs that resolve as commits, plus the brief's pinned path, revision and digest. |
| `level1_refs`, `design_refs`, `design_exception` | Stable IDs or anchored paths in pinned revisions. Each design reference carries its revision and digest. Use a recorded rationale if an applicable risk class allows reuse without a distinct design. |
| `acceptance_cases`, `fixture_refs` | Acceptance IDs, pinned fixture paths and digests. A required case has a check or recorded manual verification method and exact expected result. |
| `allowed_write_paths`, `read_only_refs` | Exact repo-relative paths or bounded patterns with defined segment matching. Reject overlap with protected paths. Allowable reads refer to approved scoped excerpts. |
| `check_ids` | Ordered named checks from the operator registry. Each registry entry fixes its argument vector, repo-relative working directory, environment, dependency inputs, time and output limit, and verification phase. A task supplies IDs only. |
| `provider`, `limits` | An admitted adapter ID plus applicable caps for attempts, correction cycles, tokens, money and wall time. Disabled capabilities and missing prerequisites block affected checks or dispatch. |
| `package_hash`, `approval_ref` | SHA-256 of the canonical worker payload and a matching trusted readiness record. Exclude the digest and approval record from the payload's own hash. The approval resolves to the package and governing inputs. |

## Worktree, patches and check execution

1. Verify the starting commit and use a dedicated worktree and unique branch for the run. Preserve the primary checkout. Treat the worker response as data. In the pilot it can propose a unified text patch, return its implementation summary, or raise a design question. Use a bounded response and accept one operation at a time.
2. Validate the complete patch and resulting paths before changing the worktree. Restrict the pilot to modifications of existing regular files in explicit writable scope. Reject out-of-scope content, absolute paths, traversal, links, Git metadata, unexpected binaries, protected files, and unsupported modes. Later support for file creation or removal must be explicitly covered by the manifest and tested. Apply the admitted patch as one recoverable step. Stop on a stale baseline or mismatch.
3. Treat specs, approved designs, release files, CI, existing approved fixtures and expectations, and trusted tools, records and settings as protected inputs. Apply trusted overrides only in a separately approved future policy. Reject unrelated refactors and formatting changes during scope review.
4. Freeze the source before launching any build or test. Record full candidate commit, parent and Git tree IDs, along with the check policy, dependency and fixture identities. Verify Git content, including any permitted newly created source, without undeclared inputs. Run checks against a read-only source snapshot and write build products elsewhere. Refuse source mismatches or missing required evidence. Keep runner and QA metadata in separate storage.
5. Run every check that can execute candidate-controlled code in an enforced OS sandbox, including baseline and mock checks. Give it read-only source, registered fixture and toolchain inputs, and a private temporary directory for outputs. Use a sanitized environment, network disabled, no credentials, no inherited Git configuration or SSH agent, and no host service sockets. Mount only declared inputs and outputs. Resolve named checks and launch fixed argv directly from trusted code. Bound wall time and output, and clean up the process tree on exit or cancellation. Verify these controls before executing the check. A missing isolation capability yields `BLOCKED`. Fetch pinned dependencies separately in a trusted setup step and supply them as declared read-only inputs.
6. Register preflight, acceptance, and regression checks for the scope. Record actual commands, exit status, timing, candidate identity and bounded sanitized output. The runner distinguishes failure of a check from an unavailable prerequisite. Candidate-origin text cannot change run state or tool policy. Only the trusted process records verified results.

## State and recovery

| State or transition | Required condition |
| --- | --- |
| `DRAFT → DESIGN_PENDING → VALIDATED → READY` | Structural validation records pinned inputs and all applicable design and operator approvals. Missing decisions stay pending or blocked with a reason. |
| `READY → WORKTREE_CREATED → IMPLEMENTING → VERIFYING` | Prepare the worktree, admit the patch, and freeze its source identity before checks. |
| `VERIFYING → IMPLEMENTING` | Applicable checks ran and failed within the worker's scope and retry budget. Prepare bounded diagnostics tied to the failed check and candidate. Refreeze after the next admitted patch. |
| `VERIFYING → REVIEW_PENDING` | All required verification passed for the frozen candidate. The review bundle includes the exact source and check evidence. |
| `REVIEW_PENDING → CHANGES_REQUESTED → IMPLEMENTING` | The operator attested to the verdict and the findings fit an allowed correction. Recheck and request fresh review for the replacement candidate. |
| `REVIEW_PENDING → APPROVED → INTEGRATION_PENDING` | Valid candidate-bound QA and independent review. The local tool has completed verification and review. |
| `INTEGRATION_PENDING → DISTRIBUTION_PENDING → CLOSED` | For an authorized source push to `master`, the operator records the integrated identity and verifies [Distribution](07-02-multi-model-development-workflow.md#distribution-after-a-source-push-to-master) before closure. Other authorized integrations close when their applicable integration evidence is recorded. The tool records evidence only. |
| `PAUSED`, `BLOCKED`, `FAILED`, `CANCELLED`, `BUDGET_EXCEEDED` | Preserve state, reason, and next step. A pause, blocked prerequisite, or raised budget can resume after revalidation and reconciliation. Failure and cancellation close the run without acceptance. A rejected review records its disposition and fails the run. |

Use one local transactional run journal, preferably SQLite from the Python standard library. Store state changes, timestamps, attempts, source and evidence IDs, and budget reservations in transactions. Derive JSONL and Markdown summaries from those committed records. Serialize mutating invocations with a repository lock so overlapping invocations cannot create a second active task or mutate the same candidate. Read-only status can report the last committed state.

Before a side effect outside the journal, record an intent with a run-scoped operation ID, required starting state, and expected observable result. For an admitted patch, record the expected result tree computed in isolated staging before mutating the run worktree. After the operation, record completion and the result identity. On restart, take the lock and recheck the pinned revisions, allowed policy, worktree, budgets and artifacts. A matching already-applied patch, frozen candidate or recorded result is reused. Retry a definitely unperformed, repeatable step only after reconciliation. State outside the expected transitions blocks the run with recovery details. Approval depends on the local operator's attestation recorded in trusted state and the matching Markdown and structured evidence.

## Usage, provider and logging

Use the [five-hour checkpoint rule](../06-delivery/06-02-implementation-milestones.md) for Codex usage, separately from provider tokens, price ceilings and wall time. Check before dispatch or another correction. Stop early enough to record the current phase, last completed operation ID, verified candidate, remaining usage or unknown, open checks, and exact next command in the passdown. Pause if the remaining quota cannot support the next increment and closeout. An unknown quota can support a bounded operator-supervised local checkpoint only.

Use the mock adapter first. For the later DeepSeek trial, require explicit approval of the HTTPS endpoint, model, disclosure scope and current pricing. Load the API key only into the trusted adapter process. Ensure egress is restricted to the approved destination. Send the admitted payload plus bounded, authorized correction context. Confirm the supported API and validate responses with fake transport before optional live tests. Use patch-only responses in the trial. Provider roles, model availability and pricing are configurable validated inputs.

Record request intent and reserve worst-case input and output budget before each paid send using verified pricing and caps. A retry requires a new reservation. If a reservation cannot be made, block dispatch. Settle known usage when the provider reports it. After a request where the result is uncertain, retain the reservation and pause until the result can be reconciled or the operator explicitly authorizes a new reserved attempt. Retry definite transient failures within the allowed backoff, budgets and provider request limits. Track provider attempts, code corrections and independent review correction cycles separately.

Retain run ID, source/package/fixture and toolchain identities, check IDs and outcome, provider and model, recorded usage or unknown, dated price basis, estimate or unknown, attempts, elapsed time, preparation, correction and review effort, and integration result. Redact secrets, private content and provider traces at ingestion. Use sanitized extracts, IDs and hashes in reports. Keep security audit material in `untracked/security/`.

## Local CLI

| Command family | Observable behavior |
| --- | --- |
| `doctor` | Report pinned prerequisites, sandbox capabilities and relevant missing configuration without printing credentials. |
| `task validate`, `task prepare` | Validate the manifest and semantic approval record. Freeze the payload from pinned inputs and prepare the worktree after readiness passes. Print starting and package identities. |
| `task run`, `task resume`, `task status`, `task cancel` | Execute admitted transitions, recover or report the run, and stop safely on cancellation. The pilot defaults to the mock adapter. Resume confirms pinned inputs and limits. |
| `review export`, `review import` | Export a read-only candidate bundle and validate the operator-attested verdict against the frozen candidate and QA evidence. |
| `task report`, `usage summary`, `cleanup --dry-run` | Produce reports and inspect proposed cleanup. Deleting dirty or unintegrated work requires separate explicit confirmation. |

Document argument syntax, a reproducible demonstration, and stable exit statuses for validation failure, blocked work, verification failure, provider failure, budget, changes requested, and internal fault. Implement the smallest command set needed for OR01 before completing the remaining interface. The tool's interfaces support worktree-local changes and records. Integration and publishing use the existing owner-controlled workflow.
