# Use or Lose Android specification

## Specification revisions

Each change updates the governing chapter, mockup annotation, parity row and acceptance case together, then bumps the version here so revisions can be compared.

| Version | Date | Change | Decision |
| --- | --- | --- | --- |
| 1.0 | November 23, 2026 | Approved baseline: menu review, Calendar main screen, implementation approval through I06. | Owner approvals through checkpoint 31 |
| 1.1 | November 24, 2026 | Calendar grid revision: the day cells fill the space between the month selector and the bottom bar with one uniform cell height computed per device; the grid keeps its base font size regardless of the system font scale; the full-width day list becomes a manual **Full list** mode opened by a small control above the bottom bar (the automatic large-text switch is removed; the automatic narrow-width switch stays); the grid scrolls instead of clipping when a row cannot fit. | Owner review, November 24, 2026 |
| 1.2 | November 25, 2026 | Product rename: the application is **Use or Lose** (spelled "Lose"). All user-visible branding changes from FedLeave: Android app label and screen wordmarks, the desktop windows and dialogs, the CLI banners, the PDF report title, and the mockup boards. Internal identifiers stay: the `fedleave` CLI command, the Python package name, the `com.westmouthbay.fedleave` app ID, the app-private data directory, and the `use-or-lose-YYYY-MM.pdf` share filename (updated from `fedleave-YYYY-MM.pdf` to match the renamed product). | Owner decision, November 25, 2026 |
| 1.3 | November 26, 2026 | Internal identifier rename: the CLI is `use-or-lose` (MCP `use-or-lose-mcp`), the Python package is `useorlose`, the Android application ID becomes `com.westmouthbay.useorlose`, the app-private data directory becomes `useorlose`, and desktop data directories and the install root drop the FedLeave name. The pinned desktop reference and fixture builder keep their historical `7041ce9e` source pin until the next parity pass re-verifies against v1.1.0. | Owner decision, November 26, 2026 |

## Product and reference build

Use or Lose for Android brings the desktop leave-accounting workflows in scope for the first release to a phone. The user can check a balance, inspect the calendar, and manage leave locally. The phone interface uses Android's Sharesheet for user-initiated sharing of reports, images, and compatible backups.

The approved [specification plan](planning/specification-plan.md) sets the scope and review milestones. Each parity row identifies the desktop behavior, the Android surface, and the acceptance case that will prove it.

| Desktop reference | Pinned identity |
| --- | --- |
| Current stable release | `v1.1.0` ("Use or Lose 1.1.0"), desktop only, November 26, 2026 |
| Fixture parity source | `7041ce9e9921dd4c407595ed3269de1aca43e699` (historical build the fixtures were verified against) |
| Baseline check | Verified November 20, 2026 against the release record, tag, and installer build metadata. |

Use the pinned fixture source to resolve differences between the older documentation and shipped behavior. Record a baseline change before updating results or parity expectations. The current fixtures stay pinned to the historical revision until the next parity pass re-verifies them against v1.0.0. [Stable release](https://github.com/joshua-guthrie/use-or-lose/releases/tag/v1.0.0) · [Pinned source](https://github.com/joshua-guthrie/use-or-lose/tree/7041ce9e9921dd4c407595ed3269de1aca43e699) · [Installer build identity](../../installers/BUILD.txt)

## Specification index

For a visual review, start with the [Calendar mockups](chapters/01-calendar/01-03-navigation-and-screens.md), [Reports mockups](chapters/03-reports-data/03-02-report-screens.md), or [Data and preferences mockups](chapters/03-reports-data/03-03-data-and-settings-screens.md).

**Menu review.** From Calendar, tap **Reports** in the bottom bar to reach every analytics view. Tap **Settings** for preferences and Help. The [dedicated menu structure chapter](chapters/01-calendar/01-07-menu-structure.md) shows the complete lists, exact tap paths, and where Back returns.

**Calendar main screen selected.** Use a tappable balance card, a month selector and daily activity inside the day cells. [View the selected Calendar](chapters/01-calendar/01-01-main-screen.md)

**Development workflow.** Start with the [orchestration specification](orchestration.md) to find the rules for design handoffs, coding delegation, independent review and the offline pilot.

| Chapter | Content |
| --- | --- |
| 01. Calendar and navigation | [01.01 Main screen](chapters/01-calendar/01-01-main-screen.md) · [01.02 Balances](chapters/01-calendar/01-02-balances.md) · [01.03 Navigation and screen states](chapters/01-calendar/01-03-navigation-and-screens.md) · [01.04 Date markers](chapters/01-calendar/01-04-date-markers.md) · [01.05 Calendar menu and PDF sharing](chapters/01-calendar/01-05-menu-and-pdf.md) · [01.06 Force Balance](chapters/01-calendar/01-06-force-balance.md) · [01.07 Menu structure and tap paths](chapters/01-calendar/01-07-menu-structure.md) |
| 02. Ledger | [02.01 Ledger and leave years](chapters/02-ledger/02-01-ledger-and-leave-years.md) · [02.02 Prior-year change detection *(proposal)*](chapters/02-ledger/02-02-prior-year-change-detection.md) |
| 03. Reports, data, and preferences | [03.01 Behavior and requirements](chapters/03-reports-data/03-01-reports-data-and-preferences.md) · [03.02 Report screens](chapters/03-reports-data/03-02-report-screens.md) · [03.03 Data and settings screens](chapters/03-reports-data/03-03-data-and-settings-screens.md) |
| 04. Architecture | [04.01 Shared core and compatibility](chapters/04-architecture/04-01-core-and-compatibility.md) · [04.02 Device storage, backups, and sharing](chapters/04-architecture/04-02-device-storage.md) |
| 05. Quality | [05.01 Developer setup and testing](chapters/05-quality/05-01-developer-setup-and-testing.md) · [05.02 Iterative QA and completion gates](chapters/05-quality/05-02-quality-gates.md) · [05.03 Orchestrator verification](chapters/05-quality/05-03-orchestrator-verification.md) |
| 06. Delivery | [06.01 Google Play](chapters/06-delivery/06-01-google-play.md) · [06.02 Implementation milestones and decisions](chapters/06-delivery/06-02-implementation-milestones.md) · [06.03 Orchestrator pilot milestones](chapters/06-delivery/06-03-orchestrator-pilot-milestones.md) |
| 07. Organization | [07.01 Project organization](chapters/07-organization/07-01-project-organization.md) · [07.02 Multi-model development workflow](chapters/07-organization/07-02-multi-model-development-workflow.md) · [07.03 Design and implementation handoffs](chapters/07-organization/07-03-design-and-implementation-handoffs.md) · [07.04 Orchestrator execution](chapters/07-organization/07-04-orchestrator-execution.md) |

Use the [feature parity matrix](verification/feature-parity.md) to trace a desktop behavior to its Android requirement, screen and test. The [acceptance tests](verification/acceptance-tests.md) include the synthetic fixtures and exact results. [Implementation briefs](../implementation/briefs/README.md) map the approved chapters to executable coding assignments. [Mockup sources and images](artwork/mockups) · [Writing checkpoints](passdowns)

## Decisions carried into the first release

- Use Kotlin and Compose with Material 3 as the starting stack. Scope the first release using the [GitHub issue decisions](planning/specification-plan.md#github-issues-reflected-in-this-draft). The What-if Planner is deferred. Android's interface and help use app language, and MCP and AI are outside the Android scope.
- Keep the phone's working datastore in private, desktop-compatible JSON. A user opens the system file picker to restore a compatible Use or Lose backup. Use the Sharesheet for backups and reports. Calendar Share automatically generates the desktop-equivalent month PDF and hands it off as an attachment. Printing is outside Android scope.
- Use one Calendar balance panel for today, any selected date, and a pay-period closing date. Show future balances as projections. The [Calendar section](chapters/01-calendar/01-02-balances.md) defines the first slice and the decisions that still need review.
- Present effective leave entries the same way on every date. Use the date, leave type, direction and hours in Calendar, forms, transaction history, report tables, charts, shared graphics and TalkBack. Retain compatible stored statuses for accounting and interoperability. [Dated entry rules](chapters/02-ledger/02-01-ledger-and-leave-years.md#edit-and-review-leave)
