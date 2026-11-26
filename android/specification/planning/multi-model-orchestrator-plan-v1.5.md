# Use or Lose Android multi-model development orchestrator · planning revision 1.5

This is the staged revision of `Use or Lose_Multi_Model_Development_Orchestrator_Specification_v1.4.md` from November 22, 2026. The v1.4 source has SHA-256 `8ad047952f956ffb39ed0d73fc2c0c19d7ddb6ecce971ec8c767a8471f8a4e9c`. The proposal below retains the original goals and revises the rollout, trust boundaries, and completion criteria. The [orchestration specification](../orchestration.md) indexes the governing chapters.

## 1. Purpose and scope

Test whether Use or Lose can use a lower-cost model for bounded implementation while preserving the product specification, mockups, accounting, and desktop compatibility. Keep design and review with the stronger model. Give the worker an assignment that can be completed with pinned evidence and without the design conversation.

The proposed tool is a local Python CLI, provisionally `fedleave-orch`, with a deterministic mock adapter and a later DeepSeek adapter. A run starts with a validated task, creates a disposable worktree, accepts a scoped patch, runs applicable checks, and hands the result to a separate Codex review session led by the operator. Owner approval governs product and architecture decisions, external disclosure, integration, and release.

The tool is for development. Use or Lose's Android app remains a local application with its existing product boundaries. The orchestrator's work does not require rewriting the desktop, changing the Android stack, or establishing production accounting interfaces during I01. Autonomous review, publishing, parallel workers, cross-machine execution, a dashboard, and notifications are deferred.

## 2. Documentation and ownership

| Level or artifact | Revised plan |
| --- | --- |
| Level 1. Product and architecture | Extend the existing numbered chapters only when a requirement changes through the normal approval procedure. Keep parity, mockups, and acceptance IDs attached to their original rules. |
| Level 2. Detailed engineering design | Author or reuse a pinned design where the assigned work needs decisions beyond the existing chapters. Use exact contracts, failure states, fixture outputs, and a reviewed decision record. Use proportional detail and record a rationale when the existing contract is sufficient. |
| Level 3. Implementation brief and handoff | Keep the existing briefs under `android/implementation/briefs/`. Add a pinned manifest and generate the scoped worker packet in ignored storage. Retain its approval and content identity in the run record. A future move requires its own decision and link validation. |
| Baseline | Start with the touched surfaces and their existing tests, fixtures, entry points, and interfaces. Pin the starting source separately from the desktop product oracle. Record commands, observed results, gaps, and affected preservation contracts. Expand coverage when a task crosses more boundaries. |
| Evidence | Save sanitized QA and independent review summaries in their existing directories. Keep security audit findings, working notes, and evidence in ignored `untracked/security/`. A tracked nonsecurity risk list may link to an approved remediation change. |

The design owner resolves ambiguous contracts and reviews readiness. The worker implements ordinary local details and raises a question when it encounters a missing decision, a conflict, or an essential change outside its paths. A structural check can detect missing or stale inputs. A qualified reviewer decides whether their content is sufficient.

Risk determines eligibility and review depth. R0 is mechanical work. R1 covers routine changes within an approved contract. R2 covers integration and stateful boundaries and requires a stronger design and evidence. R3 covers accounting, storage generations, restore, security controls, packaging, signing, and release. Keep R3 Sol-led through the pilot. Product and architecture approval requirements apply at every risk level.

## 3. Execution and evidence

1. The design owner pins the starting tree, governing requirements, design and approvals, synthetic fixtures, accepted expectations, exact writable paths, and trusted check IDs. Mutation is limited to assigned sources and permitted tests. Existing compatibility fixtures, governing requirements, protected tests, repository configuration, and release files are outside the worker's scope.
2. The orchestrator validates structure, hashes, references and scope, then checks that a trusted operator admitted the design and packet. The worker receives approved synthetic examples and scoped excerpts read from pinned revisions. A format error or unresolved decision blocks dispatch.
3. The worker returns a bounded patch or a structured design question. The patch is validated before writing. Treat patches, source comments, retrieved documents and test output as data. The orchestrator controls Git, the patch destination, subprocesses and state.
4. A worktree contains the file changes. An enforced sandbox controls access when a check runs candidate code, including an offline mock candidate. Trusted check IDs resolve to fixed argument arrays, directories, budgets and dependency inputs. Enforce no credentials, denied network, limited mounts and process cleanup. Missing isolation blocks the check. Fetch pinned dependencies separately in a trusted setup step if they are needed.
5. Freeze the candidate source before verification. Build from it, keep outputs outside the source identity, and ensure the source still matches after the checks. Give the independent reviewer that identity, the diff, the frozen brief, and the QA results. Store reports outside the source snapshot they identify. A changed source or effective integration baseline requires new applicable evidence and approval.
6. An operator imports a manually reviewed, candidate-bound verdict. The Markdown review and structured record agree on findings and evidence. Treat the approval as operator-attested. The worker and its test processes have no write access to the run journal, verdicts, or approval store.
7. Local approval reaches `INTEGRATION_PENDING`. The authorized owner decides whether to integrate. For a source push to `master`, record `DISTRIBUTION_PENDING` until the exact source has successful Distribution evidence for both platform packages, publication of stable `beta` assets, and a matching installer mirror. Raise packaging failures and keep the task open.

## 4. Recovery and operating budgets

Use one authoritative transactional run journal and a lock that serializes mutation. Derive human-readable reports and JSONL exports from it. Check the authoritative record and worktree on resume. Resume a known operation idempotently and suspend a run whose state cannot be reconciled. Retain the history of failed candidates and reviewer dispositions.

Before a paid request, record its ID and worst-case budget reservation. If a timeout leaves the provider outcome unknown, retain the reservation and pause for reconciliation or an operator-approved retry within the remaining budget. Track API attempts, coding attempts, and review correction cycles separately. A missing usage result is unknown. If the worst-case reservation cannot be established, block paid dispatch.

Use the measured five-hour Codex window separately from provider cost, tokens, and runtime. Start closeout early enough to finish while at least 30% remains. Pause at a consistent checkpoint, record the run and exact next action, and commit verified work. If a reliable reading is unavailable, record that fact and restrict progress to a bounded supervised local checkpoint. Do not dispatch a live run on the strength of an unknown quota.

## 5. Staged delivery and decision points

| Stage | Work and exit evidence |
| --- | --- |
| Offline pilot | One synthetic pure function, a fixed trusted test, one mock adapter, and a scoped worktree. Demonstrate a passing run with distinct review, a blocked check, restart without duplicate work, and rejection of an approval for the wrong source. Validate that the primary checkout and protected files keep their identities. Stop at integration pending on a disposable repository. |
| Offline hardening | Complete the negative cases for path policy, isolation, package freshness, state recovery, private paths, budgets and approval provenance. Use real local subprocess/worktree tests and fake HTTP transport. Record the requirements-to-evidence chain and reproducible operator commands. |
| Opt-in provider trial | Confirm endpoint, model, account, current pricing and permitted data handling with the owner. Use only a synthetic disposable task at first. Keep review manual. Record whether the optional paid smoke test ran and its actual result. |
| First real Android task | After the menu review and I01 approval and completion, select a small R1 task with its own satisfied dependencies and clear checks. Keep the I02 bridge and R3 surfaces with the design owner. Review QA and total effort before choosing regular use. |

Split any stage that exceeds one five-hour session and commit only a coherent, verified increment. Capture advanced-model preparation time, provider cost or unknown, review and correction time, final evidence, and first-pass results. Compare at least five comparable real tasks with a Sol-only baseline before claiming a percentage saving.

## 6. Where the v1.4 requirements landed

| v1.4 areas | Treatment in this revision |
| --- | --- |
| §§0–4B and AC-13, AC-17–23, AC-26, AC-29, AC-31–33, AC-37 | Preserve the three levels and scoped regression evidence. Retain established brief locations and generate packets on demand. Expand the baseline and navigation map with the interfaces that need them. [Design and handoffs](../chapters/07-organization/07-03-design-and-implementation-handoffs.md) |
| §§5–9, 12–13 and AC-01–08, AC-11–12, AC-16, AC-20, AC-24, AC-36 | Enforce sandboxing during actual candidate execution, trusted checks, immutable identities, transactional recovery, paid request controls, and separate quota measurement. [Orchestrator execution](../chapters/07-organization/07-04-orchestrator-execution.md) |
| §§10–11B and AC-09–10, AC-25, AC-27–28, AC-34–35 | Freeze before tests and review. Keep the separate Markdown review. Use a human-readable maintainability and Python explanation checklist. Tests verify that missing review evidence blocks closure. [Workflow and approvals](../chapters/07-organization/07-02-multi-model-development-workflow.md) · [Orchestrator verification](../chapters/05-quality/05-03-orchestrator-verification.md) |
| §§14–19 and AC-14–15, AC-30 | Start with the offline pilot. Require evidence before each expansion and preserve Android and Distribution gates. Retire the v1.4 kickoff instructions in favor of a numbered milestone and its brief. [Pilot milestones](../chapters/06-delivery/06-03-orchestrator-pilot-milestones.md) |

Provider and optional reviewer integration references are checked again at implementation. [DeepSeek API](https://api-docs.deepseek.com/api/create-chat-completion/) · [DeepSeek model pricing](https://api-docs.deepseek.com/quick_start/pricing/) · [Codex non-interactive mode](https://learn.chatgpt.com/docs/non-interactive-mode)
