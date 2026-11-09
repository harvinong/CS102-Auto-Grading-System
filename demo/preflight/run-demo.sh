#!/bin/sh
# Runs the real menu (Grade students) over the synthetic demo data from a throwaway working
# directory, so no config.json, scoresheet or report is created or overwritten in your own folders.
# Build first with ./compile.sh (this script uses its classes/ and lib/ output).
set -e
# Repository root, found from this script's own location.
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
# The checked-in demo data: template, testers, scoresheet and student submissions.
D="$REPO/demo/preflight"
# Throwaway folder that holds the copies the grader is allowed to change.
WORK="$(mktemp -d)"
# Copy the submissions, because .zip files are git-ignored and frank.zip is created below.
cp -R "$D/submissions" "$WORK/submissions"
printf 'PK\n' > "$WORK/submissions/frank.zip"
# Copy the scoresheet, because grading writes the marks into it.
cp "$D/scores.csv" "$WORK/scores.csv"
# The program writes config.json, its error history and its HTML report into its working directory.
mkdir "$WORK/run" && cd "$WORK/run"
# Type the answers a user would: starting folder, testers folder, scoresheet, menu option 3,
# the submissions folder, 3 to start grading after the submission check, then 0 to quit.
printf '%s\n' "$D/RenameToYourStudentID" "$D/testers" "$WORK/scores.csv" 3 "$WORK/submissions" 3 0 \
  | java -cp "$REPO/classes:$REPO/lib/*" autogradesys.App
# Show the marks that were written, then clean up.
echo
echo "Scoresheet after grading:"
cat "$WORK/scores.csv"
rm -rf "$WORK"
