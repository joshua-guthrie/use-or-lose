# Committed build pointer

This directory no longer mirrors installer binaries: releases are on demand,
and the desktop installers live only on the GitHub release. The only version
controlled file is `BUILD.txt`, which identifies the source commit and version
of the most recent published release; GitHub Actions updates it when a release
is created. Do not edit these files by hand.

The public download links in the main README point at the current stable
release on <https://github.com/joshua-guthrie/use-or-lose/releases>.

Both packages include `fedleave-mcp` beside the CLI. Launching it without a
policy now grants full CLI authority, including destructive operations, to the
launching OS user. Existing restricted policy files retain their profiles;
configure `read_only` explicitly to retain the former policy-free restriction.
See [MCP setup](../docs/MCP.md) for Windows/Linux connection examples and optional
restricted policies.
