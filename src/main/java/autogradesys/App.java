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

/**
 * CLI entry point for interactive and non-interactive grading.
 *
 * <p>Three modes: {@code demo} runs the sample fixtures, no arguments opens the interactive menu,
 * and {@code --non-interactive} with path options grades directly and writes an HTML report.</p>
 */
public final class App {
    // Entry-point class with only static methods; no instances.
    private App() {
    }

    /** Program start: runs the chosen mode and exits with its status code. */
    public static void main(String[] args) {
        // Hand the exit code from run() to the operating system so scripts and CI can detect failure.
        System.exit(run(args, System.out));
    }

    // Testable core of main(): takes the stream explicitly so tests can capture the output.
    public static int run(String[] args, PrintStream output) {
        // The single word "demo" runs the built-in sample fixtures.
        if (args.length == 1 && args[0].equals("demo")) {
            // Grade the fixtures under the demo folder in the current directory.
            return DemoRunner.run(Path.of("demo"), output);
        }
        // No arguments: start the interactive menu for a person at the keyboard.
        if (args.length == 0) {
            // Menu mode, where option 3 runs preflight, grading and the report.
            return runInteractive(output);
        }
        // Anything else is treated as a scripted run with --option value pairs.
        return runOptions(args, output);
    }

    // Non-interactive path: parse flags, validate them, then grade and write the report.
    private static int runOptions(String[] args, PrintStream output) {
        // Option name -> value, such as --scoresheet -> scores.csv.
        Map<String, String> options = new HashMap<>();
        // Must be set by the --non-interactive flag, a guard against running by accident.
        boolean nonInteractive = false;
        // Index loop so a value can be consumed together with its option name.
        for (int index = 0; index < args.length; index++) {
            // Current token.
            String argument = args[index];
            // A flag with no value.
            if (argument.equals("--non-interactive")) {
                // Remember that the user opted in.
                nonInteractive = true;
            // Any other --name must be followed by a value.
            } else if (argument.startsWith("--")) {
                // The complete list of options that take a value; anything else starting with -- is rejected instead of silently ignored.
                Set<String> valueOptions = Set.of("--submissions-dir", "--questions-dir", "--scoresheet",
                        "--history", "--report", "--timeout-ms", "--timeout", "--output-limit-bytes");
                // Catch typos such as --scorsheet before they cause a confusing "missing required option" later.
                if (!valueOptions.contains(argument)) {
                    // Name the unrecognised option.
                    output.println("Unknown argument: " + argument);
                    // Exit code 2: an unrecognised option is a usage error.
                    return 2;
                }
                // Missing value: the option is last, or the next token is another option.
                if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
                    // Name the offending option.
                    output.println("Missing value for " + argument);
                    // Exit code 2 so scripts can tell usage mistakes from grading failures.
                    return 2;
                }
                // Store the value and advance the index past it.
                options.put(argument, args[++index]);
            // A bare word that is not part of any option.
            } else {
                // Tell the user which token was not understood.
                output.println("Unknown argument: " + argument);
                // Exit code 2: a stray word that is not an option is a usage error.
                return 2;
            }
        }
        // Path options were given without the opt-in flag.
        if (!nonInteractive) {
            // Explain the missing flag.
            output.println("The path-based command requires --non-interactive.");
            // Exit code 2: the flag that guards path-based runs was not supplied.
            return 2;
        }
        // Folder holding one sub-folder per student; required.
        String submissions = options.get("--submissions-dir");
        // Folder holding one sub-folder per question; required.
        String questions = options.get("--questions-dir");
        // CSV to update with scores; required.
        String scoresheet = options.get("--scoresheet");
        // Optional error log location, defaulting to a hidden file in the current folder.
        String history = options.getOrDefault("--history", ".grader-error-history.jsonl");
        // Optional HTML report location, with a default.
        String report = options.getOrDefault("--report", ".grader-report.html");
        // At least one required option is missing.
        if (submissions == null || questions == null || scoresheet == null) {
            // Print the full usage line, listing optional options in brackets.
            output.println("Usage: --non-interactive --submissions-dir PATH --questions-dir PATH "
                    + "--scoresheet PATH [--history PATH] [--report PATH] [--timeout-ms MILLIS]"
                    + " [--output-limit-bytes BYTES]");
            // Exit code 2: grading cannot start without all three required paths.
            return 2;
        }

        // Per-process time limit, filled in by the block below.
        Duration timeout;
        // Number parsing throws NumberFormatException on bad input.
        try {
            // Precise form: milliseconds win if given.
            if (options.containsKey("--timeout-ms")) {
                // Parse whole milliseconds.
                timeout = Duration.ofMillis(Long.parseLong(options.get("--timeout-ms")));
            } else {
                // Otherwise read --timeout in seconds, which may be fractional, and convert to milliseconds; the default is 2 seconds.
                timeout = Duration.ofMillis(Math.round(Double.parseDouble(
                        options.getOrDefault("--timeout", "2")) * 1_000));
            }
            // A non-positive time limit would stop every process immediately.
            if (timeout.isZero() || timeout.isNegative()) {
                // Reuse the same catch block for this validation error.
                throw new NumberFormatException("timeout must be positive");
            }
        // Not a number, or not positive.
        } catch (NumberFormatException exception) {
            // Show what was wrong with the timeout.
            output.println("Invalid timeout: " + exception.getMessage());
            // Exit code 2: an unusable timeout is a usage error, not a grading failure.
            return 2;
        }

        // Maximum captured stdout plus stderr per process, assigned below.
        long outputLimitBytes;
        // Same pattern: parse, then validate.
        try {
            // Default is 1 MiB (1048576 bytes).
            outputLimitBytes = Long.parseLong(options.getOrDefault("--output-limit-bytes", "1048576"));
            // A zero or negative cap is meaningless.
            if (outputLimitBytes <= 0) {
                // Handled by the catch below.
                throw new NumberFormatException("output limit must be positive");
            }
        // Invalid or non-positive output limit.
        } catch (NumberFormatException exception) {
            // Report the reason.
            output.println("Invalid output limit: " + exception.getMessage());
            // Exit code 2: an unusable output cap is likewise a usage error.
            return 2;
        }

        // Create the shared error sink: console output plus the JSONL history file.
        ErrorReporter reporter = new ErrorReporter(new ErrorHistory(Path.of(history)), output);
        // Run the whole engine: compile, run, compare and update the CSV. Preflight is only part of the menu path.
        GradeResult result = JavaGrader.grade(Path.of(submissions), Path.of(questions),
                Path.of(scoresheet), timeout, outputLimitBytes, reporter);
        // Writing the report can fail with IOException.
        try {
            // Render the final scores and every recorded error as HTML.
            HtmlReportWriter.write(Path.of(report), result, reporter.records());
            // Show the absolute location so the user can open it.
            output.println("HTML report: " + Path.of(report).toAbsolutePath().normalize());
        // The report could not be saved.
        } catch (IOException exception) {
            // Say why.
            output.println("Could not write HTML report: " + exception.getMessage());
            // Exit code 1 means the run did not fully succeed.
            return 1;
        }
        // Summary of the run for the console.
        output.printf("Completed: %d submission(s), %d reported issue(s).%n",
                result.studentCount(), result.issueCount());
        // Fatal setup errors give exit 1; student-level errors alone still give 0, since they are expected grading results.
        return result.fatal() ? 1 : 0;
    }

    // Menu mode: the controller holds session state and the menu drives it.
    private static int runInteractive(PrintStream output) {
        // Title line.
        output.println("Auto-Grader System");
        // Blank line before the menu.
        output.println();
        // One scanner for the whole session, closed automatically on exit.
        try (Scanner scanner = new Scanner(System.in)) {
            // Create the menu with a fresh controller (the shared state of the session).
            AutoGradeMenu menu = new AutoGradeMenu(scanner, new AutoGradeCtrl());
            // Hand control to the menu, which shows options and handles them until the user quits.
            menu.readOption();
            // Normal exit after the user leaves the menu.
            return 0;
        // Unexpected failure while starting or running the menu.
        } catch (RuntimeException exception) {
            // Show a short message instead of a stack trace.
            output.println("Could not start grading: " + exception.getMessage());
            // Exit code 2 for a startup problem.
            return 2;
        }
    }
}
