package autogradesys;

import autogradesys.grading.ErrorHistory;
import autogradesys.grading.ErrorRecord;
import autogradesys.grading.ErrorReporter;
import autogradesys.grading.GradeResult;
import autogradesys.grading.HtmlReportWriter;
import autogradesys.grading.JavaGrader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaGraderTest {
    @TempDir
    Path temporary;

    @Test
    void gradesSuccessAndContinuesThroughEveryRecoverableQuestionFailure() throws Exception {
        requireJdk();
        Path questions = createQuestions();
        Path submissions = temporary.resolve("submissions");
        createSource(submissions.resolve("alice/q1/Main.java"), passingSource("ok"));
        createSource(submissions.resolve("alice/q2/Main.java"), passingSource("next"));

        createSource(submissions.resolve("compile/q1/Main.java"), "public class Main { not Java; }");
        createSource(submissions.resolve("compile/q2/Main.java"), passingSource("next"));

        createSource(submissions.resolve("runtime/q1/Main.java"),
                "public class Main { public static void main(String[] a) { throw new RuntimeException(\"boom\"); } }");
        createSource(submissions.resolve("runtime/q2/Main.java"), passingSource("next"));

        createSource(submissions.resolve("mismatch/q1/Main.java"), passingSource("wrong"));
        createSource(submissions.resolve("mismatch/q2/Main.java"), passingSource("next"));

        Path pidFile = temporary.resolve("timeout.pid");
        String escapedPidFile = pidFile.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        createSource(submissions.resolve("timeout/q1/Main.java"),
                "import java.nio.file.*; public class Main { public static void main(String[] a) throws Exception {"
                        + " Files.writeString(Path.of(\"" + escapedPidFile + "\"), Long.toString(ProcessHandle.current().pid()));"
                        + " while (true) { Thread.sleep(10); } } }");
        createSource(submissions.resolve("timeout/q2/Main.java"), passingSource("next"));

        createSource(submissions.resolve("missing/q2/Main.java"), passingSource("next"));
        createSource(submissions.resolve("malformed/q1/Answer.java"), passingSource("ok"));
        createSource(submissions.resolve("malformed/q2/Main.java"), passingSource("next"));

        Path scoresheet = temporary.resolve("scores.csv");
        Files.writeString(scoresheet,
                "student,q1,q2\nlegacy,9,8\nalice,,\ncompile,,\nruntime,,\nmismatch,,\ntimeout,,\nmissing,,\nmalformed,,\n,,\n",
                StandardCharsets.UTF_8);
        ErrorReporter reporter = reporter("errors.jsonl");

        GradeResult result = JavaGrader.grade(submissions, questions, scoresheet,
                Duration.ofSeconds(1), reporter);

        Set<String> categories = new HashSet<>();
        reporter.records().forEach(record -> categories.add(record.category()));
        assertTrue(categories.containsAll(Set.of("compile", "runtime", "timeout", "output-mismatch",
                "missing-question", "malformed-structure", "scoresheet-row")));
        assertEquals(7, result.studentCount());
        assertEquals(1, result.scores().get("alice").get("q1"));
        assertEquals(1, result.scores().get("compile").get("q2"));
        assertEquals(0, result.scores().get("missing").get("q1"));
        assertTrue(reporter.records().stream().allMatch(record -> record.canContinue()));
        assertTrue(Files.readString(scoresheet).contains("legacy,9,8"));
        assertTrue(Files.readString(scoresheet).contains("compile,0,1"));

        List<String> historyLines = Files.readAllLines(temporary.resolve("errors.jsonl"));
        assertEquals(reporter.records().size(), historyLines.size());
        assertTrue(historyLines.stream().anyMatch(line -> line.contains("\"category\":\"timeout\"")
                && line.contains("\"student\":\"timeout\"")
                && line.contains("\"question\":\"q1\"")));
        long pid = Long.parseLong(Files.readString(pidFile));
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false),
                "a timed-out student process must be terminated");
    }

    @Test
    void htmlReportUsesCurrentRunScoresAndEscapesDynamicDiagnostics() throws Exception {
        Path report = temporary.resolve("report.html");
        GradeResult result = new GradeResult(1, 1, false,
                Map.of("<student>", Map.of("q1", 1, "q2", 0)));
        ErrorRecord error = new ErrorRecord("question", "compile", "error",
                "bad <source> & \"quote\"", "Fix & retry", "<student>",
                "/tmp/<submission>", "q2", true);
        HtmlReportWriter.write(report, result, List.of(error));
        String first = Files.readString(report);
        assertTrue(first.contains("&lt;student&gt;"));
        assertTrue(first.contains("bad &lt;source&gt; &amp; &quot;quote&quot;"));
        assertTrue(first.contains("PASS"));
        assertTrue(first.contains("FAIL"));
        assertFalse(first.contains("<student>"));

        HtmlReportWriter.write(report, new GradeResult(1, 0, false,
                Map.of("second", Map.of("q1", 1))), List.of());
        String second = Files.readString(report);
        assertTrue(second.contains("second"));
        assertFalse(second.contains("<student>"), "a report must not include prior runs");
    }

    @Test
    void htmlReportWriteFailureIsClear() throws Exception {
        Path parentFile = temporary.resolve("not-a-directory");
        Files.writeString(parentFile, "occupied", StandardCharsets.UTF_8);
        IOException failure = assertThrows(IOException.class, () -> HtmlReportWriter.write(
                parentFile.resolve("report.html"), new GradeResult(0, 0, false, Map.of()), List.of()));
        assertTrue(failure.getMessage() != null && !failure.getMessage().isBlank());
    }

    @Test
    void scoresheetUpdateIsAtomicInShapeAndKeepsPriorRows() throws Exception {
        Path scoresheet = temporary.resolve("scores.csv");
        Files.writeString(scoresheet, "student,q1\nlegacy,9\nalice,old\n", StandardCharsets.UTF_8);
        ErrorReporter reporter = reporter("scoresheet-errors.jsonl");

        assertTrue(autogradesys.grading.CsvScoresheet.update(scoresheet,
                Map.of("alice", Map.of("q1", 1, "q2", 0)), reporter));
        String updated = Files.readString(scoresheet);
        assertTrue(updated.contains("legacy,9"));
        assertTrue(updated.contains("alice,1"));
        assertTrue(reporter.records().stream().anyMatch(record -> record.category().equals("scoresheet-update")));

        String beforeBadHeader = updated;
        Path bad = temporary.resolve("bad.csv");
        Files.writeString(bad, "name,q1\nalice,\n", StandardCharsets.UTF_8);
        ErrorReporter badReporter = reporter("bad-errors.jsonl");
        assertFalse(autogradesys.grading.CsvScoresheet.update(bad,
                Map.of("alice", Map.of("q1", 1)), badReporter));
        assertEquals("name,q1\nalice,\n", Files.readString(bad));
        assertEquals(beforeBadHeader, updated);
    }

    @Test
    void appRunsAPathBasedNonInteractiveSession() throws Exception {
        requireJdk();
        Path questions = createQuestions();
        Path submissions = temporary.resolve("cli-submissions");
        createSource(submissions.resolve("alice/q1/Main.java"), passingSource("ok"));
        createSource(submissions.resolve("alice/q2/Main.java"), passingSource("next"));
        Path scoresheet = temporary.resolve("cli-scores.csv");
        Files.writeString(scoresheet, "student,q1,q2\nalice,,\n", StandardCharsets.UTF_8);
        Path history = temporary.resolve("cli-history.jsonl");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        int status = App.run(new String[] {"--non-interactive", "--submissions-dir", submissions.toString(),
                "--questions-dir", questions.toString(), "--scoresheet", scoresheet.toString(),
                "--history", history.toString(), "--timeout-ms", "2000"},
                new PrintStream(output, true, StandardCharsets.UTF_8));

        assertEquals(0, status);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("PASS student=alice"));
        assertEquals("student,q1,q2\nalice,1,1\n", Files.readString(scoresheet));
        assertTrue(Files.notExists(history));
    }

    @Test
    void checkedInDemoCommandUsesFixturesAndGeneratedOutputs() throws Exception {
        requireJdk();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        // Keep the Maven test boundary aligned with the command users run from the repository root.
        int status = App.run(new String[] {"demo"}, new PrintStream(output, true, StandardCharsets.UTF_8));
        String text = output.toString(StandardCharsets.UTF_8);
        assertEquals(0, status);
        assertTrue(text.contains("question/compile"));
        assertTrue(text.contains("question/timeout"));
        assertTrue(text.contains("question/runtime"));
        assertTrue(text.contains("scoresheet/scoresheet-row"));
        assertTrue(Files.exists(Path.of("demo/target/run/scores.csv")));
        assertTrue(Files.exists(Path.of("demo/target/run/error-history.jsonl")));
    }

    private ErrorReporter reporter(String fileName) {
        return new ErrorReporter(new ErrorHistory(temporary.resolve(fileName)),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }

    private Path createQuestions() throws IOException {
        Path questions = temporary.resolve("questions");
        Files.createDirectories(questions.resolve("q1"));
        Files.createDirectories(questions.resolve("q2"));
        Files.writeString(questions.resolve("q1/expected.txt"), "ok\n", StandardCharsets.UTF_8);
        Files.writeString(questions.resolve("q2/expected.txt"), "next\n", StandardCharsets.UTF_8);
        return questions;
    }

    private static void createSource(Path path, String source) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, source, StandardCharsets.UTF_8);
    }

    private static String passingSource(String output) {
        return "public class Main { public static void main(String[] a) { System.out.print(\""
                + output + "\\n\"); } }";
    }

    private static void requireJdk() {
        Assumptions.assumeTrue(findTool("java") && findTool("javac"), "JDK is required");
    }

    private static boolean findTool(String tool) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String directory : path.split(java.io.File.pathSeparator)) {
            if (Files.isExecutable(Path.of(directory, tool))) {
                return true;
            }
        }
        return false;
    }
}
