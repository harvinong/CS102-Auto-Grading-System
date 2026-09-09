#!/usr/bin/env python3
import csv
import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from grader import ErrorHistory, ErrorReporter, grade_scoresheet, main, run_question, validate_submissions


class ErrorReportingTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.history = self.root / "history.jsonl"
        self.output = io.StringIO()
        self.reporter = ErrorReporter(ErrorHistory(self.history), self.output)

    def tearDown(self):
        self.temp.cleanup()

    def test_filesystem_error_is_fatal_and_persisted(self):
        validate_submissions(self.root / "missing", ["q1"], self.reporter)
        self.assertTrue(self.reporter.fatal)
        record = json.loads(self.history.read_text().splitlines()[0])
        self.assertEqual(record["category"], "filesystem")
        self.assertIn("Submission directory", self.output.getvalue())

    def test_malformed_and_missing_question_include_context(self):
        student = self.root / "submissions" / "alice"
        (student / "q1").mkdir(parents=True)
        validate_submissions(self.root / "submissions", ["q1", "q2"], self.reporter)
        records = [json.loads(line) for line in self.history.read_text().splitlines()]
        self.assertEqual({r["category"] for r in records}, {"malformed-structure", "missing-question"})
        self.assertTrue(all(r["student"] == "alice" for r in records))

    def test_scoresheet_updates_and_history_is_append_only(self):
        sheet = self.root / "scores.csv"
        sheet.write_text("student,q1\nalice,\n", encoding="utf-8")
        self.assertTrue(grade_scoresheet(sheet, {"alice": {"q1": 1}}, self.reporter))
        self.assertEqual(sheet.read_text(), "student,q1\nalice,1\n")
        self.assertFalse(self.reporter.records)
        self.reporter.error(stage="question", category="runtime", severity="error", message="boom",
                            guidance="fix", student="alice", question="q1", can_continue=True)
        self.assertEqual(len(self.history.read_text().splitlines()), 1)

    @unittest.skipUnless(__import__('shutil').which('javac') and __import__('shutil').which('java'),
                         'JDK is required for question execution tests')
    def test_question_failures_are_contextual_and_recoverable(self):
        cases = {
            "compile": "public class Main { this is not Java; }",
            "runtime": "public class Main { public static void main(String[] a) { throw new RuntimeException(\"boom\"); } }",
            "timeout": "public class Main { public static void main(String[] a) { while (true) {} } }",
            "output-mismatch": "public class Main { public static void main(String[] a) { System.out.print(\"wrong\"); } }",
        }
        for category, source in cases.items():
            with self.subTest(category=category):
                submissions = self.root / category / "alice" / "q1"
                spec = self.root / category / "questions" / "q1"
                submissions.mkdir(parents=True); spec.mkdir(parents=True)
                (submissions / "Main.java").write_text(source)
                (spec / "expected.txt").write_text("right")
                run_question("alice", self.root / category / "alice", "q1",
                             self.root / category / "questions", self.reporter,
                             .1 if category == "timeout" else 2)
        categories = {record.category for record in self.reporter.records}
        self.assertTrue({"compile", "runtime", "timeout", "output-mismatch"}.issubset(categories))
        self.assertTrue(all(record.student == "alice" and record.question == "q1"
                             for record in self.reporter.records))

    def test_scoresheet_bad_header_is_fatal(self):
        sheet = self.root / "scores.csv"
        sheet.write_text("name,q1\nalice,\n", encoding="utf-8")
        self.assertFalse(grade_scoresheet(sheet, {"alice": {"q1": 1}}, self.reporter))
        self.assertTrue(self.reporter.fatal)
        self.assertIn("student column", self.output.getvalue())

    def test_clean_non_interactive_path(self):
        submissions = self.root / "submissions"; questions = self.root / "questions"
        (submissions / "alice" / "q1").mkdir(parents=True)
        (questions / "q1").mkdir(parents=True)
        (submissions / "alice" / "q1" / "Main.java").write_text(
            "public class Main { public static void main(String[] a) { System.out.print(\"ok\"); } }")
        (questions / "q1" / "expected.txt").write_text("ok")
        sheet = self.root / "scores.csv"; sheet.write_text("student,q1\nalice,\n")
        code = main(["--submissions-dir", str(submissions), "--questions-dir", str(questions),
                     "--scoresheet", str(sheet), "--history", str(self.history), "--non-interactive"])
        self.assertEqual(code, 0)
        self.assertEqual(sheet.read_text(), "student,q1\nalice,1\n")


if __name__ == "__main__":
    unittest.main()
