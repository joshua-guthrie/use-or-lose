# Calendar menu and PDF sharing

Both Calendar layouts use the **Menu** icon at the upper left and the **Share calendar PDF** icon at the upper right. The balances card shows the balance date, and the calendar shows the month. Use the upper-right Share placement on report screens that have a shareable result. Give both icons 48 dp touch targets and spoken labels. [Calendar layout options](calendar-layout-options.md) · [Reports sharing](report-screens.md#month-preview-share-and-print)

## Menu in both layouts

![Menu opened in both Calendar layouts showing Today, Calendar legend, Transactions, and Preview month PDF](../mockups/calendar-menus.png)

| Callout | Interaction | Requirements |
| --- | --- | --- |
| 1–2 | The Menu icon opens the same commands in Option A and Option B. **Today** resets the calendar month and balance date. **Calendar legend** opens an explanation for the active layout. **Transactions** opens history. **Preview month PDF** opens the displayed month's PDF for inspection or printing. | `CAL-MENU-01`, `CAL-BAL-05`, `CAL-HISTORY-01` |

![Option A's schedule-marker legend and Option B's schedule and leave activity legend](../mockups/calendar-legends.png)

The Calendar grid uses the full space available beneath the balances and month controls. The legend is available from the menu and closes back to the same month and balance date. For Option B, it explains category abbreviations, signed hours, planned entries, and counts for additional entries. Include the codes used in the current grid. TalkBack announces the markers and entry meanings when someone selects a day.

## Share the displayed month

![Desktop month PDF in the phone preview and a PDF file in the Android Sharesheet](../mockups/calendar-pdf-share.png)

| Callout | Interaction | Requirements |
| --- | --- | --- |
| 1 | **Menu > Preview month PDF** opens the landscape page. The preview supports zoom and rotation. **Print** hands the same page to the platform print service. | `REP-MONTH-01`, `CAL-MENU-01` |
| 2 | Tap the upper-right Share icon on Calendar or its PDF preview. Prepare the PDF for the displayed month and open Android's Sharesheet directly. | `CAL-SHARE-01` |
| 3 | Android shows `fedleave-YYYY-MM.pdf` to share. The user chooses the receiving app. Back returns to the screen that launched Share with its month and balance date intact. | `CAL-SHARE-01` |

Use the desktop **Save Month as PDF** report as the reference for the layout and values. Preserve the landscape page, month grid, leave activity and date markers, pay-period tables, **As of Today** balances, applicable year-end figures, and the complete printed abbreviations. The phone menu legend affects the main screen. The PDF keeps the desktop report legend. Report balances follow the desktop report's Today cutoff even when the main balance card is set to a different date. Compare rendered PDFs at a matched page size and with the same fixture and report clock. [Desktop PDF generation](../../../src/fedleave_gui/main_window.py#L2234) · [Report renderer](../../../src/fedleave_month_report_graphic/report.py#L440)

The image in the mockup is a page rendered from the desktop app's **Save Month as PDF** path using the January 2026 Calendar fixture with Today fixed to January 16. The inspected sample is one landscape A4 page. Tap the reference for the full-size view. [Full-size desktop PDF reference](../mockups/calendar-desktop-pdf-reference.png) · [January fixture](../testPlan.md#calendar-balance-fixture)

On Share, use a read-only snapshot of the requested month. A temporary PDF with MIME type `application/pdf` goes to Android's Sharesheet using the existing artifact lifecycle. Show progress during preparation and offer Retry after failure. Share from a Reports detail may offer PNG or PDF if both are supported. [Artifact sharing](device-storage.md#artifact-sharing-and-lifecycle)

Rebuild the menu and share mockups with `python android/specficaiton/mockups/render.py android/specficaiton/mockups/calendar-actions.json`. The renderer uses the full-size desktop reference image above.
