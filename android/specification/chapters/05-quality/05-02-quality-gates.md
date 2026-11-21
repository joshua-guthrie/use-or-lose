# Chapter 05.02 · Iterative QA and milestone completion

An implementation milestone passes when the candidate conforms to the versioned specification and all applicable gates below pass. The approved requirements, acceptance cases, and annotated mockups set the expectations. A passing build or a plausible-looking screenshot is one piece of evidence. Record both behavior and visual conformance for each affected screen and state.

## Run the loop in every implementation milestone

1. **Freeze the target.** Record the milestone ID, approved spec revision, exact requirement and acceptance IDs, selected mockup variants, and changed or affected flows. Identify the cases to run and the devices and conditions for each. Include adjacent navigation, shared components, and existing behaviors the change could affect. Record gate applicability before testing. A milestone that needs an unresolved product decision remains open for the affected behavior.
2. **Build a candidate.** Record the code commit or reproducible source tree, build variant, toolchain, device or emulator, synthetic fixture and clock, and test commands. Run from the normal app entry points. Cover success and the relevant empty, loading, blocked, invalid, retry, cancellation, and recovery states.
3. **Check behavior and visuals.** Run the selected acceptance cases and existing regression suites. Capture the implemented UI with the same state, data, and framing as the referenced mockups. Compare the screenshots and the actual interactions using the visual checks below. Check related screens when changing a shared component. Verify files and OS handoffs separately when applicable.
4. **Review the candidate.** Run a distinct AI code review against the candidate diff, governing chapters, tests, and visual evidence. Review the change with a fresh read of the code and evidence, including behavior that callers rely on. Record the review in a Markdown report under `android/reviews/code/`. A different review pass is required even when the implementation was AI-assisted. Ask the project owner to decide changes to the spec or approved mockups.
5. **Fix, then retest.** Record each mismatch against an ID, fix it, and verify the exact failed case plus affected regressions and related screenshots. Re-review changed code and retest the applicable completion gates on the final candidate. Preserve failures in the QA report so it shows what changed and why it now passes.
6. **Close with evidence.** Save the QA report under `android/qa/reports/`, with screen captures and diffs under `android/qa/evidence/` or linked retained CI artifacts. Link the separate code review. Record the final build identity and the result for every gate. A required failure, unexplained visual difference, missing test run, or open conformity finding leaves the milestone open. A five-hour cutoff can end a session with a work-in-progress commit and passdown. Mark the milestone complete after the gates pass.

## Make each milestone executable from a brief

Write a self-contained implementation brief for every bounded coding task. Plan for a smaller coding model such as GPT-5.6 Luna to follow it without chat history. The brief is the entry point to the spec, fixtures, and tests for that task. Specify enough detail that a developer can implement the acceptance behavior without reconstructing it from chat history or guessing a UI or accounting rule.

| Brief section | Required details |
| --- | --- |
| Starting state and scope | Starting commit, files and symbols to read, files or modules the task may change, prerequisite results, and explicit deliverables. Identify existing interfaces and behavior the change must preserve. |
| Behavior and data | Named inputs and outputs, types or schema locations, relevant validation and errors, state transitions, persistence timing, cancellation and retry behavior. Give a concrete example with exact values. |
| Screen implementation | Mockup states and callouts, visible labels, placement, navigation source and return, accessibility expectations, and empty, loading or error states. Cite any platform-owned UI and the accepted variation. |
| Verification | Fixture location and clock, commands and the directories to run them from, device profile, expected result and evidence path. Identify prior cases to rerun and how to verify the small-screen and large-text path. |
| Handoff | Expected changed files, review and QA report links, closeout criteria, and where a later task starts. |

Split a brief when it mixes unrelated changes or depends on an unverified architecture choice. Use a bounded experiment with concrete measurements to resolve toolchain or compatibility questions. Escalate a missing product or accounting decision with the chapter, requirement ID, and a concrete example. Record the decision in the spec before implementing the affected behavior.

Before assignment, the brief author verifies that its named files and symbols exist or are explicitly new, runs the documented setup and baseline commands on the prerequisite revision, and checks that each acceptance case can be reproduced from its stated inputs. Treat gaps in setup, mockups, or the contract as specification work for the brief author to resolve. If a requirement depends on a result from an earlier milestone, document the dependency in the draft and fill the concrete names and commands after that milestone passes. A coder who encounters an unresolved domain decision records the blocking requirement for the specification owner to resolve.

Use the [brief template](../../../implementation/briefs/template.md). The [I01 starting project](../../../implementation/briefs/I01-01-start-the-android-project.md) and [I05 Calendar](../../../implementation/briefs/I05-calendar.md) drafts show the expected detail.


## Required gates

| Gate | Pass criteria | Evidence to retain |
| --- | --- | --- |
| **Spec and traceability** | Every behavior changed or delivered links to the approved spec, a parity or requirement ID, and an acceptance check. Each affected phone screen and state links to the correct annotated mockup and layout variant. | Spec revision, brief and its verified starting revision, scope and case list. |
| **Build and functional behavior** | The candidate builds. Applicable automated tests, manual acceptance cases, and regressions pass on the intended build. Accounting outputs match the required fixture values, including dates, hours, IDs, forecasts, and warnings. Failures produce the required user-visible recovery. | Commands or CI run, fixture identity, device and result per case. |
| **GUI conformance and accessibility** | Each affected GUI screen and applicable state passes the mockup comparison. Labels, layout, actionable areas, navigation, text scaling, contrast, focus order, and screen-reader output meet the spec. | Annotated mockup link, captured UI, visual diff or annotated comparison, and accessibility results. |
| **Data and platform behavior** | Relevant storage, restore, share, PDF, upgrade and offline checks pass. Test read-only and cancelled operations for unchanged data, and test applicable write failures and restart. Use real receiving apps or a test receiver for OS handoffs. Apply required desktop GUI round trips when the milestone or release requires them. | Ledger hashes or JSON comparison, interruption points, receiver files, and platform/build matrix. |
| **Code review and retest** | Separate AI review covers the final diff and evidence. Findings that affect scope, correctness, layout, accessibility, data safety, or tests are resolved and verified. Material fixes have review and regression evidence on the final candidate. | Code review report, resolution for each finding, and targeted plus affected regression results. |
| **Completion and release evidence** | Applicable gates show Pass and the final revision is identifiable. Source changes sent to `master` also pass the repository's Distribution requirements. Release milestones satisfy the full handset and desktop GUI test matrix before production. | QA report and passdown, candidate commit/build identity, and required CI, Distribution or release evidence. |

A gate can be **Not applicable** when the documented scope and its dependencies exclude the check. Record the reason. A missing device, tool, fixture, or result is **Blocked** when it prevents an applicable test. A test that passes only after a retry needs an explanation and repeatable results before it supports a milestone pass. Completion reports use explicit Pass, Fail, Blocked, or Not applicable statuses with evidence.

## Compare every affected GUI to the mockups

Review the candidate on the compact and regular phone profiles required by the [test matrix](05-01-developer-setup-and-testing.md#test-devices-and-api-levels). Pin device profile, density, API, locale, font scale, theme, clock, fixture, and build for reference captures. Review the alternate font size and theme and native device handoffs where they matter. Validate interactions and semantics independently of screenshots. [Android screenshot testing](https://developer.android.com/training/testing/ui-tests/screenshot) · [Android accessibility testing](https://developer.android.com/guide/topics/ui/accessibility/testing)

1. Capture the actual screen and relevant state from the candidate. Put it beside the corresponding annotated mockup. Review required labels, control positions and hierarchy, spacing, alignment, typography, contrast, markers, progress and error messages, and the absence of clipping or overlap. Check the actions and results tied to each callout. Inspect the keyboard and scroll behavior on entry screens.
2. Compare approved visual decisions in the matched device state. For such states, keep a reviewed app screenshot as a regression reference after it has passed the mockup comparison. Run screenshot comparisons whenever the state or a shared component changes. Review diffs at readable scale. Mask predictable system-owned or time-dependent areas only where documented and test the remaining behavior separately.
3. Review responsive behavior on a compact or larger display and with increased font scale. Verify adaptable layouts preserve the same labels, data and available actions. For the system document picker and Sharesheet, verify the native entry point, content, MIME type, cancellation and return. Android or the receiving app controls the platform-owned chrome. The app's balance-date picker follows its annotated mockup and marker rules.
4. For PDF and report output, compare the actual file and rendered page against the matching reference with synthetic data and the same page settings. Check values, headings, layout, row completeness, page geometry and the received file. For Calendar, run the [desktop month PDF case](../01-calendar/01-05-menu-and-pdf.md#share-the-displayed-month).

### Change the spec explicitly

When an implementation would intentionally differ, record why, the impacted requirements and screens, and the proposed behavior. Get the project owner's approval. Update the governing chapter, annotated mockups, parity row, and acceptance cases together before accepting the difference. Recheck the actual candidate against that revised baseline and record its revision in the QA report. Changes to screenshot references require the same review and an evidence link. Replacing a reference screenshot by itself does not close a mismatch.

## Record a reviewable result

Use one QA report per milestone with a table of cases and gates. Each case records the requirement, test and screen IDs, expected result, observed result, status, and links to screenshots, logs, or files. Record every failed attempt and its fix with the final retest. Store synthetic evidence only. Keep sensitive security audit working material in the repository's ignored `untracked/security/` location. Keep the written review and its sanitized finding summary in the Android review tree. [QA report template](../../../qa/reports/template.md) · [AI code review template](../../../reviews/code/template.md)

Example for Calendar I05. The Jan 13 balance case expects 92 h annual and 20 h sick. If the image matches the mockup but the date heading reflects Jan 16, record a failure, fix the state binding, then rerun the dated balance, Today reset and date-picker cancellation cases along with the affected screenshots. Close I05 only when the candidate's final report demonstrates that all applicable gates passed.
