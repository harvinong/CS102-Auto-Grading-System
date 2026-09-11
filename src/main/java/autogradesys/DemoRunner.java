package autogradesys;

import autogradesys.grading.ErrorHistory;
import autogradesys.grading.ErrorReporter;
import autogradesys.grading.GradeResult;
import autogradesys.grading.JavaGrader;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/** Runs the checked-in, credential-free demonstration fixtures. */
public final class DemoRunner {
    private DemoRunner() {
    }

    public static int run(Path demoRoot, PrintStream output) {
        Path fixtures = demoRoot.resolve("fixtures");
        Path submissions = fixtures.resolve("submissions");
        Path questions = fixtures.resolve("questions");
        Path template = fixtures.resolve("scores.template.csv");
        Path generated = demoRoot.resolve("target").resolve("run");
        Path scoresheet = generated.resolve("scores.csv");
        Path history = generated.resolve("error-history.jsonl");
        try {
            Files.createDirectories(generated);
            if (!Files.exists(scoresheet)) {
                Files.copy(template, scoresheet, StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (IOException exception) {
            output.println("Could not prepare demo output directory: " + exception.getMessage());
            return 2;
        }

        output.println("CS102 Java auto-grader demo");
        output.println("Fixtures: " + fixtures.toAbsolutePath().normalize());
        output.println("Generated scoresheet: " + scoresheet.toAbsolutePath().normalize());
        output.println("JSONL history: " + history.toAbsolutePath().normalize());
        output.println("Each question has its own bounded javac/java process; failures continue.");
        output.println();

        ErrorReporter reporter = new ErrorReporter(new ErrorHistory(history), output);
        GradeResult result = JavaGrader.grade(submissions, questions, scoresheet,
                Duration.ofSeconds(2), reporter);
        output.printf("Completed: %d submission(s), %d reported issue(s).%n",
                result.studentCount(), result.issueCount());
        output.println("Scores and history remain under demo/target/run; checked-in fixtures were not changed.");
        return result.fatal() ? 1 : 0;
    }
}
