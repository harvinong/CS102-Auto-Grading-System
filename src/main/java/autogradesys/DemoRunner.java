package autogradesys;

import autogradesys.grading.ErrorHistory;
import autogradesys.grading.ErrorReporter;
import autogradesys.grading.GradeResult;
import autogradesys.grading.HtmlReportWriter;
import autogradesys.grading.JavaGrader;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/** Runs the checked-in, credential-free demonstration fixtures.
 *
 * <p>This is deliberately a Maven-run entry point rather than a second grader;
 * it verifies the product path using only repository fixtures.</p>
 */
public final class DemoRunner {
    private DemoRunner() {
    }

    /**
     * Grades the fixtures below {@code demoRoot} and writes generated artifacts
     * beneath its target directory.
     *
     * @param demoRoot root containing the checked-in demo fixtures
     * @param output destination for demo progress and grading messages
     * @return zero when the demo has no fatal error
     */
    public static int run(Path demoRoot, PrintStream output) {
        // Prepare or update the value needed by the next grading step.
        Path fixtures = demoRoot.resolve("fixtures");
        // Prepare or update the value needed by the next grading step.
        Path submissions = fixtures.resolve("submissions");
        // Prepare or update the value needed by the next grading step.
        Path questions = fixtures.resolve("questions");
        // Prepare or update the value needed by the next grading step.
        Path template = fixtures.resolve("scores.template.csv");
        // Prepare or update the value needed by the next grading step.
        Path generated = demoRoot.resolve("target").resolve("run");
        // Prepare or update the value needed by the next grading step.
        Path scoresheet = generated.resolve("scores.csv");
        // Prepare or update the value needed by the next grading step.
        Path history = generated.resolve("error-history.jsonl");
        // Prepare or update the value needed by the next grading step.
        Path report = generated.resolve("grading-report.html");
        // Perform this small step of the grading workflow.
        try {
            // Perform this small step of the grading workflow.
            Files.createDirectories(generated);
            // Check this condition so grading follows the intended path.
            if (!Files.exists(scoresheet)) {
                // Perform this small step of the grading workflow.
                Files.copy(template, scoresheet, StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (IOException exception) {
            // Perform this small step of the grading workflow.
            output.println("Could not prepare demo output directory: " + exception.getMessage());
            // Return this computed result to the caller.
            return 2;
        }

        // Perform this small step of the grading workflow.
        output.println("CS102 Java auto-grader demo");
        // Perform this small step of the grading workflow.
        output.println("Fixtures: " + fixtures.toAbsolutePath().normalize());
        // Perform this small step of the grading workflow.
        output.println("Generated scoresheet: " + scoresheet.toAbsolutePath().normalize());
        // Perform this small step of the grading workflow.
        output.println("JSONL history: " + history.toAbsolutePath().normalize());
        // Perform this small step of the grading workflow.
        output.println("HTML report: " + report.toAbsolutePath().normalize());
        // Perform this small step of the grading workflow.
        output.println("Each question has its own bounded javac/java process; failures continue.");
        // Perform this small step of the grading workflow.
        output.println();

        // Prepare or update the value needed by the next grading step.
        ErrorReporter reporter = new ErrorReporter(new ErrorHistory(history), output);
        GradeResult result = JavaGrader.grade(submissions, questions, scoresheet,
                // Perform this small step of the grading workflow.
                Duration.ofSeconds(2), reporter);
        // Perform this small step of the grading workflow.
        try {
            // Perform this small step of the grading workflow.
            HtmlReportWriter.write(report, result, reporter.records());
        } catch (IOException exception) {
            // Perform this small step of the grading workflow.
            output.println("Could not write HTML report: " + exception.getMessage());
            // Return this computed result to the caller.
            return 1;
        }
        output.printf("Completed: %d submission(s), %d reported issue(s).%n",
                // Perform this small step of the grading workflow.
                result.studentCount(), result.issueCount());
        // Perform this small step of the grading workflow.
        output.println("Scores and history remain under demo/target/run; checked-in fixtures were not changed.");
        // Return this computed result to the caller.
        return result.fatal() ? 1 : 0;
    }
}
