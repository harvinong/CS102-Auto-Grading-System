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
                "missing-question", "malformed-structure")));
        assertEquals(7, result.studentCount());
        assertEquals(1, result.scores().get("alice").get("q1"));
        assertEquals(1, result.scores().get("compile").get("q2"));
        assertEquals(0, result.scores().get("missing").get("q1"));
        assertTrue(reporter.records().stream().allMatch(record -> record.canContinue()));
        // Grading no longer writes into the scoresheet; ScoreRecorder.exportResults saves a dated copy instead.
        assertEquals("student,q1,q2\nlegacy,9,8\nalice,,\ncompile,,\nruntime,,\nmismatch,,\ntimeout,,\nmissing,,\nmalformed,,\n,,\n",
                Files.readString(scoresheet));

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
    void outputLimitStopsFloodingRetainsBoundedBytesAndContinues() throws Exception {
        requireJdk();
        Path questions = createQuestions();
        Path submissions = temporary.resolve("output-submissions");
        createSource(submissions.resolve("flood/q1/Main.java"),
                "public class Main { public static void main(String[] a) { while (true) System.out.print(\"0123456789\"); } }");
        createSource(submissions.resolve("flood/q2/Main.java"), passingSource("next"));
        createSource(submissions.resolve("healthy/q1/Main.java"), passingSource("ok"));
        createSource(submissions.resolve("healthy/q2/Main.java"), passingSource("next"));
        Path scoresheet = temporary.resolve("output-scores.csv");
        Files.writeString(scoresheet, "student,q1,q2\nflood,,\nhealthy,,\n", StandardCharsets.UTF_8);
        ErrorReporter reporter = reporter("output-errors.jsonl");

        GradeResult result = JavaGrader.grade(submissions, questions, scoresheet,
                Duration.ofSeconds(2), 128, reporter);

        ErrorRecord limit = reporter.records().stream()
                .filter(record -> record.category().equals("output-limit")).findFirst()
                .orElseThrow(() -> new AssertionError(reporter.records().toString()));
        assertTrue(limit.message().contains("128 byte(s)"));
        assertTrue(limit.message().contains("truncated"));
        assertTrue(limit.message().contains("stdout=128 bytes, stderr=0 bytes"));
        assertEquals(1, result.scores().get("flood").get("q2"));
        assertEquals(1, result.scores().get("healthy").get("q1"));
        // Grading leaves the scoresheet unchanged.
        assertEquals("student,q1,q2\nflood,,\nhealthy,,\n", Files.readString(scoresheet));
    }

    @Test
    void invalidOutputLimitIsRejectedBeforeGrading() throws Exception {
        ErrorReporter reporter = reporter("invalid-output.jsonl");
        GradeResult result = JavaGrader.grade(temporary, temporary, null,
                Duration.ofSeconds(1), 0, reporter);
        assertTrue(result.fatal());
        assertEquals("output-limit", reporter.records().get(0).category());
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
    void htmlReportJudgesMultiMarkQuestionsAgainstTheirAvailableMarks() throws Exception {
        Path report = temporary.resolve("multi-mark.html");
        // Q1 has two testers: alice earned both, bob one, carol none.
        GradeResult result = new GradeResult(3, 0, false,
                Map.of("alice", Map.of("Q1", 2), "bob", Map.of("Q1", 1), "carol", Map.of("Q1", 0)),
                Map.of("Q1", 2));
        HtmlReportWriter.write(report, result, List.of());
        String html = Files.readString(report);
        // Full marks used to be shown as a red "FAIL (2)" because only a score of exactly 1 counted as a pass.
        assertTrue(html.contains("PASS (2/2)"), html);
        assertTrue(html.contains("PARTIAL (1/2)"), html);
        assertTrue(html.contains("FAIL (0/2)"), html);
        // One result in each summary card.
        assertTrue(html.contains("1</strong>full marks"), html);
        assertTrue(html.contains("1</strong>partial marks"), html);
        assertTrue(html.contains("1</strong>no marks"), html);
    }

    @Test
    void htmlReportWriteFailureIsClear() throws Exception {
        Path parentFile = temporary.resolve("not-a-directory");
        Files.writeString(parentFile, "occupied", StandardCharsets.UTF_8);
        IOException failure = assertThrows(IOException.class, () -> HtmlReportWriter.write(
                parentFile.resolve("report.html"), new GradeResult(0, 0, false, Map.of()), List.of()));
        assertTrue(failure.getMessage() != null && !failure.getMessage().isBlank());
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
