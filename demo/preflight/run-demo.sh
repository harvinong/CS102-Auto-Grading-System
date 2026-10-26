#!/bin/sh
# Runs the real menu (Grade students) over the synthetic demo data from a throwaway working
# directory, so no config.json is created or overwritten in your own folders.
# Build first: mvn clean dependency:copy-dependencies package
set -e
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
D="$REPO/demo/preflight"
WORK="$(mktemp -d)"
cp -R "$D/submissions" "$WORK/submissions"
printf 'PK\n' > "$WORK/submissions/frank.zip"   # .zip files are git-ignored, so create it here
mkdir "$WORK/run" && cd "$WORK/run"
printf '%s\n' "$D/RenameToYourStudentID" "$D/testers" "$D/scores.csv" 3 "$WORK/submissions" 0 \
  | java -cp "$REPO/target/classes:$REPO/target/dependency/*" autogradesys.App
rm -rf "$WORK"
