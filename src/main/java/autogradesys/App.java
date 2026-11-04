package autogradesys;

import autogradesys.grading.ErrorHistory;
import autogradesys.grading.ErrorReporter;
import autogradesys.grading.GradeResult;
import autogradesys.grading.JavaGrader;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/** CLI entry point for interactive and non-interactive grading. */
public final class App {
    private App() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out));
    }

    public static int run(String[] args, PrintStream output) {
        if (args.length == 1 && args[0].equals("demo")) {
            return DemoRunner.run(Path.of("demo"), output);
        }
        if (args.length == 0) {
            return runInteractive(output);
        }
        return runOptions(args, output);
    }

    private static int runOptions(String[] args, PrintStream output) {
        Map<String, String> options = new HashMap<>();
        boolean nonInteractive = false;
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if (argument.equals("--non-interactive")) {
                nonInteractive = true;
            } else if (argument.startsWith("--")) {
                if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
                    output.println("Missing value for " + argument);
                    return 2;
                }
                options.put(argument, args[++index]);
            } else {
                output.println("Unknown argument: " + argument);
                return 2;
            }
        }
        if (!nonInteractive) {
            output.println("The path-based command requires --non-interactive.");
            return 2;
        }
        String submissions = options.get("--submissions-dir");
        String questions = options.get("--questions-dir");
        String scoresheet = options.get("--scoresheet");
        String history = options.getOrDefault("--history", ".grader-error-history.jsonl");
        if (submissions == null || questions == null || scoresheet == null) {
            output.println("Usage: --non-interactive --submissions-dir PATH --questions-dir PATH "
                    + "--scoresheet PATH [--history PATH] [--timeout-ms MILLIS]");
            return 2;
        }

        Duration timeout;
        try {
            if (options.containsKey("--timeout-ms")) {
                timeout = Duration.ofMillis(Long.parseLong(options.get("--timeout-ms")));
            } else {
                timeout = Duration.ofMillis(Math.round(Double.parseDouble(
                        options.getOrDefault("--timeout", "2")) * 1_000));
            }
            if (timeout.isZero() || timeout.isNegative()) {
                throw new NumberFormatException("timeout must be positive");
            }
        } catch (NumberFormatException exception) {
            output.println("Invalid timeout: " + exception.getMessage());
            return 2;
        }

        ErrorReporter reporter = new ErrorReporter(new ErrorHistory(Path.of(history)), output);
        GradeResult result = JavaGrader.grade(Path.of(submissions), Path.of(questions),
                Path.of(scoresheet), timeout, reporter);
        output.printf("Completed: %d submission(s), %d reported issue(s).%n",
                result.studentCount(), result.issueCount());
        return result.fatal() ? 1 : 0;
    }

    private static int runInteractive(PrintStream output) {
        output.println("Auto-Grader System");
        output.println();
        try (Scanner scanner = new Scanner(System.in)) {
            AutoGradeMenu menu = new AutoGradeMenu(scanner, new AutoGradeCtrl());
            menu.readOption();
            return 0;
        } catch (RuntimeException exception) {
            output.println("Could not start grading: " + exception.getMessage());
            return 2;
        }
    }
}
