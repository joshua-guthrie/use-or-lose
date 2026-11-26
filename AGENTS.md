# Repository working rules

## Keep the Android developer README current

`android/README.md` is the entry point for viewing the project in Android Studio, the build and test commands, the architecture and the directory layout. Update it in the same change whenever those facts change. I01 must replace its placeholder toolchain with the tested versions; I02 records the bridge module and runtime. Do not leave stale instructions in it.

## Desktop builds and releases are on demand

Source pushes never build or publish installers. The Distribution workflow runs
only via `workflow_dispatch`: with a `release_version` it builds and validates
both desktop packages and, when `make_release` is set, creates the tagged
GitHub release with the desktop installers (no Android artifacts). A dispatch
without a release version builds a development artifact for validation only.

Only the tiny `installers/BUILD.txt` pointer is version controlled; installer
binaries live on the release and never enter the repository (this keeps LFS and
repo size quotas flat). The current published build is the newest explicit
release. No rolling `beta` channel exists; releases are created only when the
owner asks for one.

The public download URLs documented in `README.md` are compatibility surfaces.
If a URL must change, call it out explicitly in the push or issue closeout so
the project website can be updated.

## Local security reviews

Store security audit findings, working notes, and evidence under
`untracked/security/`. Git ignores this directory. Land remediation and
regression tests with the affected source.
