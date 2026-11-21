# I__-__ · Implementation brief

## Assignment

Record status, starting commit, exact approved spec revision, prerequisite reports, expected changed files and preserved interfaces. List the files and symbols to read in order, with names the implementer can find in the starting tree. Define work deferred to another milestone and the callable or navigation boundary between them.

## Behavior and contracts

List the input types, outputs, units, date handling and null or unavailable meaning. Enumerate the normal flow and each applicable loading, empty, invalid, pending, error, cancel, retry and restart transition. State which operations write, when a write commits, and how a retry or overlapping request is handled. Give exact examples from named synthetic fixtures.

## Screen and resource checklist

Map every requirement and state to a screen ID and annotated mockup. Specify visible labels, touch targets, navigation entry and return, focus, TalkBack reading and action, text scaling, and platform-owned UI. Name any stable test tags or other hooks the verification needs.

## Verification

List the commands, working directories, build variant, device profile, fixture path and fixed date. Give exact expected outputs and which neighboring flows to rerun. Record where screenshots and logs go and which QA gates apply. Give a direct way to reproduce each example from a clean checkout.

## Handoff

List expected deliverables and the report, code review and passdown paths. Record conditions to mark complete and the next milestone's dependency. Before assignment, resolve any placeholder that affects a build, contract, behavior, file path or test result.
