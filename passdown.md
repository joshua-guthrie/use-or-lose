# FedLeave Android planning passdown

## Where to resume

Start with the [menu structure and tap paths](android/specification/chapters/01-calendar/01-07-menu-structure.md). Review this draft with the owner before approval to begin I01. The next specification session also owes the I02–I04 implementation briefs and reference fixtures. [Current checkpoint](android/specification/passdowns/27-menu-structure-and-dated-entries.md) · [Specification index](android/specification/specification.md) · [I01 startup brief](android/implementation/briefs/I01-01-start-the-android-project.md)

The owner approved scoping **Edit Day** to one selected group, with individual correction for incompatible rows. The updated rule, mockup and acceptance case are ready for the I08 brief. Android Restore Merge depends on the shared planner in GitHub #152. The desktop CLI regressions mark the four known failure cases as strict expected failures until the fix lands. [Decision and regression checkpoint](android/specification/passdowns/26-edit-day-and-merge-regression.md) · [Shared import issue](https://github.com/joshua-guthrie/fedleave/issues/152)

The owner selected the [Calendar main screen](android/specification/chapters/01-calendar/01-01-main-screen.md). The balance card previews eight values and opens a scrollable list with date controls. QA evidence for this path is required in I05. [Calendar balances passdown](android/specification/passdowns/16-calendar-balances.md)

The phone Calendar uses four [date markers](android/specification/chapters/01-calendar/01-04-date-markers.md), with full descriptions in day details and TalkBack. The date-marker chapter shows the actual-size glyphs and Settings Help contains their offline legend. [Marker artwork and Help checkpoint](android/specification/passdowns/24-calendar-marker-help.md) The desktop-equivalent shared PDF keeps its report legend. Tapping Calendar Share generates the PDF and passes it as an attachment to Android’s Sharesheet. Printing is outside Android scope. [PDF attachment checkpoint](android/specification/passdowns/19-pdf-attachment-sharing.md)

During new independent leave-year setup, choose balances at the start of the year or exact balances as of Today. The Today choice starts the preceding days in that year at zero and uses the normal accrual schedule going forward. [Today setup checkpoint](android/specification/passdowns/20-today-balances.md)

Analytics charts open a [full-screen landscape view](android/specification/chapters/03-reports-data/03-02-report-screens.md#full-screen-charts) when tapped. Back restores the previous report and its state. [Expanded chart checkpoint](android/specification/passdowns/21-full-screen-charts.md)

The [Analytics history mockups](android/specification/chapters/03-reports-data/03-02-report-screens.md#analytics-with-one-six-or-eleven-months) show one, six, and eleven months in Seasonality and Cumulative Usage. The [full-year Heatmap](android/specification/chapters/03-reports-data/03-02-report-screens.md#entire-leave-year-in-the-heatmap) shows every date at once in landscape and all months in a scrollable portrait view. [History checkpoint](android/specification/passdowns/22-analytics-history-mockups.md)

The Calendar menu offers [Force Balance](android/specification/chapters/01-calendar/01-06-force-balance.md). I10 adds the date, leave type, target and adjustment review to the shared maintenance workflow. [Force Balance checkpoint](android/specification/passdowns/23-force-balance-menu.md)

Close implementation milestones through the [QA gates and retest loop](android/specification/chapters/05-quality/05-02-quality-gates.md). Use the [QA report template](android/qa/reports/template.md) and link the separate AI review.

[Organization checkpoint](android/specification/passdowns/18-project-organization.md) records the chapter moves, review destinations, and smaller-model implementation briefs.

## Decisions to carry forward

- The version-controlled project root is `android/`. The specification, implementation briefs, QA reports, AI code reviews, and deliverables have defined locations. [Project organization](android/specification/chapters/07-organization/07-01-project-organization.md)
- The phone spec will account for each desktop capability and link every phone workflow to annotated mockups and acceptance tests. Kotlin, Compose, and Android Studio are the recommended starting point. The existing desktop CLI stays available through its callable core.
- Android Calendar should have one **Balances** panel. It opens at **As of today**. A date picker marks pay-period ends, and selecting a date updates the same panel. Show future dates as projections and include expandable earned and used details at pay-period close.
- Prompt from the task when Today or a deliberate future request needs a missing leave year. Preview before creating, resolve by stored dates, and suggest carryover from an eligible adjacent source. [Draft ledger requirements](android/specification/chapters/02-ledger/02-01-ledger-and-leave-years.md)
- Keep the desktop-compatible JSON store on the phone. **Restore backup** is the sole inbound file workflow. Use the Android Sharesheet for supported reports, images, and existing-format backups.
- Treat 30% remaining in the five-hour window as the cutoff. Record a passdown and make a local commit at each specification milestone. Split large milestones by screen or behavior, and record a final checkpoint if the window ends mid-milestone.

## Previous plan publication

- Commit `79d782e` added the initial plan and established `untracked/security/` as the ignored local location for the consolidated report, findings, evidence, and review working files. The formerly tracked report remains in older Git history.
- The initial commit passed CI and Distribution and produced installer commit `12c2812`. GitHub served it from the latest stable release. [Initial Distribution](https://github.com/joshua-guthrie/fedleave/actions/runs/44658705158)
- Commit `7041ce9` added the balance-panel workflow. CI and Distribution passed. The latest stable release points to that source commit, identifies the build as `0.2.2.dev0+g7041ce9e`, and has five verified assets matching installer commit `80313ff`. Local `master` is synced. [Follow-up CI](https://github.com/joshua-guthrie/fedleave/actions/runs/44660654466) · [Follow-up Distribution](https://github.com/joshua-guthrie/fedleave/actions/runs/44660654707) · [Stable release](https://github.com/joshua-guthrie/fedleave/releases/latest)
- The usage check before final release verification showed 23% remaining in the five-hour window. The plan and its publication are complete.
