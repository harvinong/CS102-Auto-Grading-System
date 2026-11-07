package autogradesys;

import autogradesys.grading.ErrorHistory;
import autogradesys.grading.ErrorReporter;
import autogradesys.grading.GradeResult;
import autogradesys.grading.HtmlReportWriter;
import autogradesys.grading.JavaGrader;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Scanner;

/** CLI entry point for interactive and non-interactive grading. */
public final class App {
    private App() {
    }

    public static void main(String[] args) {
        // Perform this small step of the grading workflow.
        System.exit(run(args, System.out));
    }

    public static int run(String[] args, PrintStream output) {
        // Check this condition so grading follows the intended path.
        if (args.length == 1 && args[0].equals("demo")) {
            // Return this computed result to the caller.
            return DemoRunner.run(Path.of("demo"), output);
        }
        // Check this condition so grading follows the intended path.
        if (args.length == 0) {
            // Return this computed result to the caller.
            return runInteractive(output);
        }
        // Return this computed result to the caller.
        return runOptions(args, output);
    }

    private static int runOptions(String[] args, PrintStream output) {
        // Prepare or update the value needed by the next grading step.
        Map<String, String> options = new HashMap<>();
        // Prepare or update the value needed by the next grading step.
        boolean nonInteractive = false;
        // Repeat this operation for each relevant grading item.
        for (int index = 0; index < args.length; index++) {
            // Prepare or update the value needed by the next grading step.
            String argument = args[index];
            // Check this condition so grading follows the intended path.
            if (argument.equals("--non-interactive")) {
                // Prepare or update the value needed by the next grading step.
                nonInteractive = true;
            } else if (argument.startsWith("--")) {
                Set<String> valueOptions = Set.of("--submissions-dir", "--questions-dir", "--scoresheet",
                        "--history", "--report", "--timeout-ms", "--timeout", "--output-limit-bytes");
                if (!valueOptions.contains(argument)) {
                    output.println("Unknown argument: " + argument);
                    return 2;
                }
                // Check this condition so grading follows the intended path.
                if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
                    // Perform this small step of the grading workflow.
                    output.println("Missing value for " + argument);
                    // Return this computed result to the caller.
                    return 2;
                }
                // Perform this small step of the grading workflow.
                options.put(argument, args[++index]);
            } else {
                // Perform this small step of the grading workflow.
                output.println("Unknown argument: " + argument);
                // Return this computed result to the caller.
                return 2;
            }
        }
        // Check this condition so grading follows the intended path.
        if (!nonInteractive) {
            // Perform this small step of the grading workflow.
            output.println("The path-based command requires --non-interactive.");
            // Return this computed result to the caller.
            return 2;
        }
        // Prepare or update the value needed by the next grading step.
        String submissions = options.get("--submissions-dir");
        // Prepare or update the value needed by the next grading step.
        String questions = options.get("--questions-dir");
        // Prepare or update the value needed by the next grading step.
        String scoresheet = options.get("--scoresheet");
        // Prepare or update the value needed by the next grading step.
        String history = options.getOrDefault("--history", ".grader-error-history.jsonl");
        // Prepare or update the value needed by the next grading step.
        String report = options.getOrDefault("--report", ".grader-report.html");
        // Check this condition so grading follows the intended path.
        if (submissions == null || questions == null || scoresheet == null) {
            output.println("Usage: --non-interactive --submissions-dir PATH --questions-dir PATH "
                    + "--scoresheet PATH [--history PATH] [--report PATH] [--timeout-ms MILLIS]"
                    // Perform this small step of the grading workflow.
                    + " [--output-limit-bytes BYTES]");
            // Return this computed result to the caller.
            return 2;
        }

        // Perform this small step of the grading workflow.
        Duration timeout;
        // Perform this small step of the grading workflow.
        try {
            // Check this condition so grading follows the intended path.
            if (options.containsKey("--timeout-ms")) {
                // Prepare or update the value needed by the next grading step.
                timeout = Duration.ofMillis(Long.parseLong(options.get("--timeout-ms")));
            } else {
                timeout = Duration.ofMillis(Math.round(Double.parseDouble(
                        // Perform this small step of the grading workflow.
                        options.getOrDefault("--timeout", "2")) * 1_000));
            }
            // Check this condition so grading follows the intended path.
            if (timeout.isZero() || timeout.isNegative()) {
                // Report this unrecoverable condition to the caller.
                throw new NumberFormatException("timeout must be positive");
            }
        } catch (NumberFormatException exception) {
            // Perform this small step of the grading workflow.
            output.println("Invalid timeout: " + exception.getMessage());
            // Return this computed result to the caller.
            return 2;
        }

        // Perform this small step of the grading workflow.
        long outputLimitBytes;
        // Perform this small step of the grading workflow.
        try {
            // Prepare or update the value needed by the next grading step.
            outputLimitBytes = Long.parseLong(options.getOrDefault("--output-limit-bytes", "1048576"));
            // Check this condition so grading follows the intended path.
            if (outputLimitBytes <= 0) {
                // Report this unrecoverable condition to the caller.
                throw new NumberFormatException("output limit must be positive");
            }
        } catch (NumberFormatException exception) {
            // Perform this small step of the grading workflow.
            output.println("Invalid output limit: " + exception.getMessage());
            // Return this computed result to the caller.
            return 2;
        }

        // Prepare or update the value needed by the next grading step.
        ErrorReporter reporter = new ErrorReporter(new ErrorHistory(Path.of(history)), output);
        GradeResult result = JavaGrader.grade(Path.of(submissions), Path.of(questions),
                // Perform this small step of the grading workflow.
                Path.of(scoresheet), timeout, outputLimitBytes, reporter);
        // Perform this small step of the grading workflow.
        try {
            // Perform this small step of the grading workflow.
            HtmlReportWriter.write(Path.of(report), result, reporter.records());
            // Perform this small step of the grading workflow.
            output.println("HTML report: " + Path.of(report).toAbsolutePath().normalize());
        } catch (IOException exception) {
            // Perform this small step of the grading workflow.
            output.println("Could not write HTML report: " + exception.getMessage());
            // Return this computed result to the caller.
            return 1;
        }
        output.printf("Completed: %d submission(s), %d reported issue(s).%n",
                // Perform this small step of the grading workflow.
                result.studentCount(), result.issueCount());
        // Return this computed result to the caller.
        return result.fatal() ? 1 : 0;
    }

    private static int runInteractive(PrintStream output) {
        // Perform this small step of the grading workflow.
        output.println("Auto-Grader System");
        // Perform this small step of the grading workflow.
        output.println();
        // Prepare or update the value needed by the next grading step.
        try (Scanner scanner = new Scanner(System.in)) {
            // Prepare or update the value needed by the next grading step.
            AutoGradeMenu menu = new AutoGradeMenu(scanner, new AutoGradeCtrl());
            // Perform this small step of the grading workflow.
            menu.readOption();
            // Return this computed result to the caller.
            return 0;
        } catch (RuntimeException exception) {
            // Perform this small step of the grading workflow.
            output.println("Could not start grading: " + exception.getMessage());
            // Return this computed result to the caller.
            return 2;
        }
    }
}
