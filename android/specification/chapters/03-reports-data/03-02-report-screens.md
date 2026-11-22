# Chapter 03.02 · Report screens and sharing

Reports is a phone destination with a year selector and report list. Keep interactive chart controls beside the result they affect. Use the upper-right Share icon when the report has a shareable result. Open long tables and supporting entries as details. Read aloud the chart title, units, timeframe and selected value. Pair colors with labels or visual marks. System picker and Sharesheet UIs vary by phone.

[Report requirements](03-01-reports-data-and-preferences.md#reports) · [Acceptance cases](../../verification/acceptance-tests.md#reports-acceptance)

## Reports list and summary

![Reports catalog, summary, and unavailable state with callouts](../../artwork/mockups/reports-home.png)

| Callout | Interaction and states | Requirements |
| --- | --- | --- |
| 1–2 | Choose a valid leave year, see its date range in each report, and open a report without changing Calendar's balance date. The phone list is scrollable at large text size. | `REP-SUM-01`, `PREF-YEAR-01` |
| 3–4 | Show recorded, scheduled, and full-year results where applicable. Link data-quality warnings to details. Refresh uses current local data. Show unreadability or incomplete history without inventing a numeric zero. | `REP-SUM-01` |

## Activity breakdowns and supporting entries

![Seasonality with analysis and leave type, selector, contributing transactions, and one lifecycle report](../../artwork/mockups/reports-breakdowns.png)

| Callout | Interaction and states | Requirements |
| --- | --- | --- |
| 1–3 | Analysis and Leave type drive the chart, exact table, and shared result. Filter sheets list supported category and activity choices. Disabled Leave type remains visible for overtime and net accumulation. Chart and row values identify recorded and scheduled amounts. | `REP-SEA-01`, `REP-SHARE-01` |
| 4 | Open **View entries** with date, source, status and hours matching the selected report row or cell. Back retains the original report filters and scroll position. | `REP-SEA-01`, `REP-HEAT-01`, `REP-LIFE-01`, `REP-CUM-01` |
| 5–6 | Use this selector layout for the seven lifecycle views. Keep tables handy for the summary, lots, allocation and expiration performance. Empty or incomplete queries keep the view label and either explain the empty set or show unavailable values. | `REP-LIFE-01` |

## Heatmap, usage, and balance charts

![Monthly heatmap, cumulative usage, period-end balance chart, and year-to-year comparison](../../artwork/mockups/reports-charts.png)

| Callout | Interaction and states | Requirements |
| --- | --- | --- |
| 1–2 | Heatmap offers monthly grids and the year-at-a-glance strip. Tap the chart to expand it, then select a cell to read its exact date and value. Keep the exact rows accessible from the report. | `REP-HEAT-01` |
| 3 | List or select multiple absence categories. View exact cumulative daily entries. Preserve the whole-year extent and show the time context. | `REP-CUM-01` |
| 4 | Choose from the six balance series. A period point label includes its end date and balance. The exact table is navigable independently of the chart. | `REP-CHART-01` |
| 5–6 | Keep the selected month and day visible and list the date actually mapped into each comparison year. Provide the seven supported category choices. | `REP-YOY-01` |

![Year-at-a-glance heatmap, an empty report, and a calculation with cancel](../../artwork/mockups/reports-heatmap-states.png)

| Callout | Interaction and states | Requirements |
| --- | --- | --- |
| 1–2 | The full-year strip scrolls horizontally on compact phones with a spoken week and date. The example shows its first viewport. Both layouts provide the same data and details. | `REP-HEAT-01` |
| 3–4 | Distinguish no activity from a read problem and offer another year. Cancel a pending calculation without clearing the selected categories. Older results cannot overwrite the current selection. | `REP-SUM-01`, `REP-HEAT-01`, `REP-CUM-01` |

## Full-screen charts

![Portrait Seasonality report with a tappable chart and an independent action for its exact values](../../artwork/mockups/reports-chart-expand-entry.png)

| Callout | Interaction and states | Requirements |
| --- | --- | --- |
| 1–2 | Tap anywhere on the chart or its expand icon. The whole chart is one control and TalkBack names it **Expand chart**, followed by the chart title. **View exact values** opens the report data from the portrait screen. | `REP-EXPAND-01` |

![Seasonality fills the landscape screen. The Back arrow is at upper left, and the selected bar's exact value and entries are available below the chart.](../../artwork/mockups/reports-chart-fullscreen.png)

![The monthly heatmap in landscape with a selected date and its exact value](../../artwork/mockups/reports-heatmap-fullscreen.png)

| Callout | Interaction and states | Requirements |
| --- | --- | --- |
| 1 | The upper-left **Back** arrow returns to the initiating report. System Back follows the same path. Restore its previous orientation policy, scroll position and focus on the chart. | `REP-EXPAND-01` |
| 2 | Redraw the chart across the landscape app window below the title. Keep the timeframe, units, labels, selected series and filters. The expanded chart replaces bottom navigation while open. | `REP-EXPAND-01` |
| 3 | Select a bar, point, or heatmap cell in the expanded chart to read its exact value. **View entries** opens matching source rows where the report supports them. Back from the rows returns to the chart and its selected point. | `REP-EXPAND-01`, `REP-HEAT-01`, `REP-SEA-01` |

Use the same expansion action for Seasonality, monthly and year-at-a-glance Heatmaps, lifecycle plots, Cumulative Usage, balance charts and yearly comparison. A successful plotted result enables the action. Loading or unavailable states keep their existing message and recovery. Reuse the current result identity and selection in the expanded route. Rotation, Back and window resizing must keep the view's state and must not reset filters or issue duplicate computations. The chart is read-only.

The expanded route receives the initiating report's result key, selected chart and point key, and the return scroll anchor. Keep the result in the report state holder across configuration changes. The expanded route requests landscape by applying `SCREEN_ORIENTATION_SENSOR_LANDSCAPE` while it owns the chart screen. Save the prior requested orientation, then restore it when leaving for the report. Source entries opened from the chart keep the chart route and selection on the back stack.

On a supported full-screen phone, request landscape for the lifetime of the expanded route, so a portrait report turns 90° when opened. Fill the available app window on either landscape side and keep Android system Back and safe insets accessible. On a device or window that ignores the request, lay out the same full-screen view in the available orientation. Preserve the Back arrow, chart and exact values in that fallback. [Android orientation request](https://developer.android.com/reference/android/app/Activity#setRequestedOrientation(int)) · [Android adaptive layouts](https://developer.android.com/about/versions/16/behavior-changes-16#adaptive-layouts)

Rebuild the expanded chart boards with `python android/specification/artwork/mockups/render.py android/specification/artwork/mockups/reports-expanded.json`.

## Month preview and Share

![Month report preview, format chooser, and Android Sharesheet](../../artwork/mockups/reports-share.png)

| Callout | Interaction and states | Requirements |
| --- | --- | --- |
| 1–2 | Show a zoomable preview of the desktop month report and put Share in the upper right. The month report can offer PDF and PNG. The Calendar main-screen shortcut shares the month PDF directly. [Calendar PDF flow](../01-calendar/01-05-menu-and-pdf.md#share-the-displayed-month) | `REP-MONTH-01`, `CAL-SHARE-01` |
| 3–4 | Choose a supported format and hand off the rendered report to Android. Report preparation failure leaves its preview open and offers Retry. Back or cancellation returns to the report and its current filters. | `REP-SHARE-01`, `REP-MONTH-01` |

Rebuild the images with `python android/specification/artwork/mockups/render.py android/specification/artwork/mockups/reports.json`. The chart examples are fictional records and fixed-date specification fixtures.
