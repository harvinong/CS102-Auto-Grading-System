# CS102 Auto-Grading System

This repository contains a local Java/Maven auto-grader. It compiles raw student `Main.java` files with `javac`, executes them with `java`, updates CSV scores, records JSONL diagnostics, and writes a current-run HTML grading report.

## Build and test

```sh
mvn test
mvn package
```

## Two current-run features

### Current-run HTML grading report

`src/main/java/autogradesys/grading/HtmlReportWriter.java` renders the current `GradeResult` and current `ErrorReporter.records()` into a self-contained `grading-report.html`. It includes run metadata, summary counts, every student/question score, escaped diagnostics, context, severity, and guidance. It is overwritten per run, so append-only history does not leak into the report. This is inherited from the earlier HTML-report feature.

Run the inherited six-student demonstration:

```sh
rm -rf demo/target
mvn -q exec:java -Dexec.mainClass=autogradesys.App -Dexec.args=demo
```

Its presentation remains 19 passes, 5 failures, and one extra warning in the generated report/history.

### Bounded student-program output

`src/main/java/autogradesys/grading/ProcessRunner.java` captures stdout and stderr concurrently under one combined finite byte budget. The default is 1 MiB; configure a positive value with `--output-limit-bytes`. The limit is enforced while reading, retaining at most the budget rather than allocating unbounded output. On overflow, the existing process-tree lifecycle terminates the process and descendants, and `JavaGrader` emits a contextual recoverable `output-limit` diagnostic labeled truncated. Timeout, runtime error, and wrong answer remain separate categories, and grading continues.

Reproduce the trusted finite flood fixture from the repository root:

```sh
rm -rf demo/output-limit/target
mkdir -p demo/output-limit/target
cp demo/output-limit/fixtures/scores.csv demo/output-limit/target/scores.csv
mvn -q exec:java -Dexec.mainClass=autogradesys.App -Dexec.args="--non-interactive --submissions-dir $(pwd)/demo/output-limit/fixtures/submissions --questions-dir $(pwd)/demo/output-limit/fixtures/questions --scoresheet $(pwd)/demo/output-limit/target/scores.csv --history $(pwd)/demo/output-limit/target/history.jsonl --report $(pwd)/demo/output-limit/target/grading-report.html --timeout-ms 2000 --output-limit-bytes 128"
```

Observed result: `flood/q1` gets `output-limit` with retained `stdout=128 bytes`, while `flood/q2` and both healthy questions pass. The generated CSV is `flood,0,1` and `healthy,1,1`; report and history are under `demo/output-limit/target/`.

This is output robustness only, not a sandbox, container platform, network/filesystem policy, or arbitrary-code security claim.

## Other non-interactive sessions

The CLI accepts `--timeout` or `--timeout-ms`, `--history`, `--report`, and `--output-limit-bytes`. Invalid setup and scoresheet failures remain fatal; per-question failures remain recoverable. Neither subprocess uses a shell.

Before reporting changes, run `mvn test`, `mvn package`, and `git diff --check`.
