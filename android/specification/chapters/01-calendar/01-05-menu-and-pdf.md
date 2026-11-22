# Chapter 01.05 · Calendar menu and PDF sharing

Calendar uses the **Menu** icon at the upper left and the **Share calendar PDF** icon at the upper right. The balances card shows the balance date, and the calendar shows the month. Use the upper-right Share placement on report screens that have a shareable result. Give both icons 48 dp touch targets and spoken labels. [Full menu structure](01-07-menu-structure.md) · [Reports sharing](../03-reports-data/03-02-report-screens.md#month-report-and-share)

## Open the Calendar menu

![Calendar menu open showing Today, Transactions, and Force Balance](../../artwork/mockups/calendar-menus.png)

| Callout | Interaction | Requirements |
| --- | --- | --- |
| 1 | The Menu icon opens **Today**, **Transactions**, and **Force Balance**, in that order. Today resets the calendar month and balance date. Transactions opens history. Force Balance opens the effective date picker for a dated adjustment. | `CAL-MENU-01`, `CAL-MENU-02`, `CAL-BAL-05`, `CAL-HISTORY-01`, `CAL-FORCE-01` |

Anchor a Material 3 dropdown just below the upper-left Menu icon. Show a rounded, elevated surface about 248 dp wide at the reference phone size, with text-only rows left aligned in the order shown. Use at least 48 dp per row. Allow the width to fit localized labels and keep it within the available screen. The dropdown overlays Calendar in place. Tapping outside, pressing Back or reopening Menu dismisses it and restores focus to Menu. Selecting a command closes the dropdown before performing that action. Use the same layout and readable contrast in light and dark mode.

## Share the displayed month

![Calendar Share at upper right opens the Android Sharesheet with a month PDF attached](../../artwork/mockups/calendar-pdf-share.png)

| Callout | Interaction | Requirements |
| --- | --- | --- |
| 1 | Tap the upper-right Share icon on Calendar. Generate the desktop-equivalent PDF for the displayed month and open Android's Sharesheet directly. | `CAL-SHARE-01` |
| 2 | Share `fedleave-YYYY-MM.pdf` as an attachment. Android lists installed targets that accept PDFs, such as email or compatible messaging apps. The selected app receives the file for its own compose or transfer flow. Back returns to Calendar with its month and balance date intact. | `CAL-SHARE-01` |

Use the desktop **Save Month as PDF** report as the reference for the layout and values. Preserve the landscape page, month grid, leave activity and date markers, pay-period tables, **As of Today** balances, applicable year-end figures, and the complete printed abbreviations. The PDF keeps the desktop report legend. Report balances follow the desktop report's Today cutoff even when the main balance card is set to a different date. Compare rendered PDFs at a matched page size and with the same fixture and report clock. [Desktop PDF generation](../../../../src/fedleave_gui/main_window.py#L2234) · [Report renderer](../../../../src/fedleave_month_report_graphic/report.py#L440)

The standalone reference image shows the desktop app's **Save Month as PDF** result for the January 2026 Calendar fixture with Today fixed to January 16. The inspected sample is one landscape A4 page. [Full-size desktop PDF reference](../../artwork/mockups/calendar-desktop-pdf-reference.png) · [January fixture](../../verification/acceptance-tests.md#calendar-balance-fixture)

On each Share tap, use a read-only snapshot of the displayed month. Generate the PDF in private temporary storage and hand it to the native Sharesheet after rendering succeeds. Show progress during preparation. On failure stay on the launching screen, offer **Retry**, and do not open the Sharesheet with a partial file. FedLeave exposes no printing controls or print service calls. Reports can offer a format choice for shareable images and documents. [Artifact sharing](../04-architecture/04-02-device-storage.md#artifact-sharing-and-lifecycle)

Rebuild the menu and share mockups with `python android/specification/artwork/mockups/render.py android/specification/artwork/mockups/calendar-actions.json`.
