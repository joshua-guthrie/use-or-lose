# Android release deliverables

Add a release manifest here for each reviewed candidate that goes to a Play test track or production. Use a stable versioned filename such as `1.0.0-rc1.md`. Link the delivery and publication records from the corresponding QA report.

For each release record the version name and code, exact Android source revision, compatible desktop reference builds, AAB/APK artifact name and SHA-256, build workflow and artifact location, release notes, track and owner approval, and the passing QA and AI review reports. Include the applicable Play Console verification and testing evidence through an authorized location.

Commit release notes, manifests and links to approved artifacts. Keep compiled outputs in CI or the selected Play track. The repository-root `installers/` remains the desktop rolling installer mirror. [Google Play chapter](../specification/chapters/06-delivery/06-01-google-play.md) · [Repository working rules](../../AGENTS.md)
