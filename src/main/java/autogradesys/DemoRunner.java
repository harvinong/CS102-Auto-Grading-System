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

/**
 * Runs the checked-in, credential-free demonstration fixtures.
 *
 * <p>Flow: copy the template CSV, build an ErrorReporter, call JavaGrader on the sample
 * submissions, write the HTML report, and summarise. It reuses the real grading classes so the
 * demo shows exactly what a real run does.</p>
 *
 * <p>This is deliberately a thin entry point (run with {@code ./run.sh demo} or Maven) rather than
 * a second grader; it verifies the product path using only repository fixtures.</p>
 */
public final class DemoRunner {
    // Static entry point only; no instances.
    /**
     * Creates a demo runner instance.
     */
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
        // Checked-in sample data: demo/fixtures.
        Path fixtures = demoRoot.resolve("fixtures");
        // Sample student folders, some deliberately broken to show each error type.
        Path submissions = fixtures.resolve("submissions");
        // Sample questions with expected.txt (and input.txt where needed).
        Path questions = fixtures.resolve("questions");
        // Pristine scoresheet that is copied, so the checked-in file is never edited.
        Path template = fixtures.resolve("scores.template.csv");
        // Output folder under target/, which is git-ignored build output.
        Path generated = demoRoot.resolve("target").resolve("run");
        // Working copy of the scoresheet that grading will update.
        Path scoresheet = generated.resolve("scores.csv");
        // Where ErrorHistory appends the demo's error records.
        Path history = generated.resolve("error-history.jsonl");
        // Where HtmlReportWriter saves the demo report.
        Path report = generated.resolve("grading-report.html");
        // Folder and file operations can fail with IOException, handled below.
        try {
            // Create target/run if missing; no-op if it exists.
            Files.createDirectories(generated);
            // Copy the template only on the first run, so later runs keep earlier scores.
            if (!Files.exists(scoresheet)) {
                // Duplicate the template, keeping file attributes such as timestamps.
                Files.copy(template, scoresheet, StandardCopyOption.COPY_ATTRIBUTES);
            }
        // The output folder or scoresheet could not be prepared.
        } catch (IOException exception) {
            // Explain the failure to the person running the demo.
            output.println("Could not prepare demo output directory: " + exception.getMessage());
            // Exit code 2 means a setup problem, as opposed to 1 for a grading or report failure.
            return 2;
        }

        // Banner so the console output is easy to recognise.
        output.println("CS102 Java auto-grader demo");
        // Print resolved locations so the viewer can open the files afterwards.
        output.println("Fixtures: " + fixtures.toAbsolutePath().normalize());
        // Where the updated scores will be.
        output.println("Generated scoresheet: " + scoresheet.toAbsolutePath().normalize());
        // Where the error log will be.
        output.println("JSONL history: " + history.toAbsolutePath().normalize());
        // Where the report will be.
        output.println("HTML report: " + report.toAbsolutePath().normalize());
        // Reminds the viewer of the key design point: timeouts per process, and one failure does not stop the batch.
        output.println("Each question has its own bounded javac/java process; failures continue.");
        // Blank line to separate the banner from grading output.
        output.println();

        // Wire the reporter to the demo history file and the same console stream.
        ErrorReporter reporter = new ErrorReporter(new ErrorHistory(history), output);
        // Run the real grading engine on the fixtures; the demo has no preflight step and no separate grader.
        GradeResult result = JavaGrader.grade(submissions, questions, scoresheet,
                // Two seconds per process is plenty for the small demo programs, and still stops the fixture's infinite loop quickly.
                Duration.ofSeconds(2), reporter);
        // Report writing can fail with IOException.
        try {
            // Render the scores and every collected error into the HTML file.
            HtmlReportWriter.write(report, result, reporter.records());
        // The report folder is not writable, for instance.
        } catch (IOException exception) {
            // Say why no report exists.
            output.println("Could not write HTML report: " + exception.getMessage());
            // Exit code 1: grading ran, but the demo did not fully succeed.
            return 1;
        }
        // One-line summary of the run.
        output.printf("Completed: %d submission(s), %d reported issue(s).%n",
                result.studentCount(), result.issueCount());
        // Reassure the user that only generated files were modified.
        output.println("Scores and history remain under demo/target/run; checked-in fixtures were not changed.");
        // Success (0) unless grading hit a fatal error; non-fatal student errors are expected in the demo.
        return result.fatal() ? 1 : 0;
    }
}
