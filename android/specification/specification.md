# FedLeave Android specification

## Product and reference build

FedLeave for Android brings the desktop leave-accounting workflows in scope for the first release to a phone. The user can check a balance, inspect the calendar, and manage leave locally. The phone interface uses Android's Sharesheet for user-initiated sharing of reports, images, and compatible backups.

The approved [specification plan](planning/specification-plan.md) sets the scope and review milestones. Each parity row identifies the desktop behavior, the Android surface, and the acceptance case that will prove it.

| Desktop reference | Pinned identity |
| --- | --- |
| GitHub stable release | `beta`, build `0.2.2.dev0+g7041ce9e` |
| Source revision | `7041ce9e9921dd4c407595ed3269de1aca43e699` |
| Installer mirror revision | `80313ff` |
| Baseline check | Verified November 20, 2026 against the release record, tag, and installer build metadata. |

Use the pinned source to resolve differences between the older documentation and shipped behavior. Record a baseline change before updating results or parity expectations. [Stable release](https://github.com/joshua-guthrie/fedleave/releases/tag/beta) · [Pinned source](https://github.com/joshua-guthrie/fedleave/tree/7041ce9e9921dd4c407595ed3269de1aca43e699) · [Installer build identity](../../installers/BUILD.txt)

## Specification index

For a visual review, start with the [Calendar mockups](chapters/01-calendar/01-03-navigation-and-screens.md), [Reports mockups](chapters/03-reports-data/03-02-report-screens.md), or [Data and preferences mockups](chapters/03-reports-data/03-03-data-and-settings-screens.md).

Before coding, walk through the controls from Calendar to Reports, each analytics view, Settings and Help in the [menu navigation review](chapters/06-delivery/06-02-implementation-milestones.md#review-menu-navigation-before-i01).

**Calendar main screen selected.** Use a tappable balance card, a month selector and daily activity inside the day cells. [View the selected Calendar](chapters/01-calendar/01-01-main-screen.md)

| Chapter | Content |
| --- | --- |
| 01. Calendar | [01.01 Main screen](chapters/01-calendar/01-01-main-screen.md) · [01.02 Balances](chapters/01-calendar/01-02-balances.md) · [01.03 Navigation and screen states](chapters/01-calendar/01-03-navigation-and-screens.md) · [01.04 Date markers](chapters/01-calendar/01-04-date-markers.md) · [01.05 Menu and PDF](chapters/01-calendar/01-05-menu-and-pdf.md) · [01.06 Force Balance](chapters/01-calendar/01-06-force-balance.md) |
| 02. Ledger | [02.01 Ledger and leave years](chapters/02-ledger/02-01-ledger-and-leave-years.md) |
| 03. Reports, data, and preferences | [03.01 Behavior and requirements](chapters/03-reports-data/03-01-reports-data-and-preferences.md) · [03.02 Report screens](chapters/03-reports-data/03-02-report-screens.md) · [03.03 Data and settings screens](chapters/03-reports-data/03-03-data-and-settings-screens.md) |
| 04. Architecture | [04.01 Shared core and compatibility](chapters/04-architecture/04-01-core-and-compatibility.md) · [04.02 Device storage, backups, and sharing](chapters/04-architecture/04-02-device-storage.md) |
| 05. Quality | [05.01 Developer setup and testing](chapters/05-quality/05-01-developer-setup-and-testing.md) · [05.02 Iterative QA and completion gates](chapters/05-quality/05-02-quality-gates.md) |
| 06. Delivery | [06.01 Google Play](chapters/06-delivery/06-01-google-play.md) · [06.02 Implementation milestones and decisions](chapters/06-delivery/06-02-implementation-milestones.md) |
| 07. Organization | [07.01 Project organization](chapters/07-organization/07-01-project-organization.md) |

Use the [feature parity matrix](verification/feature-parity.md) to trace a desktop behavior to its Android requirement, screen and test. The [acceptance tests](verification/acceptance-tests.md) include the synthetic fixtures and exact results. [Implementation briefs](../implementation/briefs/README.md) map the approved chapters to executable coding assignments. [Mockup sources and images](artwork/mockups) · [Writing checkpoints](passdowns)

## Decisions carried into the first release

- Use Kotlin and Compose with Material 3 as the starting stack. Scope the first release using the [GitHub issue decisions](planning/specification-plan.md#github-issues-reflected-in-this-draft). The What-if Planner is deferred. Android's interface and help use app language, and MCP and AI are outside the Android scope.
- Keep the phone's working datastore in private, desktop-compatible JSON. A user opens the system file picker to restore a compatible FedLeave backup. Use the Sharesheet for backups and reports. Calendar Share automatically generates the desktop-equivalent month PDF and hands it off as an attachment. Printing is outside Android scope.
- Use one Calendar balance panel for today, any selected date, and a pay-period closing date. Show future balances as projections. The [Calendar section](chapters/01-calendar/01-02-balances.md) defines the first slice and the decisions that still need review.
