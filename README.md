# CS102 Auto-Grading System

This repository contains a small, local Java auto-grader demo. It compiles the grader with Maven, then grades student submissions with bounded `javac` and `java` subprocesses: either the instructor's tester classes against the CS102 template layout (menu option 3), or a raw student `Main.java` (the scripted `demo` argument). The demo needs no credentials, network access, or external service.

## Safety and scope

The grader compiles and runs submitted Java code directly on the instructor's machine, as the instructor's user, with no sandbox or container isolation.
It is intended for trusted local fixtures and reviewed coursework submissions, not untrusted or anonymous code.
The per-run timeout and combined stdout/stderr limit (2 seconds and 1 MiB by default) stop runaway or flooding programs, but they are resource limits, not security isolation.
Running untrusted submissions safely would require OS or container isolation, which is out of scope for this MVP.

## Prerequisites

- JDK 17 or newer, with both `java` and `javac` on `PATH`
- Maven 3.9 or newer (for development and tests only)

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

After `mvn clean package`, start the packaged application directly:

```sh
java -jar target/CS102-Auto-Grading-System-1.0-SNAPSHOT.jar
```

The jar bundles its runtime dependencies.

Maven builds and tests the auto-grader itself. A student submission is deliberately not a Maven project: the assignment fixture is source-file based, so the grader passes a source path to bounded `javac` and runs the resulting `Main` class with bounded `java`. Neither subprocess uses a shell, so student-controlled paths and text are not interpolated into shell commands.

## Run without Maven

The submission includes portable scripts for assessment. From the repository root, run `./compile.sh` followed by `./run.sh` (or `compile.bat` and `run.bat` on Windows). The scripts compile the application into `classes` and use only the checked-in runtime jars in `lib`; no network or Maven installation is required. `run.sh demo` runs the checked-in demo.

## Run the checked-in demo

The non-interactive Maven command is:

```sh
mvn -q exec:java -Dexec.mainClass=autogradesys.App -Dexec.args=demo
```

The command uses `demo/fixtures` and writes generated state to `demo/target/run`:

```text
demo/target/run/scores.csv
demo/target/run/error-history.jsonl
```

The first run copies `demo/fixtures/scores.template.csv` to the generated scoresheet. It never changes the source-controlled template or expected outputs. The history is append-only, and rerunning the demo retains the generated scoresheet and appends new JSONL records. To intentionally start a new local demo session, remove only the generated directory and run the command again:

```sh
rm -rf demo/target
mvn -q exec:java -Dexec.mainClass=autogradesys.App -Dexec.args=demo
```

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
  -Dexec.args="--non-interactive --submissions-dir /path/submissions --questions-dir /path/questions --scoresheet /path/scores.csv --history /path/errors.jsonl --timeout-ms 2000"
```

Use `--timeout` for seconds instead of `--timeout-ms`. Menu grading reads its limits from `config.json` instead: `grading_timeout_seconds` (default 2, at most 3600) and `output_limit_bytes` (default 1048576, at most 67108864), both whole numbers from 1 up to their maximum. The same maximums apply to `--timeout`, `--timeout-ms` and `--output-limit-bytes`. Missing or invalid values in `config.json` fall back to the defaults, "View configuration paths" shows them, and "Edit configuration paths" changes them (Enter keeps the current value). Invalid arguments return a nonzero status. Invalid setup and scoresheet read/write failures are reported as fatal, return a nonzero status, and stop before student programs or scoresheet writes run. A per-student or per-question failure is recoverable and does not stop unrelated work.

## Generated files

Maven's normal build output is under `target/`. The demo output is under `demo/target/`, which is ignored by Git. `scores.template.csv` and all question expected outputs are fixtures and are never rewritten by the demo.

Before reporting a change, run:

```sh
mvn test
mvn package
git diff --check
```

## Grading real CS102 submissions with tester files

Menu option 3 ("Grade students") grades submissions shaped like the CS102 starter template (`RenameToYourStudentID/Q1/Q1a.java`, `Q2/persons.txt`, ...) with the instructor's tester classes.

### Tester contract

The configured testers folder holds one folder per question, named like the template's question folders:

```text
testers/
  Q1/Q1aTester.java            a class with a main method that exercises the student's Q1a class
  Q1/Q1aTester.expected.txt    the exact output Q1aTester must print
  Q1/Q1bTester.java
  Q1/Q1bTester.expected.txt
  Q2/...
```

- A tester is any `<Name>Tester.java` file in the question folder. It has no package and a `public static void main(String[] args)`.
- Its expected output is `<Name>Tester.expected.txt` in the same folder.
- Resource files the question needs (for example `persons.txt`) are taken from the student's question folder, as the template defines them.

### How a question is graded

For each student and each question the grader:

1. copies the student's files for that question and that question's testers into a fresh temporary build folder (deleted afterwards);
2. compiles them together with one bounded `javac` call;
3. runs each tester with a bounded `java` call (no shell), with the build folder as the working directory so relative names such as `persons.txt` resolve to the student's copy;
4. compares the tester's output with its expected file (text must match exactly; `\r\n` and `\n` line endings are treated as equal).

Each passing tester is worth **one mark**. The scoresheet column for a question (for example `Q1`) holds the number of testers that passed, so a question with two testers is out of 2. The scoresheet needs a `student` column and one column per question, and a row per student whose name matches the student's folder name.

If the student's work sits one folder down (for example `bob/RenameToYourStudentID/Q1`), grading still finds it; the submission check warns about it.

Each of these is reported with the student, folder and question and does not stop grading: compile error, runtime exception, timeout (default 2 seconds per process), output over the limit, wrong output, missing question folder, a question folder without `.java` files, a question folder without testers, and a tester without an expected file. A missing or unreadable testers folder, submissions folder or scoresheet is fatal and nothing is graded.

A question folder in the testers folder that holds no `*Tester.java` but does hold an `expected.txt` keeps using the older `Main.java` layout described under "Fixture layout"; this is what `demo` uses.

### Try it with the checked-in demo data

```sh
./compile.sh
sh demo/preflight/run-demo.sh
```

The script runs the real menu over `demo/preflight` (template, `testers`, `scores.csv`, `submissions`) from a throwaway folder and prints the filled scoresheet. The students show:

| Student | Q1 | Q2 | Q3 | Story |
| --- | --- | --- | --- | --- |
| alice | 2 | 2 | 2 | full pass |
| bob | 1 | 2 | 2 | partial pass (wrong greeting text); work inside a wrapper folder |
| carol | 0 | 2 | 0 | compile error (`q1a.java` is misnamed); Q3 folder missing |
| dave | 2 | 1 | 1 | runtime exception in Q2a; infinite loop in ShapeComparator (timeout) |
| erin | 2 | 0 | 2 | Q2b.java is empty so the Q2 testers do not compile |
| frank | | | | `frank.zip` is not extracted, so it is not graded |

To grade by hand, run `./run.sh` and enter absolute paths (the prompts reject relative ones): `<repo>/demo/preflight/RenameToYourStudentID` as the starting folder, `<repo>/demo/preflight/testers` as the testers folder and the absolute path of a copy of `demo/preflight/scores.csv` as the scoresheet (grading overwrites the scoresheet in place), choose option 3 and enter `<repo>/demo/preflight/submissions`, where `<repo>` is the output of `pwd` in the repository root. The run also writes `.grader-report.html` (scores shown as marks earned out of marks available, such as `PARTIAL (1/2)`) and appends to `.grader-error-history.jsonl` in the current folder.

## Submission check

The interactive menu's "Grade students" option first runs a read-only submission check. It compares each submission folder with the configured starting-folder template and reports missing, misnamed, extra, or malformed files. The check never changes submissions or scoresheets; grading proceeds afterward using the configured tester files and scoresheet.

For a synthetic check, run `sh demo/preflight/run-demo.sh` after running `./compile.sh`. The script uses a throwaway working directory and does not modify your own `config.json`.
