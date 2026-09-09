#!/usr/bin/env python3
"""Small, local-first CS102 grading runner with structured error reporting."""
from __future__ import annotations

import argparse
import csv
import json
import os
import subprocess
import sys
import tempfile
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Optional


@dataclass(frozen=True)
class ErrorRecord:
    stage: str
    category: str
    severity: str
    message: str
    guidance: str
    submission: Optional[str] = None
    student: Optional[str] = None
    question: Optional[str] = None
    can_continue: bool = True


class ErrorHistory:
    """Append-only JSON Lines history, deliberately easy to inspect and share."""

    def __init__(self, path: Path):
        self.path = path

    def append(self, record: ErrorRecord) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with self.path.open("a", encoding="utf-8") as stream:
            stream.write(json.dumps(asdict(record), sort_keys=True) + "\n")


class ErrorReporter:
    def __init__(self, history: ErrorHistory, output=None):
        self.history = history
        self.output = output or sys.stderr
        self.records: list[ErrorRecord] = []

    def error(self, **kwargs) -> ErrorRecord:
        record = ErrorRecord(**kwargs)
        self.records.append(record)
        self.history.append(record)
        context = " ".join(f"{key}={value}" for key, value in (
            ("student", record.student), ("submission", record.submission),
            ("question", record.question)) if value)
        print(f"[{record.severity.upper()}] {record.stage}/{record.category}: {record.message}", file=self.output)
        if context:
            print(f"  context: {context}", file=self.output)
        print(f"  guidance: {record.guidance}", file=self.output)
        return record

    @property
    def fatal(self) -> bool:
        return any(not record.can_continue for record in self.records)


def _failure(reporter, stage, category, message, guidance, **context):
    reporter.error(stage=stage, category=category, severity="error", message=message,
                   guidance=guidance, **context)


def validate_submissions(root: Path, questions: list[str], reporter: ErrorReporter) -> list[tuple[str, Path]]:
    if not root.is_dir():
        _failure(reporter, "submission", "filesystem", f"Submission directory does not exist: {root}",
                 "Choose an existing directory containing one directory per student.", can_continue=False)
        return []
    students = [(entry.name, entry) for entry in sorted(root.iterdir()) if entry.is_dir()]
    if not students:
        _failure(reporter, "submission", "structure", "No student submission directories were found.",
                 "Create one directory per student inside the submissions directory.", can_continue=False)
    for student, directory in students:
        for question in questions:
            qdir = directory / question
            if not qdir.is_dir():
                _failure(reporter, "submission", "missing-question",
                         f"Question directory is missing: {question}",
                         "Add the question directory and its Java source, or check the question name.",
                         student=student, submission=str(directory), question=question)
                continue
            sources = sorted(qdir.glob("*.java"))
            if not sources:
                _failure(reporter, "submission", "malformed-structure",
                         "Question directory contains no .java source files.",
                         "Put the submitted Java source directly in the question directory.",
                         student=student, submission=str(directory), question=question)
            for source in sources:
                if source.name != "Main.java":
                    _failure(reporter, "submission", "naming",
                             f"Unexpected source name: {source.name}",
                             "Rename the entry-point source to Main.java.", student=student,
                             submission=str(directory), question=question)
    return students


def read_questions(root: Path, reporter: ErrorReporter) -> list[str]:
    if not root.is_dir():
        _failure(reporter, "submission", "filesystem", f"Questions directory does not exist: {root}",
                 "Choose an existing directory containing one directory per question.", can_continue=False)
        return []
    result = [entry.name for entry in sorted(root.iterdir()) if entry.is_dir()]
    if not result:
        _failure(reporter, "submission", "structure", "No question directories were found.",
                 "Create question directories containing expected.txt fixtures.", can_continue=False)
    return result


def run_question(student: str, submission: Path, question: str, question_root: Path,
                 reporter: ErrorReporter, timeout: float) -> int:
    qdir, spec = submission / question, question_root / question
    source = qdir / "Main.java"
    if not source.exists():
        return 0
    with tempfile.TemporaryDirectory(prefix="cs102-") as build:
        try:
            compile_result = subprocess.run(["javac", "-d", build, str(source)], text=True,
                                            capture_output=True, timeout=timeout)
        except subprocess.TimeoutExpired:
            _failure(reporter, "question", "timeout", f"Compilation exceeded {timeout:g} second(s).",
                     "Check the submission and retry with a valid Java source.", student=student,
                     submission=str(submission), question=question)
            return 0
        except OSError as exc:
            _failure(reporter, "question", "filesystem", f"Could not run javac: {exc}",
                     "Install a JDK and ensure javac is on PATH.", student=student,
                     submission=str(submission), question=question)
            return 0
        if compile_result.returncode:
            _failure(reporter, "question", "compile", compile_result.stderr.strip() or "javac failed",
                     "Fix the Java compiler errors and resubmit.", student=student,
                     submission=str(submission), question=question)
            return 0
        input_path, expected_path = spec / "input.txt", spec / "expected.txt"
        try:
            input_data = input_path.read_text(encoding="utf-8") if input_path.exists() else ""
            expected = expected_path.read_text(encoding="utf-8") if expected_path.exists() else ""
        except OSError as exc:
            _failure(reporter, "question", "input-output", str(exc),
                     "Ensure the question fixture files are readable.", student=student,
                     submission=str(submission), question=question)
            return 0
        try:
            result = subprocess.run(["java", "-cp", build, "Main"], input=input_data, text=True,
                                    capture_output=True, timeout=timeout)
        except subprocess.TimeoutExpired:
            _failure(reporter, "question", "timeout", f"Program exceeded {timeout:g} second(s).",
                     "Check for an infinite loop or reduce the program's runtime.", student=student,
                     submission=str(submission), question=question)
            return 0
        except OSError as exc:
            _failure(reporter, "question", "filesystem", f"Could not run java: {exc}",
                     "Install a JDK and ensure java is on PATH.", student=student,
                     submission=str(submission), question=question)
            return 0
        if result.returncode:
            _failure(reporter, "question", "runtime", result.stderr.strip() or
                     f"Program exited with status {result.returncode}.",
                     "Fix the runtime exception and resubmit.", student=student,
                     submission=str(submission), question=question)
            return 0
        if result.stdout != expected:
            _failure(reporter, "question", "output-mismatch",
                     f"Expected {expected!r}, received {result.stdout!r}.",
                     "Compare whitespace, line endings, and output text with expected.txt.",
                     student=student, submission=str(submission), question=question)
            return 0
        return 1


def grade_scoresheet(path: Path, scores: dict[str, dict[str, int]], reporter: ErrorReporter) -> bool:
    try:
        with path.open(newline="", encoding="utf-8") as stream:
            reader = csv.DictReader(stream)
            fieldnames = list(reader.fieldnames or [])
            rows = list(reader)
    except (OSError, csv.Error) as exc:
        _failure(reporter, "score", "scoresheet-read", f"Could not read scoresheet: {exc}",
                 "Check that the CSV exists and is readable.", can_continue=False)
        return False
    if not fieldnames or "student" not in fieldnames:
        _failure(reporter, "score", "scoresheet-row", "Scoresheet must have a student column.",
                 "Add a header named student to the scoresheet.", can_continue=False)
        return False
    for row in rows:
        student = row.get("student", "")
        if not student:
            _failure(reporter, "score", "scoresheet-row", "A scoresheet row has no student name.",
                     "Fill in the student column.", can_continue=True)
            continue
        for question, score in scores.get(student, {}).items():
            if question in row:
                row[question] = str(score)
            else:
                _failure(reporter, "score", "scoresheet-update", f"Question column is missing: {question}",
                         "Add the question column before grading.", student=student)
    temporary = path.with_suffix(path.suffix + ".tmp")
    try:
        with temporary.open("w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(stream, fieldnames=fieldnames)
            writer.writeheader(); writer.writerows(rows)
        os.replace(temporary, path)
    except OSError as exc:
        if temporary.exists(): temporary.unlink()
        _failure(reporter, "score", "scoresheet-write", f"Could not write scoresheet: {exc}",
                 "Check permissions and ensure the scoresheet is not read-only.", can_continue=False)
        return False
    return True


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Run the local CS102 Java grader")
    parser.add_argument("--submissions-dir"); parser.add_argument("--questions-dir"); parser.add_argument("--scoresheet")
    parser.add_argument("--history", default=".grader-error-history.jsonl")
    parser.add_argument("--timeout", type=float, default=2.0)
    parser.add_argument("--non-interactive", action="store_true")
    args = parser.parse_args(argv)
    def obtain(value, label):
        if value: return value
        if args.non_interactive: return ""
        return input(f"{label}: ").strip()
    submissions, questions, scoresheet = (Path(obtain(value, label)) for value, label in
                                          ((args.submissions_dir, "Submissions directory"),
                                           (args.questions_dir, "Questions directory"),
                                           (args.scoresheet, "Scoresheet CSV")))
    reporter = ErrorReporter(ErrorHistory(Path(args.history)))
    question_names = read_questions(questions, reporter)
    students = validate_submissions(submissions, question_names, reporter)
    scores = {}
    for student, directory in students:
        scores[student] = {}
        for question in question_names:
            scores[student][question] = run_question(student, directory, question, questions, reporter, args.timeout)
    if scoresheet:
        grade_scoresheet(scoresheet, scores, reporter)
    elif args.non_interactive:
        _failure(reporter, "score", "scoresheet-read", "No scoresheet was provided.",
                 "Pass --scoresheet or run interactively.", can_continue=False)
    print(f"Completed: {len(students)} submission(s), {len(reporter.records)} reported issue(s).")
    if not args.non_interactive:
        action = input("Next action [exit]: ").strip().lower()
        if action in {"again", "run"}:
            return main(argv)
    return 1 if reporter.fatal else 0


if __name__ == "__main__":
    raise SystemExit(main())
