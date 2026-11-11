# CS102 Auto-Grading System

A console application that grades CS102 lab-test submissions on the instructor's own computer. It unzips the students' submissions into a working copy, checks them against the starter template, fixes common mistakes, runs the instructor's tester files on each question with a time and output limit, and writes the marks into the eLearn scoresheet. It needs no network access or external service.

## Index

- [Safety and scope](#safety-and-scope)
- [Prerequisites](#prerequisites)
- [Compile and run](#compile-and-run)
- [Build and test with Maven](#build-and-test-with-maven)
- [Grading the CS102 project files](#grading-the-cs102-project-files)
- [How a question is graded](#how-a-question-is-graded)
- [Submission check and fixes](#submission-check-and-fixes)
- [Scoresheet formats](#scoresheet-formats)
- [Generated files](#generated-files)
- [The JUnit grader (developer tool)](#the-junit-grader-developer-tool)
- [Assets Credit](#assets-credit)

## Safety and scope

The grader compiles and runs submitted Java code directly on the instructor's machine, as the instructor's user, with no sandbox or container isolation.
It is intended for trusted local fixtures and reviewed coursework submissions, not untrusted or anonymous code.
The per-run timeout and combined stdout/stderr limit (2 seconds and 1 MiB by default) stop runaway or flooding programs, but they are resource limits, not security isolation.
Running untrusted submissions safely would require OS or container isolation, which is out of scope for this MVP.

## Prerequisites

- JDK 17 or newer, with both `java` and `javac` on `PATH` (the grader runs `javac` and `java` on the students' code)
- Maven 3.9 or newer, only for building and running the tests

Check the tools with:

```sh
java -version
javac -version
```

## Compile and run

From the repository root:

```sh
compile.bat      (Windows)     or     ./compile.sh     (macOS/Linux)
run.bat          (Windows)     or     ./run.sh         (macOS/Linux)
```

`compile` puts the class files in `classes`, and `run` starts the menu. Both use only the jars in `lib`, so no Maven or network is needed. `run.bat --debug` shows a full stack trace if something unexpected goes wrong.

## Build and test with Maven

```sh
mvn clean test
mvn package
java -jar target/CS102-Auto-Grading-System-1.0-SNAPSHOT.jar
```

The packaged jar bundles its runtime dependencies.

## Grading the CS102 project files

1. Run `run.bat`. On the first run it asks for:
   - **Starting Folder**: the blank template, e.g. `...\Project Files\RenameToYourUsername`
   - **Tester Files Folder**: e.g. `...\Project Files\Tester-Files`
   - **Scoresheet File**: a copy of the eLearn export, e.g. `...\Project Files\IS442-ScoreSheet.csv`
   - **Grading timeout** and **Output limit**: press Enter to keep the defaults (2 seconds, 1 MiB)

   Paths must be absolute. The answers are saved in `config.json`; option 2 changes them.
2. Choose **3. Grade students** and enter the submissions folder, e.g. `...\Project Files\student-submission`. The zips (`2023-2024-<username>.zip`) are extracted into a working copy in `cache`; the originals are never changed.
3. The submission check lists each student as ready or needing attention. Choose **1. Fix common mistakes automatically**, then **3. Start grading**.
4. The marks are written into the scoresheet. **4. View Recent HTML Report** opens the report of every score and problem.

With the CS102 project files this gives the totals in `groundtruth.txt`: ping.lee.2023 20, chee.teo.2022 12, david.2024 10, fen.lai.2022 10, jing.lim.2021 0 and xing.yan.2023 1.

## How a question is graded

The instructors hand out loose testers (`Q1aTester.java`, `Q1bTester.java`, `Q2aTester.java`, `Q2bTester.java`, `Q3Tester.java`). Each is matched to its question by name, so `Q1aTester` and `Q1bTester` grade the student's `Q1` folder. A testers folder with one sub-folder per question (`Q1/`, `Q2/`, ...) also works.

For each student, question and tester the grader:

1. copies the student's files for that question into a fresh temporary build folder, together with the tester. Class files that come only in compiled form with the template (such as `DataException.class` and `Shape.class`) are kept; a `.class` file next to its own `.java` is a stale copy and is left out so the source is compiled;
2. compiles only that tester with `javac`, which also compiles the student classes it uses, so a broken `Q1b.java` does not cost the marks for `Q1a`;
3. runs the tester with `java`, with the build folder as the working directory so names such as `personstester.txt` find the student's copy;
4. reads the score the tester prints as its last line (for example `3.0`). A tester that has a `<Name>Tester.expected.txt` next to it is instead marked by comparing its output with that file (one mark).

A question is worth the sum of its testers' marks (each `score += 1` in a tester counts as one). Compile errors, exceptions, timeouts and output over the limit are reported with the student, folder and question, and grading carries on. If a tester is stopped by the timeout, the test cases it had already passed still count. No process is run through a shell, and every build folder is deleted afterwards.

## Submission check and fixes

Before grading, a read-only check compares each submission with the starting-folder template and reports missing, misnamed, extra or wrapped files, unextracted zips and incomplete headers. "Fix common mistakes automatically" then repairs the working copy: it moves work out of an extra folder (such as `RenameToYourStudentID/`), fixes misnamed folders, restores missing template files and fills in empty headers. A copy of the blank template in the submissions folder is listed as `not graded`.

## Scoresheet formats

- **eLearn export** (as in `IS442-ScoreSheet.csv`): rows are matched by the `Username` column (`#ping.lee.2023`), and each student's total is written into `Calculated Final Grade Numerator`. Everything else, including the `#` markers and Windows line endings, is kept.
- **Team layout**: a `student` column and one column per question (`Q1`, `Q2`, ...), filled with that question's marks.

Rows for students who were not graded are left unchanged, and a graded student with no row is reported. The file is replaced in one step, so a failed write never leaves a half-written sheet. Each run also exports the marks per question to a new timestamped CSV.

## Generated files

All of these are written to the folder you run the program from and are ignored by Git:

- `config.json`: the saved settings
- `cache/`: the working copy of the submissions (option 5 clears it)
- `.grader-report.html`: the latest report
- `.grader-error-history.jsonl`: every problem found, one JSON object per line, added to on each run
- `<date>_<time>.csv`: the per-question export of each run

## The JUnit grader (developer tool)

`GradingEntry`, `StudentFileExec` and `QuestionTests` are a second grader that uses JUnit's dynamic tests instead of the tester files. Each test case is defined in `QuestionTests` with a `MethodSpec` (method name and parameter types) and `TestCase`s (arguments and the expected value or exception). It is not part of the menu; run it on a working copy with:

```sh
java -cp "classes;lib/*" autogradesys.GradingEntry cache\student-submission
```

(use `:` instead of `;` on macOS/Linux). It prints each student's passed/total per sub-question.

## Assets Credit

The [big title ASCII art](./assets/title.txt) is generated from [www.asciiart.eu](https://www.asciiart.eu/text-to-ascii-art) With "ANSI Shadow" font and "Box drawing light" border. Everything else ([Welcome message](./assets/welcome.md) and the cat in the title) are self made.