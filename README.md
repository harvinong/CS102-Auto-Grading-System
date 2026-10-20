# CS102 Auto-Grading System

This repository contains a small, local Java auto-grader demo. It compiles the grader with Maven, then executes each raw student `Main.java` with bounded `javac` and `java` subprocesses. The demo needs no credentials, network access, or external service.

## Prerequisites

- JDK 17 or newer, with both `java` and `javac` on `PATH`
- Maven 3.9 or newer

Check the tools with:

```sh
java -version
javac -version
mvn -version
```

## Build and test the grader

From the repository root:

```sh
mvn test
mvn package
```

Maven builds and tests the auto-grader itself. A student submission is deliberately not a Maven project: the assignment fixture is source-file based, so the grader passes a source path to bounded `javac` and runs the resulting `Main` class with bounded `java`. Neither subprocess uses a shell, so student-controlled paths and text are not interpolated into shell commands.

## Run the checked-in demo

The non-interactive Maven command is:

```sh
mvn -q exec:java -Dexec.mainClass=autogradesys.App -Dexec.args=demo
```

The command uses `demo/fixtures` and writes generated state to `demo/target/run`:

```text
demo/target/run/scores.csv
demo/target/run/error-history.jsonl
demo/target/run/grading-report.html
```

The first run copies `demo/fixtures/scores.template.csv` to the generated scoresheet. It never changes the source-controlled template or expected outputs. The history is append-only, and rerunning the demo retains the generated scoresheet and appends new JSONL records. To intentionally start a new local demo session, remove only the generated directory and run the command again:

```sh
rm -rf demo/target
mvn -q exec:java -Dexec.mainClass=autogradesys.App -Dexec.args=demo
```

Open `demo/target/run/grading-report.html` directly in a browser for a self-contained report of this run. It includes a run ID and timestamp, summary counts, every student/question score, and escaped error messages with guidance. It is overwritten for each run, so older JSONL history cannot leak into the current report.

The output includes `PASS` lines and contextual errors for:

- a passing student and question
- a compiler failure for Bob
- a runtime exception for Frank
- a hung program for Carol, terminated after the per-question timeout
- an output mismatch for Dave
- Erin's missing question directory
- the blank student row in the scoresheet

The run continues with other questions and students after each recoverable error. Human-readable errors include stage, category, severity, guidance, and available student, submission, and question context. The same fields are written as one JSON object per line in `error-history.jsonl`. Scores are updated atomically in `scores.csv`, while the existing `legacy` row and other prior rows remain intact.

## Fixture layout

```text
demo/fixtures/
  questions/q1/expected.txt
  questions/q2/expected.txt
  questions/q3/expected.txt
  questions/q4/expected.txt
  submissions/<student>/<question>/Main.java
  scores.template.csv
```

Question directories define the required question set. `input.txt` is optional and is sent to the student's standard input when present. `expected.txt` is compared exactly with standard output, including whitespace and line endings. Each question is compiled into a fresh temporary build directory, which is deleted after that question.

## Other non-interactive sessions

The same Java CLI accepts explicit paths:

```sh
mvn -q exec:java \
  -Dexec.mainClass=autogradesys.App \
  -Dexec.args="--non-interactive --submissions-dir /path/submissions --questions-dir /path/questions --scoresheet /path/scores.csv --history /path/errors.jsonl --report /path/grading-report.html --timeout-ms 2000"
```

Use `--timeout` for seconds instead of `--timeout-ms`. Invalid arguments return a nonzero status. Invalid setup and scoresheet read/write failures are reported as fatal, return a nonzero status, and stop before student programs or scoresheet writes run. A per-student or per-question failure is recoverable and does not stop unrelated work.

## Generated files

Maven's normal build output is under `target/`. The demo output is under `demo/target/`, which is ignored by Git. `scores.template.csv` and all question expected outputs are fixtures and are never rewritten by the demo.

Before reporting a change, run:

```sh
mvn test
mvn package
git diff --check
```
