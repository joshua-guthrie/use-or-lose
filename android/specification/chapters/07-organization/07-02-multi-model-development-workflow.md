# Chapter 07.02 · Multi-model development workflow

The orchestrator supports the people and models building Use or Lose. The [Android product specification](../../specification.md), [QA gates](../05-quality/05-02-quality-gates.md) and [repository working rules](../../../../AGENTS.md) govern the work.

## Roles and delegation

| Role | Responsibilities |
| --- | --- |
| Project owner | Decides product behavior, material architecture changes, external provider and data scope, exceptions, integration, and release. The authority recorded for one decision applies only to that decision. |
| Design owner (Sol/Codex or equivalent) | Resolves requirements, pins the baseline, designs interfaces, writes the brief, assesses technical readiness, and handles questions that exceed the brief. |
| Operator | Confirms scope and approvals, runs the tool, supplies credentials through the trusted adapter, gives evidence to a fresh reviewer, and records the origin and disposition of the review. |
| Coding worker | Reads the frozen package, proposes a bounded patch and reports findings. It may choose local implementation details allowed by the brief. It raises a design question when work would cross that boundary. |
| Orchestrator | Checks identities, scope, permissions and limits. Applies patches, runs named checks, and records evidence. Makes readiness and status decisions from recorded inputs. |
| Independent reviewer | Reviews the final diff, tests, QA and applicable mockup captures in a fresh pass separate from the implementing session. Records findings and a candidate-specific decision. |

| Class | Examples and eligibility |
| --- | --- |
| R0 | Mechanical edits to authorized implementation files. Reuse existing decisions. A brief can cite the rule and record why a separate design is unnecessary. |
| R1 | An isolated component, display adapter, or pure helper within an approved interface. Eligible for the first live Use or Lose trial following the offline and provider gates. |
| R2 | Bridge integration, cross-screen state ownership, concurrency, or a data adapter with broader callers. Require integration and recovery cases, an approved detailed design, and explicit admission by the design owner. Keep exploratory work such as I02 under the design owner. |
| R3 | Shared accounting and leave-policy semantics, canonical writes or migrations, backup and restore, security policy, installer publication, and signing. Keep these Sol-led through the pilot. Any later delegation requires explicit owner approval for that task and enhanced independent review. |

The design owner sets the highest applicable class. Risk informs design depth, scope and retry limits. All applicable QA gates remain required. The worker reports unexpected dependencies, contradictory fixtures, unspecified behavior, or required out-of-scope edits as a design question and stops that task. A new governing revision requires reconsidering affected packages and approvals. [Handoff and readiness rules](07-03-design-and-implementation-handoffs.md)

## Review and approval

1. Freeze source before its checks. Retain the base and candidate IDs, governing spec and design, brief and package identities, exact source diff, fixture and toolchain IDs, check results, worker summary, and any missing evidence. Use [Chapter 05.02](../05-quality/05-02-quality-gates.md) for functional, visual, accessibility, platform and regression checks.
2. Export that evidence for a fresh manual Codex review. The reviewer looks at runtime behavior, compatibility, failure states, quality and independence of tests, and the conformance of actual screens to the applicable mockups. Check that entry points, affected symbols and tests are discoverable. For changed nontrivial Python, review meaningful names, type hints at relevant interfaces, contract docstrings, and comments explaining subtle reasons or invariants. Check whether a new contributor can navigate and understand the change.
3. Use the existing [QA report](../../../qa/reports/template.md) and [code review](../../../reviews/code/template.md). Append separate candidate sections if a task needs corrections. The review records stable finding IDs, severity, actual observations, scope, results, and links. A machine-readable verdict cites the reviewed source and QA evidence. The operator imports it with a local attestation of origin in a trusted session. Reserve automated Codex invocation or stronger identity assurance for separate work.
4. A verdict is `APPROVED`, `CHANGES_REQUESTED`, `REJECTED`, or `BLOCKED`. Approval requires all applicable QA passes, the Markdown review and agreement with its structured verdict, and zero unresolved major or critical findings. Record resolved minor findings or an explicit acceptance with rationale and the appropriate approval. Changed candidates get fresh applicable QA and review. Keep earlier findings and retests traceable.
5. Close local pilot work at **integration pending** when its independent review accepts the evidence. The owner separately authorizes integration. If integration changes the source or effective base, prepare and verify a replacement candidate. Reports and verdicts identify the tested source and are written outside the snapshot they attest to.

## Distribution after a source push to master

The authorized operator handles integration outside the worker and orchestrator. For a source push to `master`, record **distribution pending** while waiting for the [Distribution workflow](../../../../.github/workflows/distribution.yml). Record the integrated source revision, the successful workflow run that built and tested both platform packages, verified stable `beta` assets, and the rolling installer mirror revision with matching artifact identities. Only then record release-related closure. Record and resolve packaging failures under the repository rules. Report any approved public download URL change in the push or issue closeout. [Installer publication rules](../../../../AGENTS.md#installer-publication-is-part-of-every-push)
