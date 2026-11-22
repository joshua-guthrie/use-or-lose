# I05 · Calendar navigation and dated balances

**Handoff draft.** Bind separate tasks to the I04 candidate and the approved specification revision before assigning code. Read the I01 project/build record, I03 read-only contracts, I04 private-store and fixture entry, then the [Calendar main screen](../../specification/chapters/01-calendar/01-01-main-screen.md), [balance requirements](../../specification/chapters/01-calendar/01-02-balances.md), [date markers](../../specification/chapters/01-calendar/01-04-date-markers.md) and [menu](../../specification/chapters/01-calendar/01-05-menu-and-pdf.md). The [acceptance table](../../specification/verification/acceptance-tests.md#calendar-acceptance-cases) is the pass condition.

## Small assignments and file ownership

| Task | Expected code under I01's app package | Scope and handoff |
| --- | --- | --- |
| I05-01 State and query wiring | `calendar/CalendarViewModel.kt`, `calendar/CalendarUiState.kt`, JVM tests in `app/src/test/` | Deliver the state machine and bridge binding from the actual I03/I04 adapter. The next task consumes only the typed state and actions. |
| I05-02 Calendar, navigation, and markers | `calendar/CalendarScreen.kt`, `calendar/CalendarDateMarkers.kt`, device tests in `app/src/androidTest/` | Deliver the real Calendar destination, menu, month selection, responsive grid/list, and read-only day detail. The next task adds the balance route and picker. |
| I05-03 Balance detail and date selection | `calendar/BalanceScreen.kt`, `calendar/BalanceDatePicker.kt`, JVM and device tests | Connect the card, all balances, period activity, nearby ends, and date selection to I05-01. Run the combined Calendar suite. |

These are proposed relative filenames. Before assignment replace them with the actual package paths and identify the symbols for the I01 navigation host, I03 queries, and I04 repository and injection hook. Update only the Calendar feature, its route registration, touched string resources, test fixtures and tests. Reuse I04's app-private reads and the shared core. I05 provides a read-only day route with date, events and activity. I08 makes that route the direct **Edit day** screen for the tapped date. I09 connects Transactions to its real destination. I10 connects **Force Balance** to the shared maintenance route. I15 connects the top-right Share action. In interim builds expose actions only once their destinations are wired. Record those milestone-specific gaps in the mockup comparison; I05 gates on `T-CAL-MENU-01` with Today. I09 tests the Transactions entry points. I10 tests Force Balance and the complete `T-CAL-MENU-02`.

## State and event contract

Represent dates as local `LocalDate` values in Kotlin and ISO `YYYY-MM-DD` at the bridge. Keep `displayMonth`, committed `balanceDate`, `followsToday`, selected day route and transient `pickerDraft` distinct. The Calendar ViewModel owns state across rotation and route return. Core results are read-only. All amounts come from the core's dated snapshot. Keep category keys and exact hour values and use a typed unavailable value. UI sorting, visibility, and formatting follow the [balance rules](../../specification/chapters/01-calendar/01-02-balances.md#preview-and-full-list).

| Trigger | State change and visible result |
| --- | --- |
| Fresh Calendar launch | Read the injected local day, show its month and set the balance cutoff to it with Today following enabled. Launch separate month and balance reads on the established adapter. |
| Local date advances | If Today is active, update its cutoff and refresh. Preserve a browsed month. An explicit confirmed cutoff stays fixed when the clock changes. |
| Select month or open day | Update only the month and day-related state. The balance date and result stay paired. Back restores month, scroll context and focus. |
| Tap balance card | Open **Balances & date** at the committed cutoff. Scroll through all balances and reveal collapsed period activity for an end date. |
| Choose date | Initialize the draft to the committed cutoff. Choosing a cell or shortcut updates the draft. A shortcut outside the month also moves the picker month. Keep the last result until confirmation. Cancel clears the draft and picker month and preserves the last result and list position. |
| Confirm a date | Commit the draft, leave Today following mode, clear the previous visible result and show loading for the requested date while it resolves. Return to the full list and show the matching date and amounts or matching retry state. Show **Projected balance** only for a future date. |
| Read finishes or fails | Match both the request identity and key to the latest relevant read before applying the result. Use a new identity for retry. A late older month or balance response cannot replace a newer result. A failure or uncovered date shows matching feedback and retains date controls. Missing-year creation is I06. |
| Menu > Today | Clear the draft, re-enable following, restore the current month and today cutoff, and supersede in-flight dated reads. |

Use stable semantic tags for the card (`calendar_balance_card`), list (`balance_list`), month (`calendar_month_selector`), day (`calendar_day_YYYY-MM-DD`), picker day (`balance_picker_day_YYYY-MM-DD`), event summary (`calendar_day_events`), and dated result (`balance_result`). Localize visible labels and give each day, marker grouping and card the semantic behavior from the mockups. The marker symbols share the day target. At the reference width eight balances fit in row-major order. At the large-text layout recalculate visible amounts and the overflow hint from actual capacity. The list and every date remain reachable.

## Test setup and exact outcomes

Use the [synthetic January fixture](../../specification/verification/acceptance-tests.md#calendar-balance-fixture) loaded through I04's developer-only fixture entry. Inject the local date January 16, 2026 in `America/New_York`. Provide a test result with ten distinct categories for layout, separate from the CLI-derived balance fixture. Use the eight preview values and the two additional values in [BALANCES-ALL](../../specification/artwork/mockups/calendar-compact-main.png). For overlap, seed holiday January 9, 2027 and zero payday offset in the dedicated marker fixture. Hash the fixture ledger before and after each read, browse and cancel case.

| Case | Exact expected result and evidence |
| --- | --- |
| 01 and 05 | On start and Today reset, Jan 16 shows annual 90 h and sick 20 h. |
| 02 and 06 | Jan 13 returns annual 92 h and sick 20 h. A cancelled pick preserves Jan 13. A failing Jan 22 request shows unavailable for Jan 22. Let an earlier response complete afterward and verify it cannot overwrite a newer date. |
| 03 and 04 | Jan 24 closes period 1 with annual 92 h and sick 24 h. Period detail shows Jan 11–24, Jan 30 payday, annual 6 earned and 14 used, and sick 4 earned and 0 used. Jan 22 shows 86 h annual and Jan 25 shows 89 h, with projected labels. The Jan 16 shortcut rows are period 1 on Jan 24 and period 2 on Feb 7. |
| 07 and NAV | Jan 4, 2027 resolves to the 2026 ledger with 233 h annual and 120 h sick. Browsing and opening a day preserve the chosen cutoff. |
| 08 | Render the eight preview amounts and **View all 10 balances · 2 more**. The full list reaches PPL 60 h and FMLA remaining 120 h. Test exactly eight, large text and unavailable amount, then verify back, date confirmation and cancelled selection. |
| MARK and MENU | Follow the event and overlap fixtures and expected TalkBack order. Use identical visible and spoken entry fields on any date. Inject each supported marker flag through the developer fixture. I18 tests the interactive preferences in `T-PREF-CAL-01`. Verify that the picker still marks pay-period ends when the Calendar hides them. Check the full-width fallback. Dismiss Menu without a date change, then use Today. Full Menu items land in the milestone that wires each destination. |

From `android/` run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` and on the chosen running emulator `./gradlew :app:connectedDebugAndroidTest`. Before assignment confirm those are the actual I01 commands and record exact emulator profiles, fixture paths and targeted test class names. Capture the default, compact/large-text and applicable night states using the [visual QA matrix](../../specification/chapters/05-quality/05-02-quality-gates.md#compare-every-affected-gui-to-the-mockups). Compare the actual screen to the annotated board, and manually confirm TalkBack and Back behavior. For PDF use I15's separate file and receiver case.

## Close each subset

Save case results and visual comparisons under `android/qa/reports/I05-0N.md`, evidence in `android/qa/evidence/I05-0N/` or durable CI artifacts, separate AI findings in `android/reviews/code/I05-0N.md`, and resume notes in `android/implementation/passdowns/I05-0N.md`. Resolve required findings and rerun their regressions before closing a subset. Use an approved spec change for an intentional difference. I05 closes after all three subsets and the combined applicable Calendar gates pass.
