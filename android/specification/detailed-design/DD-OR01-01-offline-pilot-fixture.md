# DD-OR01-01 · Offline pilot fixture

Proposed synthetic fixture for [OR01](../chapters/06-delivery/06-03-orchestrator-pilot-milestones.md#or01-offline-mock-pilot). The paths below are to be created in a disposable repository. The tool implementation must record the actual fixture and design revisions when it constructs the task.

## Contract

Use a dependency-free Python repository. Its baseline `sample.py` exposes `render_label(number: int) -> str` and returns `str(number)`. Keep a trusted check outside worker-writable paths. The baseline check asserts `render_label(-2) == "-2"` and `render_label(0) == "0"`. Record these two results before applying the mock patch.

The task changes only `sample.py`. Produce exactly `"+3"` for `render_label(3)`. Preserve the two baseline results. The candidate check compares all three results. The mock emits a patch that computes the positive prefix and leaves the other cases unchanged. A sandboxed subprocess loads the function from the read-only source mount and returns a JSON array of strings in input order. The trusted check driver compares the parsed array outside the candidate process. An absent or malformed response, unexpected value, or nonzero exit fails the check.

## Expected evidence

Use the protected policy, run journal and check harness to record both baseline and candidate results. Record the resulting candidate and QA identity for the separate review. Use different disposable runs for the blocked sandbox case, restart checkpoint, and wrong-candidate review case. Apply the [OR01 acceptance matrix](../chapters/05-quality/05-03-orchestrator-verification.md#offline-pilot-cases). No Android, Use or Lose ledger, or external provider access is needed for this fixture.
