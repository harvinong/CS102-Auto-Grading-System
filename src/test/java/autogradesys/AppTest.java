package autogradesys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import autogradesys.grading.ErrorHistory;
import autogradesys.grading.ErrorReporter;
import autogradesys.grading.JavaGrader;

class AppTest {
    @Test
    void invalidArgumentsHaveNonzeroProcessStatus() throws Exception {
        Process process = new ProcessBuilder("java", "-cp", "target/classes", "autogradesys.App",
                "--not-an-option").redirectErrorStream(true).start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(2, process.exitValue());
    }

    @Test
    void interactiveEntrypointWritesHtmlReportForTrustedPaths() throws Exception {
        Path root = Files.createTempDirectory("interactive-report-");
        Path submissions = Files.createDirectories(root.resolve("submissions/alice/q1"));
        Files.writeString(submissions.resolve("Main.java"),
                "class Main { public static void main(String[] a) { System.out.print(\"ok\\n\"); } }");
        Path questions = Files.createDirectories(root.resolve("questions/q1"));
        Files.writeString(questions.resolve("expected.txt"), "ok\n");
        Path scoresheet = root.resolve("scores.csv");
        Files.writeString(scoresheet, "student,q1\nalice,\n");
        String input = submissions.getParent().toAbsolutePath() + "\n"
                + questions.getParent().toAbsolutePath() + "\n"
                + scoresheet.toAbsolutePath() + "\n";
        String classPath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                .map(entry -> Path.of(entry).toAbsolutePath().toString())
                .collect(Collectors.joining(File.pathSeparator));
        Process process = new ProcessBuilder("java", "-cp", classPath, "autogradesys.App")
                .directory(root.toFile()).redirectErrorStream(true).start();
        process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();
        assertTrue(process.waitFor(20, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        String report = Files.readString(root.resolve(".grader-report.html"));
        assertTrue(report.contains("Grading run report"));
        assertTrue(report.contains("alice"));
    }

    @Test
    void fatalSetupHasNonzeroStatusAndDoesNotGrade() throws Exception {
        Path root = Files.createTempDirectory("fatal-setup-");
        Path submissions = Files.createDirectories(root.resolve("submissions/alice/q1"));
        Files.writeString(submissions.resolve("Main.java"),
                "class Main { public static void main(String[] a) { "
                        + "throw new RuntimeException(\"executed\"); } }");
        Path questions = Files.createDirectories(root.resolve("questions/q1"));
        Files.writeString(questions.resolve("expected.txt"), "ok\n");
        Path scoresheet = root.resolve("scores.csv");
        Files.writeString(scoresheet, "student,q1\nalice,old\n");
        Path history = root.resolve("history.jsonl");

        Process process = new ProcessBuilder("java", "-cp", "target/classes", "autogradesys.App",
                "--non-interactive", "--submissions-dir", root.resolve("submissions").toString(),
                "--questions-dir", questions.getParent().toString(), "--scoresheet",
                root.resolve("missing.csv").toString(), "--history", history.toString())
                .redirectErrorStream(true).start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(1, process.exitValue());
        assertEquals("student,q1\nalice,old\n", Files.readString(scoresheet));
        assertFalse(Files.exists(history) && Files.readString(history).contains("executed"));

        ErrorReporter reporter = new ErrorReporter(new ErrorHistory(root.resolve("direct.jsonl")),
                System.out);
        assertTrue(JavaGrader.grade(root.resolve("submissions"), questions.getParent(),
                root.resolve("also-missing.csv"), java.time.Duration.ofSeconds(1), reporter).fatal());
    }
}
