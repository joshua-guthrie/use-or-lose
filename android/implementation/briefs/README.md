# Android implementation briefs

Start with the [milestones and dependencies](../../specification/chapters/06-delivery/06-02-implementation-milestones.md). A brief is ready to assign when the prerequisites pass, its source and fixture revisions are recorded, file and symbol anchors resolve in that starting tree, and its commands and expected evidence are executable.

| Brief | Status and scope |
| --- | --- |
| [I01-01 Start the Android project](I01-01-start-the-android-project.md) | Draft pending owner approval to start. Sets the source and verification foundation. |
| [I02 Bridge packaging experiment](I02-bridge-experiment.md) | Handoff draft. Decides Chaquopy or the fallback with packaging, parity, offline and timing evidence. |
| [I03 Read-only core boundary](I03-read-only-core.md) | Handoff draft. Extracts the snapshot, dated balance, day, period and month facade. |
| [I04 Private JSON store](I04-private-store.md) | Handoff draft. Adds the no-backup canonical store, generation writer and recovery. |
| [I05 Calendar navigation and balances](I05-calendar.md) | Detailed handoff draft. I01–I04 supply the pinned source, actual module paths, bridge calls, and run settings. |
| [I06-02 Start with Today’s balances](I06-02-today-balances.md) | Adds a dated starting point after I06-01 provides independent setup. |
| [Inputs by milestone](milestone-inputs.md) | Source anchors, acceptance families and handoffs for I01–I20. |
| [Implementation brief template](template.md) | Required for each bounded implementation task, including splits within a milestone. |

The person or model preparing the brief resolves product behavior and compatibility questions before assigning code. A smaller coding model should be able to follow the brief with chapter links and repository access. Close the work with the [QA loop](../../specification/chapters/05-quality/05-02-quality-gates.md) and a separate linked code review. Record unfinished work in the passdown.

For delegated work, use the [design and packet gate](../../specification/chapters/07-organization/07-03-design-and-implementation-handoffs.md). Pin this brief's revision in the task manifest and generate the worker packet from those inputs. For orchestration development, use the [pilot milestones](../../specification/chapters/06-delivery/06-03-orchestrator-pilot-milestones.md) and their acceptance cases.
