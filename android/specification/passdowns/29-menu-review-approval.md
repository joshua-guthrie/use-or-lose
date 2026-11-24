# Checkpoint 29 · Menu review approval

## Completed

The owner reviewed the [menu structure and tap paths](../chapters/01-calendar/01-07-menu-structure.md) on November 23, 2026 and approved it as written. The approved structure keeps the **Calendar · Reports · Settings** bottom bar, the **Today**, **Transactions**, **Force Balance** dropdown, the separate upper-right **Share calendar PDF** control, tap-a-day **Edit day**, the eight Reports rows, and the five nested Settings sections and six Help articles.

The owner also approved retaining the desktop terms **Force Balance** and **Overtime, comp and credit**, keeping Share out of the dropdown, and the Settings nesting as specified. No label, route, or Back-behavior changes were requested. This satisfies the [menu walkthrough gate](../chapters/06-delivery/06-02-implementation-milestones.md#review-menu-navigation-before-i01).

## Verification

The review covered every row of Chapter 01.07 against its mockup, the referenced acceptance cases `T-CAL-MENU-01`, `T-CAL-MENU-02`, and `T-NAV-MENU-01`, and the state-retention and Back rules in [Navigation behavior for review](../chapters/01-calendar/01-07-menu-structure.md#navigation-behavior-for-review). The starting source identity was `e9d02b1473e840467b1a3390ad07b87727adbdc8`. The review changed documentation only; Android code has not started.

## Where to resume

The remaining [specification review checklist](../chapters/06-delivery/06-02-implementation-milestones.md#specification-review-checklist) item for product work is the owner's approval to start implementation. I01 remains blocked until that approval. Before coding proceeds, write the missing I02–I04 briefs and runnable reference fixtures; only I01-01, I05, and I06-02 exist. [Milestone inputs](../../implementation/briefs/milestone-inputs.md) · [I01 startup brief](../../implementation/briefs/I01-01-start-the-android-project.md)

For the separate orchestrator track, prepare the OR01 pilot brief, pin Python and Git, and choose the Linux isolation backend. [OR01](../chapters/06-delivery/06-03-orchestrator-pilot-milestones.md#or01-offline-mock-pilot)
