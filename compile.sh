#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
rm -rf "$ROOT/classes"
mkdir -p "$ROOT/classes"
find "$ROOT/src/main/java" -name '*.java' -print > "$ROOT/.java-sources"
trap 'rm -f "$ROOT/.java-sources"' EXIT
javac -encoding UTF-8 -cp "$ROOT/lib/*" -d "$ROOT/classes" @"$ROOT/.java-sources"
