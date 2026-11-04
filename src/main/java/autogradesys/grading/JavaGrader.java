package autogradesys.grading;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Local Java-source grader.
 *
 * <p>Submission and question defects are recorded as recoverable so grading
 * can continue; missing or unreadable grading setup is fatal.</p>
 */
public final class JavaGrader {
    // Prepare or update the value needed by the next grading step.
    public static final long DEFAULT_OUTPUT_LIMIT_BYTES = 1024 * 1024L;

    private JavaGrader() {
    }

    /** Backward-compatible entry point using a 1 MiB combined stdout/stderr limit. */
    public static GradeResult grade(Path submissionsRoot, Path questionsRoot, Path scoresheet,
            Duration timeout, ErrorReporter reporter) {
        // Return this computed result to the caller.
        return grade(submissionsRoot, questionsRoot, scoresheet, timeout,
                // Perform this small step of the grading workflow.
                DEFAULT_OUTPUT_LIMIT_BYTES, reporter);
    }

    /**
     * Grades each discovered student against each discovered question.
     *
     * @param submissionsRoot directory containing one directory per student
     * @param questionsRoot directory containing question fixtures
     * @param scoresheet CSV file to update, or {@code null} to report a fatal setup error
     * @param timeout maximum duration for each compile or execution process
     * @param reporter destination for recoverable and fatal diagnostics
     * @return counts, scores, and fatal status for the grading session
     */
    public static GradeResult grade(Path submissionsRoot, Path questionsRoot, Path scoresheet,
            Duration timeout, long outputLimitBytes, ErrorReporter reporter) {
        // Check this condition so grading follows the intended path.
        if (outputLimitBytes <= 0) {
            reporter.fatal("configuration", "output-limit",
                    "Output limit must be positive bytes: " + outputLimitBytes,
                    // Perform this small step of the grading workflow.
                    "Pass a positive --output-limit-bytes value.");
            // Return this computed result to the caller.
            return new GradeResult(0, reporter.records().size(), true, Map.of());
        }
        // Check this condition so grading follows the intended path.
        if (!validSetup(submissionsRoot, questionsRoot, scoresheet, reporter)) {
            // Return this computed result to the caller.
            return new GradeResult(0, reporter.records().size(), true, Map.of());
        }
        // Prepare or update the value needed by the next grading step.
        List<String> questions = questionNames(questionsRoot, reporter);
        // Prepare or update the value needed by the next grading step.
        List<Student> students = studentDirectories(submissionsRoot, reporter);
        // Prepare or update the value needed by the next grading step.
        Map<String, Map<String, Integer>> scores = new LinkedHashMap<>();

        // Repeat this operation for each relevant grading item.
        for (Student student : students) {
            // Prepare or update the value needed by the next grading step.
            Map<String, Integer> studentScores = new LinkedHashMap<>();
            // Perform this small step of the grading workflow.
            scores.put(student.name(), studentScores);
            // Repeat this operation for each relevant grading item.
            for (String question : questions) {
                // Prepare or update the value needed by the next grading step.
                Path submissionQuestion = student.directory().resolve(question);
                // Prepare or update the value needed by the next grading step.
                Path source = submissionQuestion.resolve("Main.java");
                // Check this condition so grading follows the intended path.
                if (!Files.isDirectory(submissionQuestion)) {
                    reporter.recoverable("submission", "missing-question",
                            "Question directory is missing: " + question,
                            "Add the question directory and Main.java, or check the question name.",
                            // Perform this small step of the grading workflow.
                            student.name(), display(student.directory()), question);
                    // Perform this small step of the grading workflow.
                    studentScores.put(question, 0);
                    // Perform this small step of the grading workflow.
                    continue;
                }
                // Check this condition so grading follows the intended path.
                if (!Files.isRegularFile(source)) {
                    reporter.recoverable("submission", "malformed-structure",
                            "Question directory has no Main.java entry point.",
                            "Put the submitted Java entry point in the question directory as Main.java.",
                            // Perform this small step of the grading workflow.
                            student.name(), display(student.directory()), question);
                    // Perform this small step of the grading workflow.
                    studentScores.put(question, 0);
                    // Perform this small step of the grading workflow.
                    continue;
                }
                studentScores.put(question, runQuestion(student.name(), student.directory(), question,
                        // Perform this small step of the grading workflow.
                        questionsRoot, source, timeout, outputLimitBytes, reporter));
            }
        }

        // Check this condition so grading follows the intended path.
        if (scoresheet != null) {
            // Perform this small step of the grading workflow.
            CsvScoresheet.update(scoresheet, scores, reporter);
        } else {
            reporter.fatal("scoresheet", "scoresheet-read", "No scoresheet was provided.",
                    // Perform this small step of the grading workflow.
                    "Pass a CSV scoresheet path before running a non-interactive grading session.");
        }
        // Return this computed result to the caller.
        return new GradeResult(students.size(), reporter.records().size(), reporter.hasFatalError(), scores);
    }

    private static boolean validSetup(Path submissionsRoot, Path questionsRoot, Path scoresheet,
            ErrorReporter reporter) {
        // Prepare or update the value needed by the next grading step.
        boolean valid = true;
        // Check this condition so grading follows the intended path.
        if (!Files.isDirectory(submissionsRoot)) {
            reporter.fatal("submission", "filesystem",
                    "Submission directory does not exist: " + display(submissionsRoot),
                    // Perform this small step of the grading workflow.
                    "Choose an existing directory containing one directory per student.");
            // Prepare or update the value needed by the next grading step.
            valid = false;
        }
        // Check this condition so grading follows the intended path.
        if (!Files.isDirectory(questionsRoot)) {
            reporter.fatal("submission", "filesystem",
                    "Questions directory does not exist: " + display(questionsRoot),
                    // Perform this small step of the grading workflow.
                    "Choose an existing directory containing one directory per question.");
            // Prepare or update the value needed by the next grading step.
            valid = false;
        }
        // Check this condition so grading follows the intended path.
        if (scoresheet == null || !Files.isRegularFile(scoresheet) || !Files.isReadable(scoresheet)) {
            reporter.fatal("scoresheet", "scoresheet-read",
                    "Could not read scoresheet " + display(scoresheet),
                    // Perform this small step of the grading workflow.
                    "Check that the CSV exists and is readable before grading.");
            // Prepare or update the value needed by the next grading step.
            valid = false;
        }
        // Check this condition so grading follows the intended path.
        if (valid && !CsvScoresheet.isValid(scoresheet)) {
            reporter.fatal("scoresheet", "scoresheet-read",
                    "Could not read scoresheet " + display(scoresheet),
                    // Perform this small step of the grading workflow.
                    "Check that the CSV has a valid header and no malformed rows.");
            // Prepare or update the value needed by the next grading step.
            valid = false;
        }
        // Return this computed result to the caller.
        return valid;
    }

    private static List<String> questionNames(Path root, ErrorReporter reporter) {
        // Check this condition so grading follows the intended path.
        if (!Files.isDirectory(root)) {
            reporter.fatal("submission", "filesystem",
                    "Questions directory does not exist: " + display(root),
                    // Perform this small step of the grading workflow.
                    "Choose an existing directory containing one directory per question.");
            // Return this computed result to the caller.
            return List.of();
        }
        // Prepare or update the value needed by the next grading step.
        try (Stream<Path> paths = Files.list(root)) {
            List<String> names = paths.filter(Files::isDirectory).map(path -> path.getFileName().toString())
                    // Perform this small step of the grading workflow.
                    .sorted().toList();
            // Check this condition so grading follows the intended path.
            if (names.isEmpty()) {
                reporter.fatal("submission", "structure", "No question directories were found.",
                        // Perform this small step of the grading workflow.
                        "Create question directories containing expected.txt fixtures.");
            }
            // Return this computed result to the caller.
            return names;
        } catch (IOException exception) {
            reporter.fatal("submission", "filesystem",
                    "Could not read questions directory " + display(root) + ": " + exception.getMessage(),
                    // Perform this small step of the grading workflow.
                    "Check that the questions directory is readable.");
            // Return this computed result to the caller.
            return List.of();
        }
    }

    private static List<Student> studentDirectories(Path root, ErrorReporter reporter) {
        // Check this condition so grading follows the intended path.
        if (!Files.isDirectory(root)) {
            reporter.fatal("submission", "filesystem",
                    "Submission directory does not exist: " + display(root),
                    // Perform this small step of the grading workflow.
                    "Choose an existing directory containing one directory per student.");
            // Return this computed result to the caller.
            return List.of();
        }
        // Prepare or update the value needed by the next grading step.
        try (Stream<Path> paths = Files.list(root)) {
            List<Student> students = paths.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    // Perform this small step of the grading workflow.
                    .map(path -> new Student(path.getFileName().toString(), path)).toList();
            // Check this condition so grading follows the intended path.
            if (students.isEmpty()) {
                reporter.fatal("submission", "structure", "No student submission directories were found.",
                        // Perform this small step of the grading workflow.
                        "Create one directory per student inside the submissions directory.");
            }
            // Return this computed result to the caller.
            return students;
        } catch (IOException exception) {
            reporter.fatal("submission", "filesystem",
                    "Could not read submissions directory " + display(root) + ": " + exception.getMessage(),
                    // Perform this small step of the grading workflow.
                    "Check that the submissions directory is readable.");
            // Return this computed result to the caller.
            return List.of();
        }
    }

    private static int runQuestion(String student, Path submission, String question, Path questionsRoot,
            Path source, Duration timeout, long outputLimitBytes, ErrorReporter reporter) {
        // Prepare or update the value needed by the next grading step.
        Path spec = questionsRoot.resolve(question);
        // Prepare or update the value needed by the next grading step.
        Path expectedPath = spec.resolve("expected.txt");
        // Check this condition so grading follows the intended path.
        if (!Files.isRegularFile(expectedPath)) {
            reporter.recoverable("question", "missing-expected",
                    "Expected output fixture is missing: " + display(expectedPath),
                    "Add expected.txt to the question fixture before grading.",
                    // Perform this small step of the grading workflow.
                    student, display(submission), question);
            // Return this computed result to the caller.
            return 0;
        }

        // Perform this small step of the grading workflow.
        final String input;
        // Perform this small step of the grading workflow.
        final String expected;
        // Perform this small step of the grading workflow.
        try {
            // Prepare or update the value needed by the next grading step.
            Path inputPath = spec.resolve("input.txt");
            input = Files.isRegularFile(inputPath)
                    // Perform this small step of the grading workflow.
                    ? Files.readString(inputPath, StandardCharsets.UTF_8) : "";
            // Prepare or update the value needed by the next grading step.
            expected = Files.readString(expectedPath, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            reporter.recoverable("question", "fixture-read",
                    "Could not read question fixtures: " + exception.getMessage(),
                    "Ensure expected.txt and optional input.txt are readable.",
                    // Perform this small step of the grading workflow.
                    student, display(submission), question);
            // Return this computed result to the caller.
            return 0;
        }

        // Prepare or update the value needed by the next grading step.
        Path build = null;
        // Perform this small step of the grading workflow.
        try {
            // Prepare or update the value needed by the next grading step.
            build = Files.createTempDirectory("cs102-grader-");
            List<String> compile = List.of("javac", "-encoding", "UTF-8", "-d",
                    // Perform this small step of the grading workflow.
                    build.toAbsolutePath().toString(), source.toAbsolutePath().toString());
            // Compile and execute independently: one broken submission must not stop later questions.
            ProcessRunner.Result compilation = ProcessRunner.run(compile, "", timeout, build, outputLimitBytes);
            // An output cap breach is distinct from a deadline breach and gets actionable guidance.
            if (compilation.outputLimitExceeded()) {
                reporter.recoverable("question", "output-limit",
                        "Compiler output exceeded the combined stdout/stderr limit of "
                                // Perform this small step of the grading workflow.
                                + outputLimitDescription(outputLimitBytes) + "; retained output is truncated.",
                        "Reduce compiler diagnostics or increase --output-limit-bytes.",
                        // Perform this small step of the grading workflow.
                        student, display(submission), question);
                // Return this computed result to the caller.
                return 0;
            }
            // Check this condition so grading follows the intended path.
            if (compilation.timedOut()) {
                reporter.recoverable("question", "timeout",
                        "Compilation exceeded " + timeoutDescription(timeout) + ".",
                        "Check the submission and retry with valid Java source.",
                        // Perform this small step of the grading workflow.
                        student, display(submission), question);
                // Return this computed result to the caller.
                return 0;
            }
            // Check this condition so grading follows the intended path.
            if (compilation.exitCode() != 0) {
                reporter.recoverable("question", "compile", details(compilation.stderr(),
                                "javac exited with status " + compilation.exitCode() + "."),
                        "Fix the Java compiler errors and resubmit.",
                        // Perform this small step of the grading workflow.
                        student, display(submission), question);
                // Return this computed result to the caller.
                return 0;
            }

            // Prepare or update the value needed by the next grading step.
            List<String> execute = List.of("java", "-cp", build.toAbsolutePath().toString(), "Main");
            // Prepare or update the value needed by the next grading step.
            ProcessRunner.Result execution = ProcessRunner.run(execute, input, timeout, build, outputLimitBytes);
            // Check the cap before timeout because stopping after a cap also ends the process wait.
            if (execution.outputLimitExceeded()) {
                reporter.recoverable("question", "output-limit",
                        "Program output exceeded the combined stdout/stderr limit of "
                                // Perform this small step of the grading workflow.
                                + outputLimitDescription(outputLimitBytes) + "; retained output is truncated. "
                                + "stdout=" + execution.stdout().getBytes(StandardCharsets.UTF_8).length
                                + " bytes, stderr=" + execution.stderr().getBytes(StandardCharsets.UTF_8).length + " bytes.",
                        // Perform this small step of the grading workflow.
                        "Reduce or remove diagnostic output; the process was terminated.",
                        // Perform this small step of the grading workflow.
                        student, display(submission), question);
                // Return this computed result to the caller.
                return 0;
            }
            // Check this condition so grading follows the intended path.
            if (execution.timedOut()) {
                reporter.recoverable("question", "timeout",
                        "Program exceeded " + timeoutDescription(timeout) + " and was terminated.",
                        "Check for an infinite loop or reduce the program's runtime.",
                        // Perform this small step of the grading workflow.
                        student, display(submission), question);
                // Return this computed result to the caller.
                return 0;
            }
            // Check this condition so grading follows the intended path.
            if (execution.exitCode() != 0) {
                reporter.recoverable("question", "runtime", details(execution.stderr(),
                                "Program exited with status " + execution.exitCode() + "."),
                        "Fix the runtime exception and resubmit.",
                        // Perform this small step of the grading workflow.
                        student, display(submission), question);
                // Return this computed result to the caller.
                return 0;
            }
            // Check this condition so grading follows the intended path.
            if (!execution.stdout().equals(expected)) {
                reporter.recoverable("question", "output-mismatch",
                        "Expected " + quote(expected) + ", received " + quote(execution.stdout()) + ".",
                        "Compare whitespace, line endings, and output text with expected.txt.",
                        // Perform this small step of the grading workflow.
                        student, display(submission), question);
                // Return this computed result to the caller.
                return 0;
            }
            reporter.info(String.format("PASS student=%s submission=%s question=%s score=1",
                    // Perform this small step of the grading workflow.
                    student, display(submission), question));
            // Return this computed result to the caller.
            return 1;
        } catch (IOException exception) {
            reporter.recoverable("question", "filesystem",
                    "Could not prepare or run the submission: " + exception.getMessage(),
                    "Install a JDK and ensure the temporary build directory is writable.",
                    // Perform this small step of the grading workflow.
                    student, display(submission), question);
            // Return this computed result to the caller.
            return 0;
        } catch (InterruptedException interrupted) {
            // Perform this small step of the grading workflow.
            Thread.currentThread().interrupt();
            reporter.recoverable("question", "interrupted",
                    "Grading was interrupted while running the submission.",
                    // Perform this small step of the grading workflow.
                    "Retry the grading session.", student, display(submission), question);
            // Return this computed result to the caller.
            return 0;
        } finally {
            // Check this condition so grading follows the intended path.
            if (build != null) {
                // A per-question directory prevents generated classes from leaking between submissions.
                deleteTree(build);
            }
        }
    }

    private static void deleteTree(Path root) {
        // Prepare or update the value needed by the next grading step.
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                // Perform this small step of the grading workflow.
                try {
                    // Perform this small step of the grading workflow.
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Cleanup is secondary: it must not hide the grading result already recorded.
                }
            // Perform this small step of the grading workflow.
            });
        } catch (IOException ignored) {
            // Temporary cleanup must not hide the grading result.
        }
    }

    private static String details(String stderr, String fallback) {
        // Prepare or update the value needed by the next grading step.
        String text = stderr == null ? "" : stderr.trim();
        // Return this computed result to the caller.
        return text.isEmpty() ? fallback : text;
    }

    private static String quote(String text) {
        // Return this computed result to the caller.
        return "\"" + text.replace("\\", "\\\\").replace("\r", "\\r")
                // Perform this small step of the grading workflow.
                .replace("\n", "\\n").replace("\"", "\\\"") + "\"";
    }

    private static String outputLimitDescription(long bytes) {
        // Return this computed result to the caller.
        return bytes + " byte(s)";
    }

    private static String timeoutDescription(Duration timeout) {
        // Check this condition so grading follows the intended path.
        if (timeout.toMillis() % 1_000 == 0) {
            // Return this computed result to the caller.
            return (timeout.toMillis() / 1_000) + " second(s)";
        }
        // Return this computed result to the caller.
        return timeout.toMillis() + " millisecond(s)";
    }

    private static String display(Path path) {
        // Return this computed result to the caller.
        return path == null ? "<missing>" : path.toAbsolutePath().normalize().toString();
    }

    private record Student(String name, Path directory) {
    }
}
