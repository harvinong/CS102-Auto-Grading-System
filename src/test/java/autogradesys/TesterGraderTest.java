package autogradesys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import autogradesys.grading.ErrorHistory;
import autogradesys.grading.ErrorReporter;
import autogradesys.grading.GradeResult;
import autogradesys.grading.JavaGrader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Grades template-shaped submissions with instructor Tester classes through the public JavaGrader entry point. */
class TesterGraderTest {
    // Fresh temp folder per test; holds the testers, submissions, scoresheet and error history.
    @TempDir
    Path root;

    // The testers folder: one folder per question, as in the CS102 template.
    private Path testers;
    // The submissions folder: one folder per student.
    private Path submissions;
    // The scoresheet the grader fills in.
    private Path scores;
    // Collects every diagnostic so tests can assert on categories and context.
    private ErrorReporter reporter;

    @BeforeEach
    void setUp() throws IOException {
        // Tester Q1/SquareTester prints square(3); its expected output is 9.
        testers = root.resolve("testers");
        write(testers, "Q1/SquareTester.java",
                "public class SquareTester { public static void main(String[] a) {"
                        + " System.out.println(\"square=\" + Square.of(3)); } }");
        write(testers, "Q1/SquareTester.expected.txt", "square=9\n");
        // Scoresheet with a Q1 column and one row per student used by the tests.
        submissions = root.resolve("submissions");
        scores = root.resolve("scores.csv");
        Files.writeString(scores, "student,Q1\nalice,\nbob,\ncarol,\n");
        // Reporter writes to a throwaway buffer so test output stays quiet.
        reporter = new ErrorReporter(new ErrorHistory(root.resolve("history.jsonl")),
                new PrintStream(new ByteArrayOutputStream()));
    }

    // Writes a file, creating its parent folders.
    private static void write(Path base, String relative, String content) throws IOException {
        Path file = base.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    // Grades with a short deadline so the timeout test finishes quickly but javac still has time to start.
    private GradeResult grade() {
        return JavaGrader.grade(submissions, testers, scores, Duration.ofSeconds(5), reporter);
    }

    // The categories of every recorded diagnostic, in order.
    private List<String> categories() {
        return reporter.records().stream().map(record -> record.category()).toList();
    }

    @Test
    void passingSubmissionEarnsTheMark() throws IOException {
        // A correct Square class.
        write(submissions, "alice/Q1/Square.java",
                "public class Square { static int of(int n) { return n * n; } }");

        GradeResult result = grade();

        // One tester, one mark, no diagnostics, and the scoresheet itself is left unchanged
        // (ScoreRecorder.exportResults writes the marks into a dated copy instead).
        assertEquals(1, result.scores().get("alice").get("Q1"));
        assertEquals(List.of(), categories());
        assertEquals("student,Q1\nalice,\nbob,\ncarol,\n", Files.readString(scores));
    }

    @Test
    void wrongOutputScoresZeroWithContext() throws IOException {
        // Square returns the wrong value.
        write(submissions, "alice/Q1/Square.java",
                "public class Square { static int of(int n) { return n + n; } }");

        GradeResult result = grade();

        // No mark, one mismatch diagnostic that names the student and question.
        assertEquals(0, result.scores().get("alice").get("Q1"));
        assertEquals(List.of("output-mismatch"), categories());
        assertEquals("alice", reporter.records().get(0).student());
        assertEquals("Q1", reporter.records().get(0).question());
        assertTrue(reporter.records().get(0).canContinue());
    }

    @Test
    void compileErrorScoresZeroAndGradingContinues() throws IOException {
        // alice's file does not compile; bob's is fine, so bob must still be graded.
        write(submissions, "alice/Q1/Square.java", "public class Square { static int of(int n) { return n * ; } }");
        write(submissions, "bob/Q1/Square.java",
                "public class Square { static int of(int n) { return n * n; } }");

        GradeResult result = grade();

        assertEquals(0, result.scores().get("alice").get("Q1"));
        assertEquals(1, result.scores().get("bob").get("Q1"));
        assertTrue(categories().contains("compile"));
        assertFalse(result.fatal());
    }

    @Test
    void timeoutIsReportedAndTerminated() throws IOException {
        // Square never returns.
        write(submissions, "alice/Q1/Square.java",
                "public class Square { static int of(int n) { while (true) { } } }");
        reporter = new ErrorReporter(new ErrorHistory(root.resolve("history.jsonl")),
                new PrintStream(new ByteArrayOutputStream()));

        GradeResult result = JavaGrader.grade(submissions, testers, scores, Duration.ofSeconds(3), reporter);

        assertEquals(0, result.scores().get("alice").get("Q1"));
        assertEquals(List.of("timeout"), categories());
    }

    @Test
    void missingTesterIsReportedAsRecoverable() throws IOException {
        // A Q2 folder in the testers folder with no Tester classes and no expected.txt.
        Files.createDirectories(testers.resolve("Q2"));
        write(submissions, "alice/Q1/Square.java",
                "public class Square { static int of(int n) { return n * n; } }");
        write(submissions, "alice/Q2/Other.java", "public class Other { }");
        Files.writeString(scores, "student,Q1,Q2\nalice,,\n");

        GradeResult result = grade();

        // Q1 still earns its mark; Q2 scores zero with a missing-tester diagnostic.
        assertEquals(1, result.scores().get("alice").get("Q1"));
        assertEquals(0, result.scores().get("alice").get("Q2"));
        assertEquals(List.of("missing-tester"), categories());
        assertFalse(result.fatal());
    }

    @Test
    void missingExpectedFileOnlyAffectsThatTester() throws IOException {
        // A second tester with no expected file: the first tester must still be graded.
        write(testers, "Q1/OtherTester.java",
                "public class OtherTester { public static void main(String[] a) { System.out.println(1); } }");
        write(submissions, "alice/Q1/Square.java",
                "public class Square { static int of(int n) { return n * n; } }");

        GradeResult result = grade();

        assertEquals(1, result.scores().get("alice").get("Q1"));
        assertEquals(List.of("missing-expected"), categories());
    }

    @Test
    void missingQuestionFolderAndMissingFilesAreRecoverable() throws IOException {
        // alice has no Q1 folder at all; bob's Q1 folder holds no Java.
        write(submissions, "alice/readme.txt", "no questions here");
        write(submissions, "bob/Q1/notes.txt", "forgot the code");

        GradeResult result = grade();

        assertEquals(0, result.scores().get("alice").get("Q1"));
        assertEquals(0, result.scores().get("bob").get("Q1"));
        assertTrue(categories().contains("missing-question"));
        assertTrue(categories().contains("missing-file"));
        assertFalse(result.fatal());
    }

    @Test
    void wrapperFolderAndResourceFilesAreHandled() throws IOException {
        // The tester reads data.txt from the working folder; the student zipped the template wrapper folder.
        write(testers, "Q1/SquareTester.java",
                "public class SquareTester { public static void main(String[] a) throws Exception {"
                        + " System.out.println(Square.of(\"data.txt\")); } }");
        write(testers, "Q1/SquareTester.expected.txt", "hello\n");
        write(submissions, "alice/RenameToYourStudentID/Q1/Square.java",
                "public class Square { static String of(String f) throws Exception {"
                        + " return java.nio.file.Files.readString(java.nio.file.Path.of(f)).trim(); } }");
        write(submissions, "alice/RenameToYourStudentID/Q1/data.txt", "hello\n");

        GradeResult result = grade();

        assertEquals(1, result.scores().get("alice").get("Q1"));
        assertEquals(List.of(), categories());
    }

    @Test
    void marksCountPassingTestersPerQuestion() throws IOException {
        // Two testers; the student satisfies only the first.
        write(testers, "Q1/CubeTester.java",
                "public class CubeTester { public static void main(String[] a) {"
                        + " System.out.println(Square.cube(2)); } }");
        write(testers, "Q1/CubeTester.expected.txt", "8\n");
        write(submissions, "alice/Q1/Square.java",
                "public class Square { static int of(int n) { return n * n; }"
                        + " static int cube(int n) { return 0; } }");

        GradeResult result = grade();

        assertEquals(1, result.scores().get("alice").get("Q1"));
        // Q1 is worth one mark per tester, so the report shows 1/2 rather than a pass.
        assertEquals(2, result.marksFor("Q1"));
        assertEquals(List.of("output-mismatch"), categories());
    }

    @Test
    void questionWithoutTestersIsWorthNoMarks() throws IOException {
        // Same setup as missingTesterIsReportedAsRecoverable: Q2 has no testers, so nobody can earn marks for it.
        Files.createDirectories(testers.resolve("Q2"));
        write(submissions, "alice/Q1/Square.java",
                "public class Square { static int of(int n) { return n * n; } }");
        Files.writeString(scores, "student,Q1,Q2\nalice,,\n");

        GradeResult result = grade();

        assertEquals(1, result.marksFor("Q1"));
        assertEquals(0, result.marksFor("Q2"));
    }
}
