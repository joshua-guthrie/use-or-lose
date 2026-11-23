# Chapter 07.03 · Design and implementation handoffs

An implementation brief must be usable by a developer or smaller coding model without chat history. A user-facing task starts from the product requirement and ends with evidence against its acceptance cases. An orchestration development task uses [Chapter 05.03](../05-quality/05-03-orchestrator-verification.md) as its acceptance source.

## Three levels of detail

| Level | Source and purpose |
| --- | --- |
| 1. Product and architecture | The [product specification](../../specification.md), numbered chapters, approved mockups, [parity](../../verification/feature-parity.md) and [acceptance cases](../../verification/acceptance-tests.md). These define behavior, constraints and expected results. Use stable IDs or exact anchored sections. |
| 2. Detailed design | An existing authoritative chapter or a [numbered design record](../../detailed-design/README.md). Settle interface signatures, ownership, state transitions, invariants, units, example inputs and outputs, failure and recovery behavior, and test seams. Record material decisions and the design review within the versioned design or a linked decision note. |
| 3. Implementation assignment | The established [implementation briefs](../../../implementation/briefs/README.md), task manifest and generated worker packet. State exactly what can change, what must be preserved, and how to prove the result. |

One design may govern multiple briefs. R0 needs a reference and a rationale if additional engineering detail is unnecessary. R1 needs exact interfaces and examples. R2 also needs integration boundaries, concurrency and failure cases as applicable. R3 design work must include data invariants, migration and recovery evidence where relevant, and owner decisions. Use existing approved detail when it covers the task. Label planned paths and symbols explicitly until they exist. Write designs just in time.

## Establish the starting evidence

Record a read-only baseline for the task's affected interfaces before delegating. Include the exact source revision, read paths and callers, relevant fixtures, commands and toolchain, expected and observed results, known baseline failures, and unexamined areas. Identify the checks that establish preservation. Existing valid evidence is reusable when its revision, environment, and inputs cover the same boundary. An unresolved relevant failure or inaccessible check blocks a candidate that depends on it. Use a linked scope record or a short baseline section in the brief. Keep navigation entries from feature to entry point, symbol, and test for material changes.

Distinguish the task's starting Git revision from the Android spec's pinned desktop oracle. Verify CLI flags, outputs and exit codes, JSON schema and unknown-field preservation, accounting goldens, and packaging surfaces when the affected scope reaches them. A baseline records observations. Any change to an approved expectation needs the normal spec approval and a linked decision. Keep earlier observations when adding a later baseline delta. Keep security audit working material under ignored `untracked/security/`. [Repository working rules](../../../../AGENTS.md#local-security-reviews)

## Prepare and approve the worker packet

1. Write or update the [brief](../../../implementation/briefs/template.md). Pin the full base and specification revision. Resolve requirement and acceptance IDs separately, locate existing symbols, list the proposed files, pin applicable designs and fixtures, and record approval or the rationale for design reuse. Describe what is out of scope and the local choices the worker can make.
2. Define exact accepted examples, expected failures and existing regressions, trusted check IDs, evidence paths, and independent test expectations. Identify pre-existing checks separately from tests the worker is assigned to create. Record applicability and prerequisites. A required missing device, tool, decision, or unresolved acceptance reference blocks readiness for the affected task.
3. Build the worker packet from pinned Git content using an explicit file or excerpt list. Include scoped source excerpts and an ordered brief, approved design and acceptance excerpts with source IDs, fixture identities and results, and a worker response contract. Keep the scope sufficient for the assignment. Stop if the content exceeds the context budget or contains material outside the approved disclosure scope. Report the offending input for the design owner to resolve.
4. Canonically order and hash the payload and its inputs. Record the format version and the SHA-256 package identity. Store the hash, timestamp, approvals and results in the trusted run record outside the hashable payload. A generated packet has no authority to amend its sources. Keep runtime packets and transient traces in ignored storage. Track the human-authored requirements, designs and briefs and retain sanitized verifiable summaries.
5. Validate the manifest, source hashes, anchors, bounded write paths, design references or applicable exception, check prerequisites and current approval. The design owner records a semantic readiness review of contracts, failure paths and expected results. Structural validation checks identifiers and integrity. Only the recorded design review establishes semantic sufficiency.

A design question records the task ID, governing IDs, observed mismatch, affected file or fixture, and options with consequences. A local clarification updates the brief. A design change updates the detailed design and dependent briefs. A product or material architecture change goes through owner review of the relevant chapters. Rebuild and reapprove the packet, then apply fresh checks and review to any affected candidate.

The [execution chapter](07-04-orchestrator-execution.md) specifies the manifest and provider contract. The [pilot milestones](../06-delivery/06-03-orchestrator-pilot-milestones.md) define when to exercise each capability.
