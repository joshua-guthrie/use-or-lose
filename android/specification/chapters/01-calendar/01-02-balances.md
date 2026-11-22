# Chapter 01.02 · Calendar balances

## User outcome

Calendar gives the user one **Balances** panel. They can see their position today, look back to an ordinary date, or look ahead to a pay-period closing date in the same place. Pay-period activity remains available beside the balance it explains. [Parity matrix](../../verification/feature-parity.md) · [Acceptance cases](../../verification/acceptance-tests.md#calendar-acceptance-cases)

[Selected Calendar and balance flow](01-01-main-screen.md) · [Calendar menu and PDF](01-05-menu-and-pdf.md)

## Requirements

| ID | Android behavior |
| --- | --- |
| `CAL-BAL-01` | A fresh open of Calendar starts on the device's current local date. The panel reads **As of today** and shows the date and balances in hours. Follow the device's local date while Today is active. |
| `CAL-BAL-02` | Tap anywhere on the balance card to open **Balances & date** at the current cutoff. **Choose date** opens the picker. Confirmation updates the full list and card together. Dismissing the picker preserves the prior date, amounts and list position. |
| `CAL-BAL-03` | Use the flag from the [date marker table](01-04-date-markers.md) for a pay-period end in the month grid and picker. Announce **Pay-period end** in the date's accessible label. Provide nearby period ends as quick choices with the date and period number. A selected end date shows that day's closing balance in the same panel. Period details provide the number, start and end dates, payday, and earned and used hours by category. Period activity opens from the full balance view. |
| `CAL-BAL-04` | For a date later than today, show **Projected balance** and the exact selected date. Use the calculation for that day, with effective leave entries and accrued hours through that date. |
| `CAL-BAL-05` | **Menu > Today** restores the current month, active calendar day, and balance date. A fresh open also starts on today after a previous manual date choice. |
| `CAL-BAL-06` | Keep a balance result paired with the date it covers. If a requested date cannot be loaded, show it with an unavailable state and a retry or date-change action. Carry forward applicable warnings and show an unavailable category value as unavailable. A slow older request must never replace the result for the current selection. |
| `CAL-BAL-07` | Use the stored leave-year start and end dates to resolve the selected day. A January date can belong to the preceding leave year. Prompt from the current task when an intentional date request needs a missing current or future year. [Leave-year setup](../02-ledger/02-01-ledger-and-leave-years.md#start-or-find-a-leave-year) |
| `CAL-NAV-01` | Browsing another month or selecting a calendar day for inspection and editing keeps the balance date selected in the panel. Confirming a balance date in the picker or using **Today** changes the balance cutoff. |
| `CAL-BAL-08` | Show up to eight actual values in two rows of four at the reference phone size and default text scale. With hidden amounts, report how many are hidden. The full view scrolls through every balance at the same cutoff and also gives access to the date picker. |

## Preview and full list

- Build both views from one dated result. Show one row per category. Order annual and sick leave first when reported, followed by comp, credit, travel comp, time-off award, restored annual, religious comp, PPL and FMLA remaining. Put other supported balance categories afterward in the desktop category order, then by stable category key if the source supplies no order. Usage-only categories stay in daily activity and reports. Do not sum overlapping parental or FMLA windows in the UI. Use the core's remaining-hours fields.
- Include a reported annual or sick zero, any additional nonzero balance, and any reported unavailable value or tracked remaining-hours field. Keep a zero when it represents an active tracked limit such as an exhausted PPL grant. Label a reported missing value **Unavailable**. Preserve negative amounts and exact fractional values. Leave omitted categories out. Use full labels in the list and for accessibility.
- At the reference width and default text scale, render the first eight in row-major order. If fewer values fit legibly at the user's width or text scale, use fewer columns and preview values. Keep the first values in the same order, compute the remainder from the actual visible count, and never shrink or clip the amount. With no hidden values the action still opens the full list and date controls.
- Compute the hint as **View all {total} balances · {hidden} more** when `hidden > 0`. Keep it part of the single button, with a localized quantity and a 48 dp minimum target. Scroll the full list vertically at any text scale. Keep date controls reachable with large text. Back returns to the same month, with focus on the card. Cancel in the picker returns to the full view without changing the cutoff.

## Nearby closing-date shortcuts

- On entering **Choose date**, use the committed balance cutoff as the shortcut anchor. Show the first two known pay-period ends on or after that anchor, ordered by date. Include the date and stored period number. When navigating the picker to another month, use the first day of that month as the new anchor.
- Selecting a day or shortcut changes only the pending choice. A shortcut outside the visible month moves the picker to that date. Keep the shortcut list stable while making a pending choice. Confirm applies the selected cutoff. Cancel discards the pending choice and picker month.
- Show one shortcut when only one known end qualifies. Hide the shortcut group when there are no matching stored end dates. The picker still allows ordinary dates. For the January fixture with the date anchored on January 16, list period 1 on January 24 and period 2 on February 7.

## Data and calculation rules

- Resolve dates as calendar dates in the device's time zone. The balance cutoff includes activity dated on that day. Use the desktop opening balances, effective transaction rules, accrual dates, and expiration rules. Existing-year views calculate from the local store without adding, changing, or deleting ledger entries. [Desktop dated balance calculation](../../../../src/fedleave/commands/balances.py#L271) · [Ledger balance rules](../../../../src/fedleave/ledger.py#L186)
- Use the stored pay-period ending date as the balance cutoff for a closing balance. Pay date and accrual date are separate schedule fields. Closing balance equals the dated balance for that end date when calculated from the same data. Period earned and used totals cover the whole period and follow the desktop activity rules. [Month period calculations](../../../../src/fedleave/commands/balances.py#L144) · [Schedule fields](../../../../src/fedleave/payperiods.py#L43)
- A future dated balance uses the same result as a desktop lookup for that date. Label year-end **Use or Lose** with its own forecast horizon. Link from relevant balances to the lot and parental details. [Forecast and expiration requirements](../02-ledger/02-01-ledger-and-leave-years.md#accrual-caps-and-expiration) · [Parental requirements](../02-ledger/02-01-ledger-and-leave-years.md#paid-parental-leave-and-fmla) · [Desktop dated and projected month fields](../../../../src/fedleave/commands/balances.py#L655)

## Screen states

| Screen or state ID | Key visual or interaction |
| --- | --- |
| `CALENDAR-MONTH` | Current month, a month selector, tappable days, daily leave, and schedule markers. |
| `BALANCES-TODAY` | A tappable preview with today's date and up to eight exact values. The main-screen menu contains **Today**. |
| `BALANCES-ALL` | Full scrollable list with the current cutoff, precise amounts and **Choose date**. |
| `BALANCE-DATE-PICKER` | Date grid with the current selection, ordinary days, marked pay-period ends, nearby period shortcuts, and cancellation. |
| `BALANCES-DATED` | Selected historical date, balances and warning area, with the same controls as Today. |
| `BALANCES-PERIOD` | Closing date and the same balance values, plus the collapsed and expanded period activity. |
| `BALANCES-PROJECTED` | The projected label and exact future date, including how it combines with an end-of-period selection. |
| `BALANCES-UNAVAILABLE` | Requested date, missing data or read error, and recovery actions. |

## Decisions for later milestones

- Annotate the selected date and return path for missing-year setup opened from a future balance lookup or edit. [Leave-year setup](../02-ledger/02-01-ledger-and-leave-years.md#start-or-find-a-leave-year)
