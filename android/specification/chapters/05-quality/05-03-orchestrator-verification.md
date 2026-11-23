# Chapter 05.03 · Orchestrator verification

Use these acceptance cases for the development tool. Continue to apply [Chapter 05.02](05-02-quality-gates.md) to the normal QA and independent review of tool changes and any Android candidate. Tool verification uses synthetic repositories and fixtures. Record the exact command, Git identity, relevant case ID, observed result, disposition, and evidence link for each case.

## Offline pilot cases

Run these cases using the [synthetic label fixture](../../detailed-design/DD-OR01-01-offline-pilot-fixture.md), with no provider key or network. Capture the real local worktree and sandbox outcomes. Android SDK, handset and paid provider verification are inapplicable to this synthetic scope.

| Case | Exercise | Expected result |
| --- | --- | --- |
| `T-ORCH-PILOT-01` | Validate a pinned brief and prepare a disposable worktree. Use the mock adapter to submit the bounded patch and run the trusted check. Record a separate QA and independent review. | The baseline reads `-2` and `0`. The candidate returns `-2`, `0`, and `+3` for the three fixed inputs. QA and the accepted review identify the same frozen candidate. The run reaches `INTEGRATION_PENDING`. The starting commit, primary checkout, and protected inputs keep their initial identities. |
| `T-ORCH-PILOT-02` | Disable the required sandbox capability and request verification. | `BLOCKED` identifies the missing capability. The check never launches and there is no review-eligible candidate. |
| `T-ORCH-PILOT-03` | Interrupt the run after its patch has been applied and frozen but before the step's completion record is committed. Resume under the run lock. | Resume recognizes the frozen candidate and continues without applying the patch again, dispatching a new turn, or creating an approval. The recovered operation and candidate IDs are traceable. |
| `T-ORCH-PILOT-04` | Try importing an approving verdict and review for a different candidate into a run with passing verification. | The verdict is rejected and the run remains `REVIEW_PENDING` for the actual source. Import a matching, operator-attested verdict separately to prove the supported path. |
| `T-ORCH-PILOT-05` | Give a real worktree run a validly formed patch that includes one protected file alongside an allowed edit. | The entire patch is refused before modifying either file. The run records the scope failure. |

## Hardening and provider cases

Complete the offline cases before enabling the external adapter. Use fake transport for paid-request and provider behavior tests.

| Case | Exercise and pass condition |
| --- | --- |
| `T-ORCH-READY-01` | An unresolved requirement, invalid interface anchor, missing required design or fixture, inadequate approval, wrong revision, registry-policy drift, or tampered packet blocks dispatch. An eligible, reviewed packet with pinned inputs validates and can dispatch. |
| `T-ORCH-SCOPE-01` | Invalid paths, link destinations, Git metadata, unauthorized changes to fixtures or tests, unrelated changes, unsupported patch forms, and overlapping permissions are refused before mutation or rejected at scope review. Assert the primary checkout and protected paths remain unchanged. |
| `T-ORCH-ISOLATE-01` | In a real sandboxed subprocess, verify that approved inputs are readable, only the approved output area is writable, credential and service access is absent, and network is denied. A missing sandbox or required test tool is blocked. Confirm cancellation and timeout clean up child processes. |
| `T-ORCH-RECOVERY-01` | Inject interruptions across worktree, patch, candidate freeze and check-result recording. A known completed operation is reconciled, a safe repeatable operation is retried only when needed, and an unexplained worktree edit blocks. Overlapping mutating invocations serialize. Check that cancellation and recovery never imply approval. |
| `T-ORCH-VERIFY-01` | Required failed tests and missing evidence keep the run open. A child process that exits successfully without required results cannot pass. A scoped verification failure leads to a bounded correction and fresh tests. A changed source invalidates earlier evidence. A changed integration base requires re-evaluation. |
| `T-ORCH-REVIEW-01` | Missing separate Markdown review, absent origin attestation, mismatched evidence IDs, stale source, unresolved major findings, or an unauthorized verdict cannot approve a run. Retain the rejected revision when a correction receives fresh evidence and a new review. |
| `T-ORCH-LIMITS-01` | An exhausted attempt cap or quota pauses or stops safely according to its policy. An unknown quota only permits a supervised bounded local checkpoint. Wall time and coding and review limits apply independently. |
| `T-ORCH-PROVIDER-01` | Fake transport verifies allowed destination and model, the authorized context, usage treatment, authentication versus transient error, and uncertain-result recovery. No paid request without its reservation. A timed-out send with uncertain outcome pauses without an automatic resend. Logs, reports and child environments omit credentials and private input. |
| `T-ORCH-DISTRIBUTION-01` | Synthetic integration records leave a `master` source push in `DISTRIBUTION_PENDING` for absent, failing or mismatched publication evidence. Close only on the documented matching source/package/asset/mirror evidence. Exercise this case without publishing. |
| `T-ORCH-OPERATOR-01` | Install in the documented clean local environment and reproduce the demonstration. Confirm `doctor` explains missing dependencies without secrets and the final QA, independent review, and passdown link the policy, inputs, candidate and observed results. |

The independent review checks the tests themselves, including preservation assertions and whether failure cases exercise the intended boundary. The reviewer evaluates readability, explanations, and code navigation against [Chapter 07.02](../07-organization/07-02-multi-model-development-workflow.md#review-and-approval). Automated verification can require the assessment and validate its references. Comment quality and semantic design sufficiency are reviewer decisions.

Report an optional live test accurately as executed, blocked, or inapplicable with a reason. Use the [QA report template](../../../qa/reports/template.md), [code review template](../../../reviews/code/template.md), and [pilot milestones](../06-delivery/06-03-orchestrator-pilot-milestones.md) to record completion and remaining gates.
