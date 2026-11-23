# Chapter 06.03 · Orchestrator pilot milestones

The pilot tests a proposed development tool before it is used for Android product work. Follow the [existing five-hour checkpoint and commit rule](06-02-implementation-milestones.md), including the 30% remaining closeout. Write a verified brief and use the [orchestrator acceptance cases](../05-quality/05-03-orchestrator-verification.md) for each increment. Split a milestone by the first unmet dependency when it will exceed the window. Record the completed portion, exact next action and any blocked gates in the root passdown and the implementation note.

## OR01. Offline mock pilot

Create a minimal local CLI and disposable synthetic repository for the [label fixture](../../detailed-design/DD-OR01-01-offline-pilot-fixture.md). Pin the Python, Git, schema and local isolation setup in the brief. Before running candidate code, establish the trusted check and its isolation. Implement prepare, run, resume, status and review handoff for one serialized task. Allow the mock adapter to propose one bounded text patch. Freeze before testing and keep the review and run state outside the candidate. Use a candidate-bound operator-attested verdict.

**Exit evidence** includes passing `T-ORCH-PILOT-01` through `05`, a documented reproduction, actual baseline and candidate identities, QA report, separate review, and a feature-branch checkpoint. Keep the approved synthetic candidate at integration pending. A missing sandbox is a legitimate blocked outcome for the negative case and a blocker for the passing case.

## OR02. Offline hardening

Expand the manifest/readiness gate, trust isolation tests, candidate and journal recovery, cancellation, and independent review integrity. Add fake-transport provider and budget tests, the synthetic Distribution evidence test, and the rest of the applicable CLI and operator documentation. Record preserved project surfaces as the scope expands. Split recovery and provider budgets into their own verified increments if needed.

**Exit evidence** includes the applicable offline hardening cases, negative results as expected, and a clean reproducible demo. Keep provider calls disabled until OR03 authorization.

## OR03. Optional external provider trial

Get explicit owner approval for the endpoint, model, account spending, and external data handling. Verify the provider capabilities and dated pricing again. Use one disposable synthetic task with an approved payload, a bounded budget, and the existing sandbox and manual review. If the live smoke test cannot run, record its status and the exact dependency. The fake-transport tests remain required.

**Exit evidence** for an actual provider trial includes the sanitized request and budget record, confirmed disposition, verification, and independent review. A decision to defer the trial is a documented milestone outcome. Any later real paid task still depends on a successful synthetic provider trial.

## OR04. First real Android assignment

After the [menu walkthrough and I01 completion](06-02-implementation-milestones.md#review-menu-navigation-before-i01), choose a low-risk R1 task with verified prerequisites and a clear acceptance case. Keep the bridge experiment, shared accounting, canonical storage and restore, signing, and release with Sol and the owner through the pilot. Use the governing mockups and existing product QA for any affected UI or accessibility. Record the same review and QA evidence as for a normal Android milestone.

**Exit evidence** includes the candidate's passed Android QA and independent review, separate integration decision, and the measured design, implementation, correction, review, and elapsed effort. The orchestrator is optional for Android milestones. Compare at least five comparable bounded real tasks with a Sol-only baseline before claiming a percentage saving. Record first-pass acceptance, defects and final outcomes alongside cost.
