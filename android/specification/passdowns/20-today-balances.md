# First-time leave-year balance date

November 21, 2026. For a newly created independent year that includes Today, choose **Balances at start of leave year** or **Balances as of Today**. The second choice starts earlier days in that ledger at zero, records the exact entered amounts on the current local date, and resumes normal scheduled accrual. Earlier years are untouched. [Requirements](../chapters/02-ledger/02-01-ledger-and-leave-years.md#balances-as-of-today) · [Setup screens](../chapters/01-calendar/01-03-navigation-and-screens.md#choose-the-balance-date-at-setup)

A disposable CLI test verified zero and nonzero fixtures on February 10, February 7 (an accrual day) and January 9, 2027 (the final accrual). It checked dated results, earlier historical projections, and the exact schema-v1 backup round trip. A last-day zero target preserved 6 h annual and 4 h sick rates for the successor, and 122 related desktop tests passed. [Acceptance case](../verification/acceptance-tests.md#ledger-and-leave-year-acceptance)

I06-01 delivers ordinary setup. I06-02 adds the Today choice, validation, atomic creation and handset QA. [Coding brief](../../implementation/briefs/I06-02-today-balances.md)
