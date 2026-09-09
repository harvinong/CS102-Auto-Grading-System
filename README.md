# CS102 Auto-Grading System - error-reporting MVP

This MVP is a deterministic, local CLI for grading Java submissions while keeping actionable errors from the whole run. It does not require a service, credentials, or network access.

## Layout

```text
submissions/<student>/<question>/Main.java
questions/<question>/expected.txt
questions/<question>/input.txt       # optional, empty input when omitted
scores.csv                           # student,q1,q2,...
```

The question directory names define the required questions. A submission can fail one question without preventing other students or questions from running. Java compilation and execution use `javac` and `java`; each is protected by `--timeout` (default 2 seconds).

## Run

Interactive:

```sh
python3 grader.py
```

For scripts and CI, provide all paths:

```sh
python3 grader.py --non-interactive \
  --submissions-dir ./submissions --questions-dir ./questions \
  --scoresheet ./scores.csv --history ./grader-error-history.jsonl
```

A clean run exits 0. Fatal run setup or scoresheet failures exit nonzero. Per-student and per-question failures are printed and do not abort the remaining run. Output includes stage, category, severity, guidance, and available student/submission/question context.

## Error history

Every reported error is appended immediately as one JSON object per line to `.grader-error-history.jsonl` by default (or `--history PATH`). It is durable across runs and safe to inspect or share:

```sh
python3 -m json.tool < <(head -n 1 .grader-error-history.jsonl)
grep '"category": "timeout"' .grader-error-history.jsonl
```

History is intentionally append-only; remove or archive it between grading sessions when a fresh report is desired.

## Tests

```sh
python3 -m unittest discover -s tests -v
```

Tests cover filesystem and submission-structure errors, missing questions, scoresheet persistence, bad score rows, and the clean path. The integration clean-path test requires a JDK on `PATH`.
