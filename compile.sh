#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
# Work from the repository root so the source list holds relative paths: javac splits an @file on
# spaces, so absolute paths would break when the project sits in a folder such as "CS102 Project".
cd "$ROOT"
rm -rf classes
mkdir -p classes
find src/main/java -name '*.java' -print > .java-sources
trap 'rm -f "$ROOT/.java-sources"' EXIT
javac -encoding UTF-8 -cp "lib/*" -d classes @.java-sources
