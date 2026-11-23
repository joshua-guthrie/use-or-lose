# Checkpoint 28 · Multi-model orchestration specification

## Completed

Staged revision 1.5 of the supplied plan and integrated its implementation requirements into numbered Organization, Quality, and Delivery chapters. Added a root orchestration index, a detailed design template, and a synthetic pilot design. Linked the existing QA, milestone, implementation brief, and review materials. The briefs retain their existing home.

The staged revision maps v1.4 requirements to their governing chapters. The workflow now specifies enforced isolation for candidate checks, a frozen source shared by QA and review, an operator-attested verdict, transactional resume and budget rules, and Distribution evidence for a source push to `master`. Tool execution and Android product work are future milestones. [Planning revision 1.5](../planning/multi-model-orchestrator-plan-v1.5.md) · [Orchestration index](../orchestration.md)

## Verification and usage

Checked 181 local links and anchors across the 21 changed Markdown files, chapter numbering, 15 unique orchestrator acceptance IDs, and the planning source digest. The source baseline for this documentation increment was `a8369eee6c80de549a8778d95fe4385052f6aac5`. The measured five-hour window had 43% remaining at documentation closeout. The local commit is the checkpoint.

## Where to resume

For the next orchestration increment, prepare the OR01 brief. Pin the available Python and Git versions, identify a Linux isolation backend that can enforce Chapter 07.04, and record the initial disposable fixture and trusted check IDs. Work through the five offline cases before considering paid provider use. [OR01](../chapters/06-delivery/06-03-orchestrator-pilot-milestones.md#or01-offline-mock-pilot) · [Pilot definition](../detailed-design/DD-OR01-01-offline-pilot-fixture.md) · [Acceptance cases](../chapters/05-quality/05-03-orchestrator-verification.md#offline-pilot-cases)

For Android product work, the next owner review is still the menu walkthrough before I01. The I02–I04 briefs and reference fixtures remain open. [Android milestone order](../chapters/06-delivery/06-02-implementation-milestones.md)
