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
    private JavaGrader() {
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
            Duration timeout, ErrorReporter reporter) {
        List<String> questions = questionNames(questionsRoot, reporter);
        List<Student> students = studentDirectories(submissionsRoot, reporter);
        Map<String, Map<String, Integer>> scores = new LinkedHashMap<>();

        for (Student student : students) {
            Map<String, Integer> studentScores = new LinkedHashMap<>();
            scores.put(student.name(), studentScores);
            for (String question : questions) {
                Path submissionQuestion = student.directory().resolve(question);
                Path source = submissionQuestion.resolve("Main.java");
                if (!Files.isDirectory(submissionQuestion)) {
                    reporter.recoverable("submission", "missing-question",
                            "Question directory is missing: " + question,
                            "Add the question directory and Main.java, or check the question name.",
                            student.name(), display(student.directory()), question);
                    studentScores.put(question, 0);
                    continue;
                }
                if (!Files.isRegularFile(source)) {
                    reporter.recoverable("submission", "malformed-structure",
                            "Question directory has no Main.java entry point.",
                            "Put the submitted Java entry point in the question directory as Main.java.",
                            student.name(), display(student.directory()), question);
                    studentScores.put(question, 0);
                    continue;
                }
                studentScores.put(question, runQuestion(student.name(), student.directory(), question,
                        questionsRoot, source, timeout, reporter));
            }
        }

        if (scoresheet != null) {
            CsvScoresheet.update(scoresheet, scores, reporter);
        } else {
            reporter.fatal("scoresheet", "scoresheet-read", "No scoresheet was provided.",
                    "Pass a CSV scoresheet path before running a non-interactive grading session.");
        }
        return new GradeResult(students.size(), reporter.records().size(), reporter.hasFatalError(), scores);
    }

    private static List<String> questionNames(Path root, ErrorReporter reporter) {
        if (!Files.isDirectory(root)) {
            reporter.fatal("submission", "filesystem",
                    "Questions directory does not exist: " + display(root),
                    "Choose an existing directory containing one directory per question.");
            return List.of();
        }
        try (Stream<Path> paths = Files.list(root)) {
            List<String> names = paths.filter(Files::isDirectory).map(path -> path.getFileName().toString())
                    .sorted().toList();
            if (names.isEmpty()) {
                reporter.fatal("submission", "structure", "No question directories were found.",
                        "Create question directories containing expected.txt fixtures.");
            }
            return names;
        } catch (IOException exception) {
            reporter.fatal("submission", "filesystem",
                    "Could not read questions directory " + display(root) + ": " + exception.getMessage(),
                    "Check that the questions directory is readable.");
            return List.of();
        }
    }

    private static List<Student> studentDirectories(Path root, ErrorReporter reporter) {
        if (!Files.isDirectory(root)) {
            reporter.fatal("submission", "filesystem",
                    "Submission directory does not exist: " + display(root),
                    "Choose an existing directory containing one directory per student.");
            return List.of();
        }
        try (Stream<Path> paths = Files.list(root)) {
            List<Student> students = paths.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> new Student(path.getFileName().toString(), path)).toList();
            if (students.isEmpty()) {
                reporter.fatal("submission", "structure", "No student submission directories were found.",
                        "Create one directory per student inside the submissions directory.");
            }
            return students;
        } catch (IOException exception) {
            reporter.fatal("submission", "filesystem",
                    "Could not read submissions directory " + display(root) + ": " + exception.getMessage(),
                    "Check that the submissions directory is readable.");
            return List.of();
        }
    }

    private static int runQuestion(String student, Path submission, String question, Path questionsRoot,
            Path source, Duration timeout, ErrorReporter reporter) {
        Path spec = questionsRoot.resolve(question);
        Path expectedPath = spec.resolve("expected.txt");
        if (!Files.isRegularFile(expectedPath)) {
            reporter.recoverable("question", "missing-expected",
                    "Expected output fixture is missing: " + display(expectedPath),
                    "Add expected.txt to the question fixture before grading.",
                    student, display(submission), question);
            return 0;
        }

        final String input;
        final String expected;
        try {
            Path inputPath = spec.resolve("input.txt");
            input = Files.isRegularFile(inputPath)
                    ? Files.readString(inputPath, StandardCharsets.UTF_8) : "";
            expected = Files.readString(expectedPath, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            reporter.recoverable("question", "fixture-read",
                    "Could not read question fixtures: " + exception.getMessage(),
                    "Ensure expected.txt and optional input.txt are readable.",
                    student, display(submission), question);
            return 0;
        }

        Path build = null;
        try {
            build = Files.createTempDirectory("cs102-grader-");
            List<String> compile = List.of("javac", "-encoding", "UTF-8", "-d",
                    build.toAbsolutePath().toString(), source.toAbsolutePath().toString());
            // Compile and execute independently: one broken submission must not stop later questions.
            ProcessRunner.Result compilation = ProcessRunner.run(compile, "", timeout, build);
            if (compilation.timedOut()) {
                reporter.recoverable("question", "timeout",
                        "Compilation exceeded " + timeoutDescription(timeout) + ".",
                        "Check the submission and retry with valid Java source.",
                        student, display(submission), question);
                return 0;
            }
            if (compilation.exitCode() != 0) {
                reporter.recoverable("question", "compile", details(compilation.stderr(),
                                "javac exited with status " + compilation.exitCode() + "."),
                        "Fix the Java compiler errors and resubmit.",
                        student, display(submission), question);
                return 0;
            }

            List<String> execute = List.of("java", "-cp", build.toAbsolutePath().toString(), "Main");
            ProcessRunner.Result execution = ProcessRunner.run(execute, input, timeout, build);
            if (execution.timedOut()) {
                reporter.recoverable("question", "timeout",
                        "Program exceeded " + timeoutDescription(timeout) + " and was terminated.",
                        "Check for an infinite loop or reduce the program's runtime.",
                        student, display(submission), question);
                return 0;
            }
            if (execution.exitCode() != 0) {
                reporter.recoverable("question", "runtime", details(execution.stderr(),
                                "Program exited with status " + execution.exitCode() + "."),
                        "Fix the runtime exception and resubmit.",
                        student, display(submission), question);
                return 0;
            }
            if (!execution.stdout().equals(expected)) {
                reporter.recoverable("question", "output-mismatch",
                        "Expected " + quote(expected) + ", received " + quote(execution.stdout()) + ".",
                        "Compare whitespace, line endings, and output text with expected.txt.",
                        student, display(submission), question);
                return 0;
            }
            reporter.info(String.format("PASS student=%s submission=%s question=%s score=1",
                    student, display(submission), question));
            return 1;
        } catch (IOException exception) {
            reporter.recoverable("question", "filesystem",
                    "Could not prepare or run the submission: " + exception.getMessage(),
                    "Install a JDK and ensure the temporary build directory is writable.",
                    student, display(submission), question);
            return 0;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            reporter.recoverable("question", "interrupted",
                    "Grading was interrupted while running the submission.",
                    "Retry the grading session.", student, display(submission), question);
            return 0;
        } finally {
            if (build != null) {
                // A per-question directory prevents generated classes from leaking between submissions.
                deleteTree(build);
            }
        }
    }

    private static void deleteTree(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Cleanup is secondary: it must not hide the grading result already recorded.
                }
            });
        } catch (IOException ignored) {
            // Temporary cleanup must not hide the grading result.
        }
    }

    private static String details(String stderr, String fallback) {
        String text = stderr == null ? "" : stderr.trim();
        return text.isEmpty() ? fallback : text;
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\r", "\\r")
                .replace("\n", "\\n").replace("\"", "\\\"") + "\"";
    }

    private static String timeoutDescription(Duration timeout) {
        if (timeout.toMillis() % 1_000 == 0) {
            return (timeout.toMillis() / 1_000) + " second(s)";
        }
        return timeout.toMillis() + " millisecond(s)";
    }

    private static String display(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private record Student(String name, Path directory) {
    }
}
