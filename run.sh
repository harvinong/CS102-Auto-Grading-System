#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec java -cp "$ROOT/classes:$ROOT/lib/*" autogradesys.App "$@"
