# CS102 Auto-Grading System

This repository contains a small, local Java auto-grader demo. It compiles the grader with Maven, then executes each raw student `Main.java` with bounded `javac` and `java` subprocesses. The demo needs no credentials, network access, or external service.

## Prerequisites

- JDK 17 or newer, with both `java` and `javac` on `PATH`
- Maven 3.9 or newer

## Build and test the grader

```sh
mvn test
mvn package
```

A student submission is source-file based. The grader passes a source path to bounded `javac` and runs the resulting `Main` class with bounded `java`; neither subprocess uses a shell.

## Output limit

Each compile or execution process has a **combined stdout plus stderr capture budget**, 1 MiB by default. Capture is bounded while streams are read, not truncated after an unbounded allocation. When the combined byte total exceeds the budget, the process tree is terminated, retained output is marked truncated in an `output-limit` diagnostic, and grading continues with the next question/student. This is output control only, not a sandbox or arbitrary-code security boundary.

For a path-based run, configure a positive finite budget:

```sh
mvn -q exec:java -Dexec.mainClass=autogradesys.App \
  -Dexec.args="--non-interactive --submissions-dir /path/submissions --questions-dir /path/questions --scoresheet /path/scores.csv --output-limit-bytes 1048576"
```

Invalid or non-positive limits are rejected before grading. The diagnostic includes student and question context, the configured byte budget, retained stream totals, truncation, termination, and remediation guidance.

## Run the checked-in demo

```sh
mvn -q exec:java -Dexec.mainClass=autogradesys.App -Dexec.args=demo
```

The command uses `demo/fixtures` and writes `demo/target/run/scores.csv`, `error-history.jsonl`, and `grading-report.html`. Existing demo counts and report behavior are unchanged. For a dedicated bounded-output proof, run the focused test:

```sh
mvn -q -Dtest=JavaGraderTest#outputLimitStopsFloodingRetainsBoundedBytesAndContinues test
```

The test uses the actual Java grader path, a finite configured 128-byte budget, a controlled flooding fixture, and a healthy later question. It proves termination, bounded retained output, contextual reporting, and continuation without exhausting host memory.

## Other non-interactive sessions

The CLI accepts `--timeout` or `--timeout-ms`, `--history`, `--report`, and `--output-limit-bytes`. Invalid setup and scoresheet failures remain fatal; per-question failures, including timeout, runtime error, wrong answer, and output-limit, remain recoverable.

## Fixture layout

`demo/fixtures/questions/<question>` contains `expected.txt` and optional `input.txt`; submissions are `demo/fixtures/submissions/<student>/<question>/Main.java`. Expected output is compared exactly. Each question gets a fresh temporary build directory.

## Generated files

Maven output is under `target/`; demo output is under `demo/target/`. Before reporting a change, run `mvn test`, `mvn package`, and `git diff --check`.
