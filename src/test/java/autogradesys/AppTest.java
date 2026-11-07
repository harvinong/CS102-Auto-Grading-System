package autogradesys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

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
    void outOfRangeLimitsAreUsageErrors() throws Exception {
        // Each pair is one limit option with a value above its maximum; all must stop with status 2 before grading.
        String[][] cases = { { "--timeout-ms", "3600001" }, { "--timeout", "1e300" },
                { "--output-limit-bytes", "67108865" } };
        for (String[] limit : cases) {
            Process process = new ProcessBuilder("java", "-cp", "target/classes", "autogradesys.App",
                    "--non-interactive", "--submissions-dir", "s", "--questions-dir", "q", "--scoresheet", "c.csv",
                    limit[0], limit[1]).redirectErrorStream(true).start();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            String printed = new String(process.getInputStream().readAllBytes());
            assertEquals(2, process.exitValue(), printed);
            assertTrue(printed.contains("at most"), printed);
        }
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
                root.resolve("missing.csv").toString(), "--history", history.toString(),
                "--report", root.resolve("report.html").toString())
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
