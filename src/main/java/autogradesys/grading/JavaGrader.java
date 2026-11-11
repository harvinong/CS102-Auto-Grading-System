package autogradesys.grading;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Local Java-source grader: the engine behind the "grade students" menu option.
 *
 * <p>Grading flow: the menu runs the submission preflight, then calls {@link #grade}. For every
 * student and every question this class either hands a tester-based question to
 * {@link TesterGrader} (one mark per passing instructor tester) or, for the older layout, compiles
 * {@code Main.java} with javac, runs it through {@link ProcessRunner} with the question's input and
 * compares stdout with {@code expected.txt} (one mark). Every problem is recorded in the
 * {@link ErrorReporter}, and the marks are returned in a {@link GradeResult}. The HTML report
 * is written afterwards from the result and the reporter's records.</p>
 *
 * <p>Submission and question defects are recorded as recoverable so grading
 * can continue; missing or unreadable grading setup is fatal.</p>
 */
public final class JavaGrader {
    // Default cap on stdout plus stderr per process: 1 MiB (1024 * 1024 bytes).
    public static final long DEFAULT_OUTPUT_LIMIT_BYTES = 1024 * 1024L;

    // Utility class with only static methods, so the constructor is private to block instantiation.
    private JavaGrader() {
    }

    /** Backward-compatible entry point using a 1 MiB combined stdout/stderr limit. */
    public static GradeResult grade(Path submissionsRoot, Path questionsRoot, Path scoresheet,
            Duration timeout, ErrorReporter reporter) {
        // Forward to the full overload, filling in the default 1 MiB output cap.
        return grade(submissionsRoot, questionsRoot, scoresheet, timeout,
                DEFAULT_OUTPUT_LIMIT_BYTES, reporter);
    }

    /**
     * Grades each discovered student against each discovered question.
     *
     * @param submissionsRoot directory containing one directory per student
     * @param questionsRoot directory containing question fixtures
     * @param scoresheet CSV file to update, or {@code null} to report a fatal setup error
     * @param timeout maximum duration for each compile or execution process
     * @param outputLimitBytes TODO: document outputLimitBytes
     * @param reporter destination for recoverable and fatal diagnostics
     * @return counts, scores, and fatal status for the grading session
     */
    public static GradeResult grade(Path submissionsRoot, Path questionsRoot, Path scoresheet,
            Duration timeout, long outputLimitBytes, ErrorReporter reporter) {
        // No folder is a template copy when the caller does not know the starter template.
        return grade(submissionsRoot, questionsRoot, scoresheet, timeout, outputLimitBytes, Set.of(), reporter);
    }

    /**
     * Same as {@link #grade(Path, Path, Path, Duration, long, ErrorReporter)}, but skips the named
     * submission folders: copies of the starter template (such as RenameToYourStudentID) that are not
     * a student's work, so grading them would only add errors and a missing scoresheet row.
     *
     * @param templateCopies names of submission folders to leave out of grading
     */
    public static GradeResult grade(Path submissionsRoot, Path questionsRoot, Path scoresheet,
            Duration timeout, long outputLimitBytes, Set<String> templateCopies, ErrorReporter reporter) {
        // Step 1 of grading: validate the capture limit up front, since a limit of zero or less could never hold any output.
        if (outputLimitBytes <= 0) {
            // Record a FATAL error: this is a setup mistake by the operator, not a student problem.
            reporter.fatal("configuration", "output-limit",
                    "Output limit must be positive bytes: " + outputLimitBytes,
                    "Pass a positive --output-limit-bytes value.");
            // Return immediately: zero students graded, the error count so far, fatal flag set, and no scores.
            return new GradeResult(0, reporter.records().size(), true, Map.of());
        }
        // Step 2: check that both folders and the CSV scoresheet exist and are usable; validSetup has already recorded why if not.
        if (!validSetup(submissionsRoot, questionsRoot, scoresheet, reporter)) {
            // Setup is broken, so stop here with the same fatal result instead of grading against missing inputs.
            return new GradeResult(0, reporter.records().size(), true, Map.of());
        }
        // The course gives all testers in one folder (Q1aTester.java, Q2aTester.java, ...); group them into
        // one temporary folder per question (Q1/, Q2/, ...) so they can be graded like the folder layout.
        Path groupedTesters = null;
        try {
            groupedTesters = TesterGrader.groupFlatTesters(questionsRoot);
        } catch (IOException exception) {
            reporter.fatal("submission", "filesystem",
                    "Could not group the tester files by question: " + exception.getMessage(),
                    "Put the testers in one folder per question (Q1, Q2, ...) and try again.");
            return new GradeResult(0, reporter.records().size(), true, Map.of());
        }
        if (groupedTesters != null) {
            questionsRoot = groupedTesters;
        }
        // Step 3: list the question folder names (q1, q2, ...) from the questions directory, sorted so scoring order is stable.
        List<String> questions = questionNames(questionsRoot, reporter);
        // List one entry per student folder, also sorted, so the CSV rows and report follow a predictable order.
        List<Student> students = studentDirectories(submissionsRoot, reporter);
        // Leave template copies out, saying so, so the instructor knows the folder was seen but not graded.
        students = students.stream().filter(student -> {
            if (!templateCopies.contains(student.name())) {
                return true;
            }
            reporter.info("Not graded: " + student.name() + "/ is a copy of the starter template, not a student submission.");
            return false;
        }).toList();
        // Result table of student name -> (question name -> marks earned); LinkedHashMap keeps insertion order for the CSV.
        Map<String, Map<String, Integer>> scores = new LinkedHashMap<>();
        // Marks each question is worth, so the report can tell full marks from partial marks.
        Map<String, Integer> availableMarks = new LinkedHashMap<>();
        // A tester-based question is worth one mark per tester; the Main.java layout is worth one mark.
        for (String question : questions) {
            Path questionDir = questionsRoot.resolve(question);
            availableMarks.put(question, TesterGrader.usesTesters(questionDir)
                    ? TesterGrader.fullMarks(questionDir) : 1);
        }

        // Step 4: grade students one at a time; any problem inside is recorded and the loop carries on with the next student.
        for (Student student : students) {
            // Fresh per-student score map, filled in question by question below.
            Map<String, Integer> studentScores = new LinkedHashMap<>();
            // Register the map now so the student appears in the results even if every question fails.
            scores.put(student.name(), studentScores);
            // Visit every question for this student, including ones the student did not submit, so each gets an explicit mark (0 when not submitted).
            for (String question : questions) {
                // A tester-based question (instructor Tester classes in the testers folder) is graded by
                // TesterGrader against the student's template-shaped folder; it reports its own errors.
                if (TesterGrader.usesTesters(questionsRoot.resolve(question))) {
                    studentScores.put(question, TesterGrader.grade(student.name(),
                            TesterGrader.submissionRoot(student.directory(), questions), question,
                            questionsRoot.resolve(question), timeout, outputLimitBytes, reporter));
                    continue;
                }
                // Where this student's work for this question should live, e.g. submissions/alice/q1.
                Path submissionQuestion = student.directory().resolve(question);
                // The convention is that every submission's entry point is a file called Main.java inside the question folder.
                Path source = submissionQuestion.resolve("Main.java");
                // Student never created the folder for this question.
                if (!Files.isDirectory(submissionQuestion)) {
                    // Record a RECOVERABLE error (the student's fault, grading continues) with a hint on how to fix it.
                    reporter.recoverable("submission", "missing-question",
                            "Question directory is missing: " + question,
                            "Add the question directory and Main.java, or check the question name.",
                            student.name(), display(student.directory()), question);
                    // Score the unsubmitted question as 0 so the scoresheet has a value for every cell.
                    studentScores.put(question, 0);
                    // Skip compile and run and go straight to the next question.
                    continue;
                }
                // Folder exists but Main.java does not, so there is nothing to compile.
                if (!Files.isRegularFile(source)) {
                    // Record the structure problem (wrong file name or nested folder) as recoverable so the student sees what to rename.
                    reporter.recoverable("submission", "malformed-structure",
                            "Question directory has no Main.java entry point.",
                            "Put the submitted Java entry point in the question directory as Main.java.",
                            student.name(), display(student.directory()), question);
                    // No entry point means no marks for this question.
                    studentScores.put(question, 0);
                    // Move on to the next question; runQuestion would fail anyway without a source file.
                    continue;
                }
                // Structure is fine, so compile and run the submission; runQuestion returns 1 for a pass and 0 for any failure, and records the reason itself.
                studentScores.put(question, runQuestion(student.name(), student.directory(), question,
                        questionsRoot, source, timeout, outputLimitBytes, reporter));
            }
        }

        // Step 5: the marks are saved by the caller: GraderMenu passes this result to ScoreRecorder.exportResults,
        // which writes a dated copy of the scoresheet with the marks. The original scoresheet is never changed here.
        // Remove the temporary per-question tester folders, if they were made.
        if (groupedTesters != null) {
            deleteTree(groupedTesters);
        }
        // Summarise the session for the caller: students graded, total errors recorded, whether any was fatal, and all scores.
        return new GradeResult(students.size(), reporter.records().size(), reporter.hasFatalError(), scores,
                availableMarks);
    }

    /** Checks that the submissions folder, questions folder and scoresheet CSV are usable, reporting every failure found. */
    private static boolean validSetup(Path submissionsRoot, Path questionsRoot, Path scoresheet,
            ErrorReporter reporter) {
        // Assume the setup is fine and flip this to false on each problem, so every problem is reported in one pass rather than only the first.
        boolean valid = true;
        // The folder of student submissions must exist.
        if (!Files.isDirectory(submissionsRoot)) {
            // Fatal because without submissions there is nothing to grade.
            reporter.fatal("submission", "filesystem",
                    "Submission directory does not exist: " + display(submissionsRoot),
                    "Choose an existing directory containing one directory per student.");
            // Remember that setup failed but keep checking the other inputs.
            valid = false;
        }
        // The questions folder (instructor testers, or expected.txt fixtures in the older layout) must exist as well.
        if (!Files.isDirectory(questionsRoot)) {
            // Fatal because there would be nothing to compare student output against.
            reporter.fatal("submission", "filesystem",
                    "Questions directory does not exist: " + display(questionsRoot),
                    "Choose an existing directory containing one directory per question.");
            // Mark failed, then continue to the scoresheet check.
            valid = false;
        }
        // The CSV must be supplied, be a normal file, and be readable; the null test comes first so the other calls never see null.
        if (scoresheet == null || !Files.isRegularFile(scoresheet) || !Files.isReadable(scoresheet)) {
            // Fatal because results could not be saved; display() prints "<missing>" when the path is null.
            reporter.fatal("scoresheet", "scoresheet-read",
                    "Could not read scoresheet " + display(scoresheet),
                    "Check that the CSV exists and is readable before grading.");
            // Mark failed and fall through to the return.
            valid = false;
        }
        // Read the scoresheet with ScoresheetDAO now, so a sheet the marks cannot be saved into (for example
        // one without a Username column) stops grading before any student is graded, not after.
        if (valid && !new autogradesys.io.ScoresheetDAO(scoresheet.toFile()).load()) {
            reporter.fatal("scoresheet", "scoresheet-read",
                    "Could not read scoresheet " + display(scoresheet),
                    "Use the course scoresheet: a CSV with a Username column and no broken rows.");
            valid = false;
        }
        // True only when all three inputs passed their checks.
        return valid;
    }

    /** Lists question folder names in sorted order; records a fatal error and returns an empty list on failure. */
    private static List<String> questionNames(Path root, ErrorReporter reporter) {
        // Defensive recheck: validSetup normally catches this, but this method can be reached on its own.
        if (!Files.isDirectory(root)) {
            // Report the missing questions folder as fatal.
            reporter.fatal("submission", "filesystem",
                    "Questions directory does not exist: " + display(root),
                    "Choose an existing directory containing one directory per question.");
            // Nothing can be listed, so give the caller an empty list rather than null.
            return List.of();
        }
        // Files.list returns a lazy directory stream that holds an OS handle, so try-with-resources closes it.
        try (Stream<Path> paths = Files.list(root)) {
            // Keep only sub-directories (each one is a question), take their folder names, and sort them alphabetically.
            List<String> names = paths.filter(Files::isDirectory).map(path -> path.getFileName().toString())
                    .sorted().toList();
            // A questions folder with no question sub-folders cannot be used for grading.
            if (names.isEmpty()) {
                // Fatal: the caller will end up with no questions to score, so tell the operator how to fix the fixtures.
                reporter.fatal("submission", "structure", "No question directories were found.",
                        "Put the tester files in the testers folder, either all together or in one folder per question (Q1, Q2, ...).");
            }
            // Return the sorted names (possibly empty, in which case the fatal error above is already recorded).
            return names;
        // Listing can fail on permissions or a disappearing folder.
        } catch (IOException exception) {
            // Include the system's own message in the error so the cause is visible in the report.
            reporter.fatal("submission", "filesystem",
                    "Could not read questions directory " + display(root) + ": " + exception.getMessage(),
                    "Check that the questions directory is readable.");
            // Return an empty list so the caller does not crash; the fatal record is what stops the run.
            return List.of();
        }
    }

    /** Lists one {@link Student} per sub-folder of the submissions directory, sorted by name. */
    private static List<Student> studentDirectories(Path root, ErrorReporter reporter) {
        // Same defensive check for the submissions folder.
        if (!Files.isDirectory(root)) {
            // Report the missing submissions folder as fatal.
            reporter.fatal("submission", "filesystem",
                    "Submission directory does not exist: " + display(root),
                    "Choose an existing directory containing one directory per student.");
            // No students can be listed, so return an empty list.
            return List.of();
        }
        // Open the directory listing, closed automatically when the block ends.
        try (Stream<Path> paths = Files.list(root)) {
            // Each sub-directory is one student; plain files in the folder (such as a stray .DS_Store) are ignored.
            List<Student> students = paths.filter(Files::isDirectory)
                    // Sort by folder name because directory listing order is not guaranteed by the OS.
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    // Pair each folder name (used as the student's name in the CSV) with its path.
                    .map(path -> new Student(path.getFileName().toString(), path)).toList();
            // No student folders at all means there is no work to grade.
            if (students.isEmpty()) {
                // Fatal, with a hint about the expected one-folder-per-student layout.
                reporter.fatal("submission", "structure", "No student submission directories were found.",
                        "Create one directory per student inside the submissions directory.");
            }
            // Hand back the sorted students for the grading loop.
            return students;
        // The folder could not be read, for instance because of permissions.
        } catch (IOException exception) {
            // Fatal, quoting the underlying I/O message so it can be diagnosed.
            reporter.fatal("submission", "filesystem",
                    "Could not read submissions directory " + display(root) + ": " + exception.getMessage(),
                    "Check that the submissions directory is readable.");
            // Empty list keeps the caller safe; the fatal record explains why nothing was graded.
            return List.of();
        }
    }

    /**
     * Grades one student's answer to one question: load fixtures, compile, run, compare output.
     *
     * @return 1 if the program printed exactly the expected text, otherwise 0 (the reason is recorded)
     */
    private static int runQuestion(String student, Path submission, String question, Path questionsRoot,
            Path source, Duration timeout, long outputLimitBytes, ErrorReporter reporter) {
        // Folder holding this question's fixtures: expected.txt and optionally input.txt.
        Path spec = questionsRoot.resolve(question);
        // expected.txt is the exact text a correct program must print.
        Path expectedPath = spec.resolve("expected.txt");
        // Without an expected answer the submission cannot be judged, which is a problem with the question and not the student.
        if (!Files.isRegularFile(expectedPath)) {
            // Category "question" separates this teacher-side defect from student mistakes in the report.
            reporter.recoverable("question", "missing-expected",
                    "Expected output fixture is missing: " + display(expectedPath),
                    "Add expected.txt to the question fixture before grading.",
                    student, display(submission), question);
            // Score 0 and stop; the grade loop moves on to the next question.
            return 0;
        }

        // Text fed to the program's standard input; final so it can be assigned once inside the try block.
        final String input;
        // Exact expected stdout, compared byte for byte later.
        final String expected;
        // Reading the fixtures can throw IOException, handled just below.
        try {
            // input.txt is optional, because many questions need no stdin.
            Path inputPath = spec.resolve("input.txt");
            // Use the file's contents when it exists, otherwise an empty string.
            input = Files.isRegularFile(inputPath)
                    ? Files.readString(inputPath, StandardCharsets.UTF_8) : "";
            // Always decode as UTF-8 so results do not depend on the machine's default charset.
            expected = Files.readString(expectedPath, StandardCharsets.UTF_8);
        // A fixture exists but cannot be read, e.g. due to permissions.
        } catch (IOException exception) {
            // Blame the fixture, not the student, and keep the batch going.
            reporter.recoverable("question", "fixture-read",
                    "Could not read question fixtures: " + exception.getMessage(),
                    "Ensure expected.txt and optional input.txt are readable.",
                    student, display(submission), question);
            // Without fixtures there is no fair grade, so score 0.
            return 0;
        }

        // Temporary folder for compiled .class files; declared outside the try so the finally block can delete it.
        Path build = null;
        // Everything from here can fail: creating the folder, starting javac or java, or being interrupted.
        try {
            // Fresh directory per question so classes from one submission can never be picked up by another.
            build = Files.createTempDirectory("cs102-grader-");
            // Compile command as separate arguments: UTF-8 source, with -d sending the .class output to the temp folder.
            List<String> compile = List.of("javac", "-encoding", "UTF-8", "-d",
                    build.toAbsolutePath().toString(), source.toAbsolutePath().toString());
            // Run javac with empty stdin under the timeout and output cap, so an endless or huge compile cannot hang grading.
            ProcessRunner.Result compilation = ProcessRunner.run(compile, "", timeout, build, outputLimitBytes);
            // Checked before the timeout because killing a process for too much output also stops it waiting.
            if (compilation.outputLimitExceeded()) {
                // Spell out the limit and that the retained text is cut short.
                reporter.recoverable("question", "output-limit",
                        "Compiler output exceeded the combined stdout/stderr limit of "
                                + outputLimitDescription(outputLimitBytes) + "; retained output is truncated.",
                        "Reduce compiler diagnostics or raise the output limit (menu option 2, or --output-limit-bytes).",
                        student, display(submission), question);
                // A compile that was cut short cannot be trusted, so score 0.
                return 0;
            }
            // javac did not finish before the deadline.
            if (compilation.timedOut()) {
                // Report the time limit in readable units and suggest checking the source.
                reporter.recoverable("question", "timeout",
                        "Compilation exceeded " + timeoutDescription(timeout) + ".",
                        "Check the submission and retry with valid Java source.",
                        student, display(submission), question);
                // No class files can be assumed, so no marks.
                return 0;
            }
            // A non-zero exit from javac means syntax or type errors in the student's code.
            if (compilation.exitCode() != 0) {
                // Put javac's own messages in the report (or the exit status when stderr is empty) so the student sees the exact error.
                reporter.recoverable("question", "compile", details(compilation.stderr(),
                                "javac exited with status " + compilation.exitCode() + "."),
                        "Fix the Java compiler errors and resubmit.",
                        student, display(submission), question);
                // Cannot run code that did not compile, so score 0.
                return 0;
            }

            // Compilation worked: launch the compiled Main class, with the classpath pointing at the temp folder holding the .class files.
            List<String> execute = List.of("java", "-cp", build.toAbsolutePath().toString(), "Main");
            // Run the student's program with the fixture as stdin, under the same timeout and output cap.
            ProcessRunner.Result execution = ProcessRunner.run(execute, input, timeout, build, outputLimitBytes);
            // Check the cap first for the same reason as at compile time: a kill for flooding also ends the wait.
            if (execution.outputLimitExceeded()) {
                // Include how many stdout and stderr bytes were kept, to show how far over the program went.
                reporter.recoverable("question", "output-limit",
                        "Program output exceeded the combined stdout/stderr limit of "
                                + outputLimitDescription(outputLimitBytes) + "; retained output is truncated. "
                                + "stdout=" + execution.stdout().getBytes(StandardCharsets.UTF_8).length
                                + " bytes, stderr=" + execution.stderr().getBytes(StandardCharsets.UTF_8).length + " bytes.",
                        "Reduce or remove diagnostic output; the process was terminated.",
                        student, display(submission), question);
                // Truncated output can never match expected.txt, so 0.
                return 0;
            }
            // The program was still running at the deadline (typically an infinite loop) and has been killed.
            if (execution.timedOut()) {
                // Tell the student the program was terminated and where to look.
                reporter.recoverable("question", "timeout",
                        "Program exceeded " + timeoutDescription(timeout) + " and was terminated.",
                        "Check for an infinite loop or reduce the program's runtime.",
                        student, display(submission), question);
                // Zero marks for a program that never finishes.
                return 0;
            }
            // Non-zero exit usually means an uncaught exception or System.exit with an error code.
            if (execution.exitCode() != 0) {
                // Report the stack trace from stderr when present, else the exit status.
                reporter.recoverable("question", "runtime", details(execution.stderr(),
                                "Program exited with status " + execution.exitCode() + "."),
                        "Fix the runtime exception and resubmit.",
                        student, display(submission), question);
                // Crashing programs get no marks even if they printed correct text first.
                return 0;
            }
            // The pass/fail test: output must be exactly equal to expected.txt, including spaces and line endings.
            if (!execution.stdout().equals(expected)) {
                // Show both expected and actual text, escaped by quote(), so invisible differences like trailing newlines are visible.
                reporter.recoverable("question", "output-mismatch",
                        "Expected " + quote(expected) + ", received " + quote(execution.stdout()) + ".",
                        "Compare whitespace, line endings, and output text with expected.txt.",
                        student, display(submission), question);
                // Wrong output gets 0.
                return 0;
            }
            // Everything matched: log an informational PASS record rather than an error.
            reporter.info(String.format("PASS student=%s submission=%s question=%s score=1",
                    student, display(submission), question));
            // One mark for this question.
            return 1;
        // Could not create the temp folder or start javac/java (for example no JDK installed).
        } catch (IOException exception) {
            // Environment problem, reported with the system message and a hint to install a JDK.
            reporter.recoverable("question", "filesystem",
                    "Could not prepare or run the submission: " + exception.getMessage(),
                    "Install a JDK and ensure the temporary build directory is writable.",
                    student, display(submission), question);
            // Cannot grade without running the program, so 0 for this question.
            return 0;
        // The grading thread was cancelled while waiting for a child process.
        } catch (InterruptedException interrupted) {
            // Put the interrupt flag back so code further up knows cancellation was requested.
            Thread.currentThread().interrupt();
            // Record that this question was not graded because of the interruption.
            reporter.recoverable("question", "interrupted",
                    "Grading was interrupted while running the submission.",
                    "Retry the grading session.", student, display(submission), question);
            // Stop work on this question with no mark.
            return 0;
        // Runs on every path above (return or exception), so the temp folder is always cleaned up.
        } finally {
            // build stays null if createTempDirectory itself failed, so guard before deleting.
            if (build != null) {
                // Delete the temp folder and its class files so repeated runs do not fill the disk.
                deleteTree(build);
            }
        }
    }

    /** Best-effort recursive delete of the temporary build folder. */
    static void deleteTree(Path root) {
        // Walk the whole temp folder recursively; the stream is closed by try-with-resources.
        try (Stream<Path> paths = Files.walk(root)) {
            // Reverse order puts children before their parent folder, since a folder must be empty to be deleted.
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                // Each delete can fail separately, so the try sits inside the loop and one failure does not stop the rest.
                try {
                    // Remove one file or now-empty folder, tolerating one that has already gone.
                    Files.deleteIfExists(path);
                // Deleting a single leftover file failed.
                } catch (IOException ignored) {
                }
            });
        // Walking the folder failed.
        } catch (IOException ignored) {
        }
    }

    /** Picks the tool's own error text if there is any, otherwise the supplied fallback sentence. */
    static String details(String stderr, String fallback) {
        // Treat missing stderr as empty and trim surrounding whitespace so the message is tidy.
        String text = stderr == null ? "" : stderr.trim();
        // Prefer the real error text; use the generic fallback (e.g. the exit status) only when stderr has nothing.
        return text.isEmpty() ? fallback : text;
    }

    /** Renders text in quotes with control characters escaped, so mismatch messages stay on one readable line. */
    static String quote(String text) {
        // Wrap the text in quotes and escape backslashes first, then carriage returns, newlines and quotes, so whitespace differences are visible in messages.
        return "\"" + text.replace("\\", "\\\\").replace("\r", "\\r")
                .replace("\n", "\\n").replace("\"", "\\\"") + "\"";
    }

    /** Wording for the output cap used in the output-limit error messages. */
    static String outputLimitDescription(long bytes) {
        // Format the limit for messages, e.g. "1048576 byte(s)".
        return bytes + " byte(s)";
    }

    /** Wording for the time limit used in the timeout error messages. */
    static String timeoutDescription(Duration timeout) {
        // Whole seconds read better as seconds (e.g. "10 second(s)").
        if (timeout.toMillis() % 1_000 == 0) {
            // Convert milliseconds to seconds for the message.
            return (timeout.toMillis() / 1_000) + " second(s)";
        }
        // Fractions of a second are shown in milliseconds so no precision is lost.
        return timeout.toMillis() + " millisecond(s)";
    }

    /** Formats a path for error records and the report. */
    static String display(Path path) {
        // Absolute, normalised path for unambiguous messages; a null path prints as <missing> instead of throwing.
        return path == null ? "<missing>" : path.toAbsolutePath().normalize().toString();
    }

    // A student's folder name (shown in the CSV and report) plus the path to their submission folder.
    private record Student(String name, Path directory) {
    }
}
