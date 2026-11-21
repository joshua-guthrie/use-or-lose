# Passdown 15 — Iterative QA and milestone completion

November 21, 2026. Added a mandatory build, test, visual comparison, AI review, fix and retest loop. A milestone remains open with any required gate failed or blocked. A design change requires an approved spec revision and matching updates to its mockups and acceptance cases.

- Use the [QA gates](../chapters/05-quality/05-02-quality-gates.md) and [QA report template](../../qa/reports/template.md) from every implementation milestone. Match actual captures against each affected annotated screen and state.
- Give a smaller coding model a [self-contained brief](../chapters/05-quality/05-02-quality-gates.md#make-each-milestone-executable-from-a-brief) with file boundaries, exact requirements and failure states, test commands, expected values, and evidence paths.
- Desktop release verification remains required for a source push to `master`. Android device and visual checks apply to the implementation candidate once it exists.
- The next spec change expands the selected Calendar balance card and its full balance view.
