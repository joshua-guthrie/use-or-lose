# Calendar marker artwork and Help legend

Chapter 01.04 shows the four actual glyphs inline at the 9 dp Calendar size and defines their meanings and date sources. The same drawing function renders the Calendar, the four transparent PNG references, and the Settings Help mockup. [Actual-size markers](../chapters/01-calendar/01-04-date-markers.md#marker-artwork-at-calendar-size)

The app's offline legend lives at **Settings → Help → Calendar date markers**. I18 uses the bundled help copy, verifies its artwork and descriptions, and checks TalkBack and the return to Help. [Help copy](../help/calendar-date-markers.md) · [Help mockup](../artwork/mockups/settings-calendar-markers-help.png)

Verified 35 reproducible boards, 96 screen IDs, 54 parity and acceptance pairs, and 665 local links. The four PNG references are distinct 9 × 9 RGBA files and regenerate byte-for-byte. The Calendar marker board is byte-identical before and after the shared drawing change.

## Earlier checkpoints in this session

- `a19dee9` adds start-of-year versus Today balances. The prior PDF attachment and no-print specification is included in the same push. [Distribution](https://github.com/joshua-guthrie/fedleave/actions/runs/44963885915)
- `727bf30` adds full-screen landscape chart views and Back behavior. [Distribution](https://github.com/joshua-guthrie/fedleave/actions/runs/44965373250)
- `a3ad935` adds the Analytics usage examples and the full-year Heatmap. [Distribution](https://github.com/joshua-guthrie/fedleave/actions/runs/44966443585)
- `0d6343e` adds Force Balance, the annotated Calendar dropdown and the explanation of manual payroll review. The owner can choose whether to keep the separate payroll action. [Distribution](https://github.com/joshua-guthrie/fedleave/actions/runs/44967751980)
