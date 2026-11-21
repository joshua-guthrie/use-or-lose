# Chapter 07.01 · Android project organization

Use the [Android front page](../../../README.md) for navigation and the [specification index](../../specification.md) for the normative chapter order. Put a requirement in the chapter where the workflow or contract is defined. Link to it from parity, acceptance, briefs, reviews, and implementation notes by its stable ID. Use a single authored description for a rule so updates have one source. Keep filenames short enough to scan and readable in GitHub.

## Directory layout

Paths below start at the repository root. Add implementation and evidence directories when the first real file belongs there.

| Path | Contents and owner |
| --- | --- |
| `android/specification/specification.md` | Product scope, pinned desktop reference, and numbered chapter index. |
| `android/specification/chapters/NN-topic/NN-NN-subject.md` | Authoritative requirements, annotated screen guides, architecture and delivery chapters. Name each Markdown file with its subsection number. |
| `android/specification/planning/` | How to write and maintain the specification, including the five-hour writing and handoff routine. |
| `android/specification/artwork/mockups/` | Editable JSON manifests, renderer, annotated PNGs and reference material. Keep source and generated boards together and regenerate both in the change that revises a screen. |
| `android/specification/verification/` | Cross-chapter feature parity and acceptance cases with fixtures and exact outcomes. |
| `android/specification/passdowns/` | Numbered specification-writing notes in date order. The repository-root `passdown.md` is the current entry point and points at the latest relevant note. |
| `android/implementation/briefs/` | One versioned execution brief per bounded task. Use `I05-01-calendar-state.md` as the filename pattern. A brief links the approved spec and binds it to exact files, tests and examples. |
| `android/implementation/passdowns/` | Handoff for each coding milestone or completed subset. Name a note `I05-01.md` using its implementation ID and update the root passdown to point to it. |
| `android/app/` | Kotlin, Compose screens, resources, manifest, JVM and device tests. Group code by feature under the application namespace established by I01. Put shared screen components in the app module when they have real callers. |
| `android/gradle/` and Android root Gradle files | Pinned wrapper, compatible toolchain and module wiring, added by I01. Android Studio opens the `android/` directory. Developer machine configuration stays local. |
| `android/bridge/` | Optional Android-specific Python packaging module if the I02 experiment needs a separate module. Record its actual path and versioned contract in I02. The portable Python accounting code used by both desktop and phone resides under the existing root `src/`. |
| `android/qa/fixtures/`, `android/qa/golden/` | Synthetic input stores, fixture manifests, expected results and accepted app screenshot baselines. Name references with case and device IDs. |
| `android/qa/evidence/`, `android/qa/reports/` | Screenshots and test evidence by milestone, or a durable artifact link. The Markdown report is the gate decision and records commands, build and fixture identities. |
| `android/reviews/code/` | One AI code review report for each candidate milestone or completed subset. Use `I05-01.md` and record follow-up findings and the final candidate in the same report. |
| `android/deliverables/` | Versioned release notes, artifact manifest and references to approved APK/AAB outputs and Play Console evidence. Build products are stored by the release tooling. |

## Keep related evidence discoverable

- Follow a stable chain from chapter and requirement ID to parity row, acceptance test, candidate brief, QA report, and separate code review. Use the existing IDs when moving files. Link a replacement for a retired workflow from any retained passdown that depends on it.
- Keep the mockup screen ID stable when adjusting a layout. Create a new ID for a new interaction or state. Commit the edited manifest and regenerated PNG together. Distinguish specification mockups from app screenshots captured during verification.
- Put commands and fixture inputs in version control once they are established. Store brief launch/build context in the brief and concrete results in the QA report. Reserve the passdown for the next actionable checkpoint, including blocked gates and the measured five-hour usage.
- Retain synthetic fixtures, sanitized review and QA summaries, and selected approved images. Put temporary logs and build outputs in ignored directories. Keep real leave data, developer credentials, signing keys and local machine configuration outside Git. Repository security audit working material stays under the ignored `untracked/security/` directory.

## Document code review in every milestone

After implementation, a distinct AI review reads the final diff against the approved chapters, brief, mockups, acceptance data and actual QA evidence. Put findings, resolutions and retest evidence in the [AI code review report](../../../reviews/code/template.md). Link it from the QA report. Review the revised portions after material fixes and leave the milestone open until required work passes. The project owner approves any intended change to the product specification or visual design. [QA completion gates](../05-quality/05-02-quality-gates.md#required-gates)

## Changes to this organization

Add files to the location that matches their purpose. When moving an established chapter or asset, update the navigation, relative links, rebuild commands, briefs, QA references and current passdown together. Preserve Git history and established requirement IDs. Describe a move in its commit so a reader can find older versions.
