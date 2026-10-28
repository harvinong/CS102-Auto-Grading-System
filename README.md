# CS102-Auto-Grading-System
Automatically grade and collate students' answers for Java questions.

## Table of Content
- [Prerequisites](#Prerequisites)
- [Installation](#Installation)
- [Submission check](#Submission-check)

## Prerequisites
Make sure you install Java and Maven before proceeding to installation.

## Installation
Compile the file by executing ```compile.bat```, and then run the file by executing ```run.bat```.

## Submission check
"Grade students" (menu option 3) first runs a read-only submission check. It compares every folder in the Submissions Folder with the Starting Folder, **which it assumes is the instructor's starter template** (e.g. `RenameToYourStudentID`), and lists per student what is missing, misnamed, wrapped in an extra folder, empty, extra, or junk, each with a one-line fix.

It never compiles or runs submissions, never changes, moves or deletes files, never touches the scoresheet, and does not grade. "Ready" only means the layout matches the template, not that the answers are correct.

Try it on the synthetic data in `demo/preflight` (build first with `compile.bat`, or `mvn clean dependency:copy-dependencies package`):
```
sh demo/preflight/run-demo.sh
```
If the Starting Folder is the folder *above* the template, the check stops with the exact folder to choose instead. A `.zip` sitting next to the folder it was extracted into is noted, not counted as a second student.

The script runs from a throwaway working directory, so it never creates or overwrites your own `config.json`.
