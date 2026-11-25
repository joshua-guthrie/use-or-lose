#!/usr/bin/env bash
# Pull every device evidence capture from the debug app's private files dir
# into android/qa/evidence/I05-02/. Run after any connectedDebugAndroidTest
# run so the local evidence always matches the latest verified build.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
out="$repo_root/android/qa/evidence/I05-02"
adb_bin="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
mkdir -p "$out"

remote_dir="files/qa/I05-02"
"$adb_bin" shell "run-as com.westmouthbay.fedleave ls $remote_dir" 2>/dev/null |
    tr -d '\r' | grep '\.png$' |
while read -r name; do
    "$adb_bin" shell "run-as com.westmouthbay.fedleave cat $remote_dir/$name" > "$out/$name"
done

echo "pulled $(find "$out" -maxdepth 1 -name '*.png' | wc -l) captures to $out"
