# I05 · Calendar navigation and dated balances

**Finalized draft, pending planner approval.** This replaces the handoff notes with the actual package paths, symbols, dependencies, fixtures and test classes found in the I04 tree (`049272c`). Sections marked **Proposed decision** record a binding choice that the planner must approve, adjust, or replace before assigning code. Nothing else in this brief is speculative: every named file, symbol, fixture, command and expected value was verified against the starting tree.

## Assignment

- Status: **owner-reviewed draft**. The owner reviewed the bindings on November 24, 2026 and approved the synthetic-evidence and mockup-conformance decisions recorded inline (P-D4, P-D6, Verification). Remaining formal step: planner sign-off on P-D1…P-D6 (or owner go-ahead) before assigning coding tasks; once given, remove this section's draft marker.
- Starting commit: `049272c` (`master`, I01–I04 complete; I04 QA on `e016575`).
- Approved spec surfaces: checkpoint 31 owner approval of I01–I06; chapters [01-01](../../specification/chapters/01-calendar/01-01-main-screen.md), [01-02](../../specification/chapters/01-calendar/01-02-balances.md), [01-03](../../specification/chapters/01-calendar/01-03-navigation-and-screens.md), [01-04](../../specification/chapters/01-calendar/01-04-date-markers.md), [01-05](../../specification/chapters/01-calendar/01-05-menu-and-pdf.md), [01-07](../../specification/chapters/01-calendar/01-07-menu-structure.md), [05-02 QA gates](../../specification/chapters/05-quality/05-02-quality-gates.md) and the [Calendar acceptance cases](../../specification/verification/acceptance-tests.md#calendar-acceptance-cases).
- Prerequisite evidence to read first: [I01-01 passdown](../../implementation/passdowns/I01-01.md), [I03 passdown](../../implementation/passdowns/I03.md) and its facade `src/fedleave/read_api.py`, [I04 passdown](../../implementation/passdowns/I04.md) plus `app/src/main/java/com/westmouthbay/fedleave/storage/` and `app/src/debug/java/com/westmouthbay/fedleave/storage/DeveloperFixture.kt`.
- Read order for the implementer: `MainActivity.kt` → `storage/StoreCoordinator.kt`, `StoreModels.kt`, `StorePaths.kt` → `bridge/src/fedleave_probe.py` → `src/fedleave/read_api.py` → `app/build.gradle.kts` (assets and Chaquopy blocks) → `android/qa/fixtures/build_calendar_fixture.py` → the mockup JSONs under `specification/artwork/mockups/`.

### File ownership by task

All new files use the existing app package `com.westmouthbay.fedleave`.

| Task | Files owned (new unless noted) | Delivers |
| --- | --- | --- |
| I05-01 State and bridge | `core/CoreBridge.kt`, `core/CoreModels.kt`, `core/TimeSource.kt`, `core/AppDependencies.kt`, `calendar/CalendarViewModel.kt`, `calendar/CalendarUiState.kt`, `calendar/CalendarDisplaySettings.kt`; debug `calendar/CalendarDeveloperConfig.kt`; tests `app/src/test/.../core/CoreModelsTest.kt`, `app/src/test/.../calendar/CalendarViewModelTest.kt`, `app/src/androidTest/.../core/CoreBridgeTest.kt` | The typed Chaquopy adapter over `fedleave_probe` + I03 facade, the clock and display-settings injection, the composition root, and the full Calendar state machine. I05-02 and I05-03 consume only the typed state and actions; the placeholder `FedLeaveHome` stays rendered until I05-02. |
| I05-02 Calendar, navigation, and markers | `ui/FedLeaveApp.kt`, `ui/AppRoutes.kt`, `calendar/CalendarScreen.kt`, `calendar/CalendarDateMarkers.kt`, `calendar/MonthPicker.kt`, `calendar/DayScreen.kt`; `MainActivity.kt` (modified); `gradle/libs.versions.toml` + `app/build.gradle.kts` (modified); tests `app/src/androidTest/.../calendar/CalendarScreenTest.kt`, `CalendarNavigationTest.kt`, `CalendarMarkersTest.kt`; `LaunchSmokeTest.kt` (updated) | The real Calendar destination, bottom bar, month selection, responsive grid and full-width day list, the four marker glyphs, the read-only day route, and the Menu dropdown with Today. I05-03 adds the balance route and picker. |
| I05-03 Balance detail and date selection | `calendar/BalanceScreen.kt`, `calendar/BalanceDatePicker.kt`, `calendar/BalancePresentation.kt`; tests `app/src/test/.../calendar/BalanceDatePickerStateTest.kt`, `CalendarViewModelTest.kt` (extended), `app/src/androidTest/.../calendar/BalanceScreenTest.kt` | The card wiring, all-balances list, period activity, nearby closing-date shortcuts, date selection and confirmation, projected and unavailable states. Run the combined Calendar suite at the end. |

I05 closes after all three subsets and the combined applicable Calendar gates pass. Route registration and touched string resources are part of each subset's own files; `MainActivity.kt` is touched only by I05-02.

### Proposed decisions requiring planner approval

- **P-D1 · Typed bridge adapter is created inside I05-01.** The I03 adapter today is Python-only (`fedleave_probe.call`), and the I04 store hands raw JSON text. I05-01 creates `CoreBridge` (see below). No change to `src/fedleave/read_api.py`, `bridge/src/fedleave_probe.py`, or the desktop CLI.
- **P-D2 · Leave-year metadata comes from the snapshot, not a new facade op.** The UI needs `leave_year_start`/`leave_year_end` for the year-end marker and day events. The month-view result does not expose them. The ViewModel parses those two fields from the snapshot file `leave_years/<year>.json` (JSON text already held by `StorageSnapshot.files`) with `org.json`, matching the storage package. Rejected alternative (kept visible for the planner): adding a `year_info` operation to the shared facade, which would reopen the closed I03 surface.
- **P-D3 · Payday overlap fixture uses stored pay dates, not a runtime offset.** The facade derives `is_payday` from the stored `pay_date` (`pay_period_pay_date` prefers the explicit field). The marker fixture therefore rewrites each stored `pay_date` to its period end (offset 0). The user-facing payday-offset display preference stays an I18 concern. No core change.
- **P-D4 · Navigation stack is created in I05-02 with Navigation Compose.** Add `androidx.navigation:navigation-compose:2.10.2` (latest stable on Google Maven at briefing time) and `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7` (matches the pinned lifecycle 2.8.7) to the version catalog. Verify the pin resolves at first sync and record the final version in the I05-02 QA report. The bottom bar follows the mockups exactly: **Calendar · Reports · Settings** all render in I05, with Calendar active. Reports and Settings are working navigation destinations with minimal placeholder content until their real screens land (Reports I13, Settings I18); record the placeholder content as the interim gap. Owner decision, November 24, 2026: follow the existing mockups; do not hide the other tabs.
- **P-D5 · Clock and settings injection.** `TimeSource` (interface, device local date) with a debug-only fixed `2026-01-16` `America/New_York` override; `CalendarDisplaySettings` data class with defaults `showHolidays=true, showPaydays=true, showPayPeriodEnds=true, showAccruals=true, weekStart=SUNDAY`. Both live in `AppDependencies`, which `MainActivity` constructs. I18 replaces the defaults with persisted preferences.
- **P-D6 · Marker fixture is generated by the pinned builder.** Extend `android/qa/fixtures/build_calendar_fixture.py` to emit `qa/fixtures/marker-fixture/calendar-markers/` (calendar-2026 files with `pay_date` = period end, plus a generated 2027 holiday cache with one synthetic January 9, 2027 entry) and its own manifest with its own tree digest. The calendar-2026 tree digest must remain `03e9e1757f73015b356b900703a13a88390e854f274c7bce78bd2f9f00c7aea9`. Never hand-edit generated files. Owner-approved, November 24, 2026: synthetic data in developer-only fixtures is fine; production holidays never come from these files — the app generates its cache offline (bundled rules) or restores it from backup and makes no OPM download.

### Preserved interfaces and boundaries

- `StoreCoordinator`, `StorageSnapshot`, `StorePaths.forContext`, and the debug `DeveloperFixture.importNamedAssets` keep their exact signatures; I04 tests stay green.
- The bridge probe contract (schema_version 1, `{local_date, files, request}` → `{schema_version, ok, result | error}`) is unchanged; `BridgeProbeTest` stays green and is the adapter's contract oracle.
- Deferred: **Edit day** controls (I08), **Transactions** and **Force Balance** menu commands (I09, I10), top-right **Share** (I15), Settings persistence and help (I18), missing-year creation (I06). Interim builds expose only working destinations. I05 gates on `T-CAL-MENU-01` with **Today** only.
- The day route is read-only in I05: it opens **Edit day** with the tapped date, its schedule events, and its saved activity, but has no **Add entry** or review controls.

## Behavior and contracts

### Typed core contract (I05-01)

`CoreBridge` (package `com.westmouthbay.fedleave.core`) exposes suspend functions that run on `Dispatchers.IO`:

- `suspend fun datedBalance(snapshot: StorageSnapshot, request: DatedBalanceRequest, localDate: LocalDate): DatedBalanceResult`
- `suspend fun monthView(snapshot: StorageSnapshot, year: Int, month: Int, calendarYear: Int = year, localDate: LocalDate): MonthViewResult` — the `calendar_year` display parameter lets the UI browse a calendar month that belongs to an adjacent leave year (e.g. January 2027 on the 2026 ledger).
- `suspend fun payPeriod(snapshot: StorageSnapshot, date: LocalDate, localDate: LocalDate): PayPeriodDetailResult`
- `suspend fun dayActivity(snapshot: StorageSnapshot, date: LocalDate, localDate: LocalDate): DayActivityResult`

Each call builds the probe payload (files = `snapshot.files` verbatim, `local_date` ISO), invokes `Python.getInstance().getModule("fedleave_probe").callAttr("call", payload)`, and maps the JSON envelope to typed models in `CoreModels.kt`:

- `DatedBalanceResult(year: Int, asOf: LocalDate, projected: Boolean, projectTo: String?, balances: Map<String, Double>, parental: ParentalSummary?, warnings: List<String>)`; `ParentalSummary(pplAvailableHours: Double?, fmlaRemainingHours: Double?)` mapped from the parental payload's `ppl_available_hours` and `fmla.remaining_hours`.
- `MonthViewResult(year, month, today, days: List<MonthDay>, payPeriods: List<PeriodRow>, payDates: Set<LocalDate>, warnings)`; `MonthDay(date, inDisplayMonth, holidayName: String?, isToday, isPayday, isPayPeriodEnd, entries: List<DayEntry>, displayLines: List<String>)`; `DayEntry(id, category, direction, hours, status, source, description)`; `PeriodRow(number, start, end, payDate, totals: Map<String, CategoryTotals>, endingBalances: Map<String, Double>)`.
- `PayPeriodDetailResult(year, date, period: PeriodInfo, activity: Map<String, CategoryTotals>, endingBalances, warnings)`.
- `DayActivityResult(year, date, hasActivity, activity: Map<String, CategoryTotals>)` — not consumed by any I05 screen; covered by the adapter test for contract parity.
- Failures map to `sealed CoreError` with a code and field: `InvalidRequest`, `InvalidDate`, `LeaveYearNotFound(field)`, `InvalidSnapshot`, `MissingConfig`, `UnknownOperation`, `Unexpected(message)`. A `LEAVE_YEAR_NOT_FOUND` for the requested date drives the **BALANCES-UNAVAILABLE** state; any other failure drives it too, with the same Retry / Choose date controls.

`Python.start(AndroidPlatform(context))` runs once on the main thread from `AppDependencies` (mirroring `BridgeProbeTest`). Dates cross the bridge as ISO `YYYY-MM-DD`; Kotlin state uses `java.time.LocalDate`/`YearMonth`. Hours stay `Double`; the UI formats from the core's exact values and never rounds for display logic. All core results are read-only snapshots; nothing in I05 writes to the store (fixture import in tests is the only publisher).

`TimeSource` has one method, `fun today(): LocalDate`, resolving in the device zone; the debug `FixedTimeSource("2026-01-16")` in `CalendarDeveloperConfig.kt` supplies the fixture date. Tests may also inject a mutable fake clock to drive `T-CAL-BAL-01`'s clock advance.

### Calendar state machine (I05-01)

The Calendar ViewModel owns state across rotation (retained via `androidx.lifecycle.ViewModel` and the `viewModel(factory = ...)` hook) and route return. Distinct state fields: `displayMonth: YearMonth`, committed `balanceDate: LocalDate`, `followsToday: Boolean`, `selectedDay: LocalDate?` (day route), and transient `pickerDraft: LocalDate?` + `pickerMonth: YearMonth?` (cleared on cancel or back). Results: `monthResult` keyed to `displayMonth` and `balanceResult` keyed to `balanceDate`, each with a monotonically increasing request identity. The constructor takes `bridge: CoreBridge`, `store: StoreCoordinator`, `timeSource: TimeSource`, `displaySettings: CalendarDisplaySettings` — every test injects fakes or the real adapter.

| Trigger | State change and visible result |
| --- | --- |
| Fresh Calendar launch | Read the injected local day (`TimeSource.today()`), set `displayMonth` to its month, `balanceDate` to it, `followsToday = true`. Launch separate month and balance reads (`month_view(displayMonth)`, `dated_balance(balanceDate)`) on the `CoreBridge`. |
| Local date advances | If `followsToday`, update `balanceDate` and refresh; move `displayMonth` only if the month changed. A browsed month is preserved. An explicit confirmed cutoff stays fixed when the clock changes. |
| Select month or open day | Update only `displayMonth` / `selectedDay`. The balance date and result stay paired. Back restores month, scroll context and focus. |
| Tap balance card | Open **Balances & date** at the committed cutoff. Scroll through all balances and reveal collapsed period activity for an end date. |
| Choose date | Initialize the draft to the committed cutoff. Choosing a cell or shortcut updates the draft only. A shortcut outside the month also moves the picker month. Keep the last result until confirmation. Cancel clears the draft and picker month and preserves the last result and list position. |
| Confirm a date | Commit the draft, set `followsToday = false`, clear the previous visible result, show loading for the requested date while it resolves. Return to the full list with the matching date and amounts, or the matching unavailable state. Show **Projected balance** only for a future date (`result.projected`, i.e. `balanceDate > today`). |
| Read finishes or fails | Match both the request identity and key (month or balance date) to the latest relevant read before applying the result. Use a new identity for retry. A late older month or balance response cannot replace a newer result. A failure or uncovered date shows matching feedback and retains date controls. Missing-year creation is I06; I05 shows the unavailable state with Retry / Choose date. |
| Menu > Today | Clear the draft, set `followsToday = true`, restore the current month and today cutoff, and supersede in-flight dated reads (bump identity). |

Derived presentation values: `isProjected = balanceDate > today && result.projected`; period-number map `end → number` built from `monthResult.payPeriods`; leave-year bounds parsed once per snapshot from each `leave_years/<year>.json` (`leave_year_end`, `leave_year_start`) per **P-D2**. Browsing a month resolves its covering leave year locally from those bounds and passes it (plus `calendarYear` for the display) to `monthView`. The marker visibility filter is applied in the UI layer from `CalendarDisplaySettings`; the balance picker always shows pay-period ends regardless of the main-Calendar flag.

### Balance panel rules (I05-03)

- One dated result drives both views. Rows per category in this order: annual, sick, comp, credit, travel_comp, time_off_award, restored_annual, religious_comp, PPL, FMLA remaining, then any other reported balance in desktop category order, then stable key order. PPL and FMLA rows come from `ParentalSummary`; do not sum overlapping parental windows.
- Card shows short labels (Annual, Sick, Comp, Credit, Travel comp, Time award, Restored AL, Relig. comp) and the full list shows full labels (Annual leave, Sick leave, Compensatory time, Credit hours, Travel comp, Time-off award, Restored annual, Religious comp, Paid parental leave, FMLA remaining). Include a reported zero (e.g. an exhausted PPL grant) and exact fractional values; label a missing value **Unavailable**. Accessibility uses the full labels.
- At the reference width and default text scale render the first eight in row-major order; when fewer fit legibly at the user's width or font scale, use fewer columns and preview values, keep first values in order, compute the hidden count from the actual visible count, and never shrink or clip an amount. Hint: **View all {total} balances · {hidden} more** when `hidden > 0`, part of the single 48 dp card action; with no hidden values the card still opens the full list.
- Full list: one row per category, vertically scrollable at any text scale, with **Choose date** above it and a **Back to Calendar** action. `BalancePresentation.kt` owns ordering, label mapping, hint computation and the "two lines + **+N**" grid truncation, all as pure JVM-testable functions.
- Future result labels **Projected balance** with the exact date; the projected panel includes the explanation text from the `BALANCES-PROJECTED` mockup. Unavailable shows **Requested date · <date>**, the warning card text from `BALANCES-UNAVAILABLE`, **Retry** and **Choose date**.
- Period detail (collapsed card, expands in place): period number, inclusive **Jan 11–24**, **Pay date Jan 30**, and per category **6 h earned · 14 h used** rows from `payPeriod(...)`. **View transactions** is hidden until I09 (interim gap); **Choose date** reopens the picker.

### Shortcuts (I05-03)

On entering the picker, anchor on the committed cutoff; show the first two known pay-period ends on or after the anchor ordered by date, with date and stored period number (fixture: **Period 1 · Sat, Jan 24**, **Period 2 · Sat, Feb 7**). Navigating the picker to another month anchors on that month's first day. Selecting a day or shortcut updates only the pending draft; a shortcut outside the visible month moves the picker month. Show one shortcut when one qualifies; hide the group when none. Confirm applies; Cancel discards draft and picker month.

### Markers (I05-02)

Four 9 × 9 dp glyphs drawn with Canvas in `CalendarDateMarkers.kt`, immediately right of the day number, table order: five-point star (holiday), coin/circle-with-stroke (payday), flag (pay-period end, period number), check-in-calendar (leave-year end). Up to two per row, 2 dp apart, second row when needed; tinted for light/dark/selected. Data: `holidayName`/`isPayday`/`isPayPeriodEnd` from `MonthDay`, period number from the period map, leave-year end from the parsed snapshot field. Spoken order: date, events in table order, then activity. Example target label: **Saturday, January 9, 2027. Pay-period end, period 26. Leave-year end, leave year 2026. 6 hours annual leave earned. 4 hours sick leave earned.** The picker reuses the same glyph composables and always labels period ends.

### Menu (I05-02)

Upper-left **Menu** opens an anchored Material 3 dropdown over Calendar with **Today** only in I05 (Transactions and Force Balance are absent — interim gap, tested by I09/I10). ~248 dp rounded elevated surface, 48 dp rows, dismiss on outside tap or Back with focus restored to Menu; selecting Today closes the dropdown first, then resets month and cutoff. Identical light/dark layout. **Share** icon is absent until I15 (interim gap).

### Day route (I05-02, read-only)

**Edit day** title, the tapped date subtitle (**Friday, Jan 16, 2026**), schedule-event rows above a **Leave activity** heading, then one row per stored entry from `MonthDay.entries` with full labels and exact hours (**Annual leave · 2 h used**). No **Add entry**/review controls. Back returns to the same Calendar month with its existing balance cutoff and focus on the tapped date.

### Month selector and full-width fallback (I05-02)

Month row **January 2026** opens **Choose month**: a **Calendar year** row plus the twelve month rows; selecting a month updates the grid and preserves the balance cutoff. If seven readable 48 dp day targets cannot fit (narrow width or large text), render the full-width scrollable day list instead of the grid: each row spells out schedule events and day activity and opens the same read-only day route. The picker uses the same list fallback for pending cutoff selection.

## Screen and resource checklist

| Screen or state | Annotated mockup | Notes for I05 |
| --- | --- | --- |
| `CALENDAR-MONTH` / `BALANCES-TODAY` | `calendar-compact-main.png` | Interim gaps: no Share icon; with the real fixture the card shows only Annual/Sick (no hidden count). The bottom bar matches the mockup (Calendar active; Reports/Settings open placeholder destinations). |
| `BALANCES-ALL` | `calendar-compact-main.png` (middle) | Full ten-row list only in the fake-bridge layout test and large-text checks. |
| `BALANCE-DATE-PICKER` | `calendar-compact-main.png` (right) | Shortcut rows and buttons exact from the mockup. |
| `CALENDAR-MONTH-SELECTOR`, `CALENDAR-DAY-LIST` | `calendar-compact-month.png` | Grid/list fallback switch per capacity. |
| `BALANCES-DATED`, `BALANCES-PROJECTED`, `BALANCES-UNAVAILABLE` | `calendar-date-states.png` | Exact subtitles and recovery controls. |
| `BALANCES-PERIOD` | `calendar-balances.png` | **View transactions** hidden until I09. |
| `CALENDAR-MENU` | `calendar-menus.png` | Today row only. |
| `CALENDAR-YEAR-END`, `CALENDAR-DAY-MARKERS` | `calendar-markers.png` | Read-only day rows; collision case uses the marker fixture. |
| `CALENDAR-DAY` | `calendar-compact-day.png` | Read-only subset (no Add entry, no Review changes). |

Visible labels go in `app/src/main/res/values/strings.xml` (localizable); the semantic test tags are `calendar_balance_card`, `balance_list`, `calendar_month_selector`, `calendar_day_YYYY-MM-DD`, `balance_picker_day_YYYY-MM-DD`, `calendar_day_events`, `balance_result`. Each day, marker grouping and card carries the semantic behavior from the mockups; TalkBack content descriptions follow the spoken-order rules above and the visible wording stays identical to the spoken wording. Grid cells and day rows are 48 dp touch/focus targets; the calendar content scrolls above the bottom bar on shorter displays.

## Verification

Run from `android/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` and the repository `.venv/bin` on `PATH`. The connected tasks need the `fedleave_test` AVD (pixel_5, API 36 google_apis x86_64, 1080x2340 @ 440 dpi); stop the Gradle daemon (`./gradlew --stop`) before booting the emulator on this 5.6 GB host.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Fixtures and clock:

- Real store: `qa/fixtures/calendar-2026` (`config.json`, `leave_years/2026.json`, `holiday_cache/federal_holidays_2026.json`) imported through `DeveloperFixture.importNamedAssets`; local date fixed to **2026-01-16** `America/New_York` via the debug `FixedTimeSource`. Tree digest must stay `03e9e175…`.
- Marker fixture: `qa/fixtures/marker-fixture/calendar-markers/` (import prefix `calendar-markers/`; `assets.srcDirs` gains `qa/fixtures/marker-fixture`), built by the extended `build_calendar_fixture.py` (**P-D6**), with its own manifest digest recorded in the QA report. January 9, 2027 carries all four events.
- Layout double: a JVM and device fake `CoreBridge` returning a ten-category `DatedBalanceResult` (annual 90, sick 20, comp 8, credit 4, travel_comp 6, time_off_award 12, restored_annual 10, religious_comp 2, ppl 60, fmla 120) — the eight preview values plus the two additional `BALANCES-ALL` values from the mockup. Owner-approved, November 24, 2026: fabricated layout data is the intended evidence for the fictional mockup values; it never leaves the test doubles.
- Hash the fixture ledger tree before and after every read, browse and cancel case (I04's `tree(...)` approach) and assert byte-identity.

| Case | Exact expected result and evidence |
| --- | --- |
| 01 and 05 | On start and Today reset, Jan 16 shows annual 90 h and sick 20 h. A fake-clock advance to Jan 17 keeps 90/20 while Today is active. |
| 02 and 06 | Jan 13 returns annual 92 h and sick 20 h. A cancelled pick preserves Jan 13. A failing Jan 22 request shows unavailable for Jan 22. Let an earlier response complete afterward and verify it cannot overwrite a newer date. |
| 03 and 04 | Jan 24 closes period 1 with annual 92 h and sick 24 h. Period detail shows Jan 11–24, Jan 30 payday, annual 6 earned and 14 used, and sick 4 earned and 0 used. Jan 22 shows 86 h annual and Jan 25 shows 89 h, with projected labels. The Jan 16 shortcut rows are period 1 on Jan 24 and period 2 on Feb 7. |
| 07 and NAV | Jan 4, 2027 resolves to the 2026 ledger with 233 h annual and 120 h sick. Browsing and opening a day preserve the chosen cutoff. |
| 08 | Render the eight preview amounts and **View all 10 balances · 2 more**. The full list reaches PPL 60 h and FMLA remaining 120 h. Test exactly eight, large text and unavailable amount, then verify back, date confirmation and cancelled selection. |
| MARK and MENU | Follow the event and overlap fixtures and expected TalkBack order. Use identical visible and spoken entry fields on any date. Inject each supported marker flag through the developer fixture. I18 tests the interactive preferences in `T-PREF-CAL-01`. Verify that the picker still marks pay-period ends when the Calendar hides them. Check the full-width fallback. Dismiss Menu without a date change, then use Today. Full Menu items land in the milestone that wires each destination. |

Acceptance mapping: `T-CAL-BAL-01`→01/05, `T-CAL-BAL-02`→02/06, `T-CAL-BAL-03`→03, `T-CAL-BAL-04`→03/04, `T-CAL-BAL-05`→05, `T-CAL-BAL-06`→02/06, `T-CAL-BAL-07`→07, `T-CAL-BAL-08`→08, `T-CAL-NAV-01`→NAV, `T-CAL-MARK-01`→MARK, `T-CAL-MENU-01`→MENU. `BridgeProbeTest`, the storage suites and the updated launch smoke test must stay green as regressions. The fixture builder must print the unchanged calendar-2026 digest and the new marker manifest digest; `make check` is not required (no shared-core change) but rerun the builder from a clean checkout as documented in `android/README.md`.

Visual QA matrix: capture on the compact profile the default (light, normal font), large-text and dark (night) states for every screen above; compare each against its annotated mockup with the `image` agent (vision verified working on this tree), record milestone-specific gaps listed above, and save captures under `android/qa/evidence/I05-0N/`. Visual conformance is an **iterative loop inside each subset, not a closeout step**: implement → capture → image-agent comparison → iterate the code → recapture, until the capture matches the mockup. The mockup is the source of truth; the code changes, never the mockup — the only permitted difference is an owner-approved spec revision, recorded explicitly. Log each attempt and code fix in the subset QA report. I05-01 changes no screen, so its visual comparison is Not applicable (as in I04). Manually confirm TalkBack order and Back behavior on the emulator for the menu, picker, day route and full-width fallback.

## Handoff

- Subset records: case results and visual comparisons in `android/qa/reports/I05-01.md`, `I05-02.md`, `I05-03.md`; separate AI reviews in `android/reviews/code/I05-01.md` … ; resume notes in `android/implementation/passdowns/I05-01.md` … . Resolve required findings and rerun their regressions before closing a subset.
- Closeout: all three subsets pass, the combined Calendar acceptance cases (`T-CAL-BAL-01…08`, `T-CAL-NAV-01`, `T-CAL-MARK-01`, `T-CAL-MENU-01`) pass on the final candidate, and the QA report links the code review for the final diff.
- Update `android/README.md` (new `calendar/`, `core/` and `ui/` packages; new dependencies; the fixture builder now emits the marker fixture) and the root `passdown.md` (I05 complete, I06 next) in the same change.
- Next milestones consume: I06 the snapshot/open + publish path via `StoreCoordinator` and the unavailable-state handoff; I08 the day route; I09 the Transactions entry point; I10 Force Balance and the full `T-CAL-MENU-02`; I15 the Share action; I18 the Settings persistence behind `CalendarDisplaySettings`.

Before assigning any task, the planner approves or amends P-D1…P-D6 and removes this section's draft marker.
