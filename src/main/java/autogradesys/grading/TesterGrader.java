package autogradesys.grading;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

/**
 * Grades one question of a template-shaped CS102 submission with the instructor's tester classes.
 *
 * <p>The testers folder holds one folder per question (a flat folder of testers is grouped by
 * {@link #groupFlatTesters}). Each holds {@code <Name>Tester.java} classes (each with a {@code main}
 * method). Each tester is compiled with the student's files and run on its own. A course tester
 * prints the student's score on its last line, and that is the mark. A tester with a
 * {@code <Name>Tester.expected.txt} next to it instead earns one mark when its output matches.</p>
 *
 * <p>Defects in a single submission are reported as recoverable so grading continues with the next
 * tester, question and student.</p>
 */
final class TesterGrader {
    // Suffix that marks an instructor tester class file, e.g. Q1aTester.java.
    private static final String TESTER_SUFFIX = "Tester.java";
    // Suffix of the expected-output file that sits next to each tester, e.g. Q1aTester.expected.txt.
    private static final String EXPECTED_SUFFIX = ".expected.txt";

    // Utility class with only static methods, so the constructor is private to block instantiation.
    private TesterGrader() {
    }

    /** Returns true when the question folder is graded with testers rather than a Main.java and expected.txt. */
    static boolean usesTesters(Path testerDir) {
        // A folder that holds at least one tester class is clearly tester based.
        if (!testerFiles(testerDir).isEmpty()) {
            // Testers found: use the tester-based grading path.
            return true;
        }
        // The older layout is recognised only by its expected.txt; anything else (including an empty
        // folder) is tester based, so a question with no testers is reported as "missing-tester".
        return !Files.isRegularFile(testerDir.resolve("expected.txt"));
    }

    /** Number of tester classes in the question folder, which is the number of marks the question is worth. */
    static int testerCount(Path testerDir) {
        // Each tester is worth one mark, so the count is the question's maximum.
        return testerFiles(testerDir).size();
    }

    /**
     * Finds the folder that holds a student's question folders.
     *
     * <p>Students often zip the whole template folder, so the question folders may sit one folder
     * down (for example {@code RenameToYourStudentID/Q1}). The submission check already warns about
     * that; grading tolerates it so those students still receive marks.</p>
     */
    static Path submissionRoot(Path studentDir, List<String> questions) {
        // Normal case: a question folder sits directly in the student's folder.
        if (holdsQuestion(studentDir, questions)) {
            // No wrapper folder to look through.
            return studentDir;
        }
        // Otherwise look at the sub-folders for exactly one that holds question folders.
        // The single wrapper folder found so far; stays null until one is seen.
        Path found = null;
        // Files.list holds an OS handle, so try-with-resources closes the directory stream.
        try (Stream<Path> children = Files.list(studentDir)) {
            // Only sub-folders can be wrappers, taken in name order so the result is the same on every machine.
            for (Path child : children.filter(Files::isDirectory).sorted().toList()) {
                // This sub-folder directly holds question folders, so it is a candidate wrapper.
                if (holdsQuestion(child, questions)) {
                    // Two candidate wrappers is ambiguous, so fall back to the student folder itself.
                    if (found != null) {
                        return studentDir;
                    }
                    // First candidate: remember it, and keep looking in case there is a second.
                    found = child;
                }
            }
        } catch (IOException exception) {
            // An unreadable folder is reported later as a missing question folder.
            return studentDir;
        }
        // Use the single wrapper if there was one, else the student folder (questions then count as missing).
        return found == null ? studentDir : found;
    }

    /**
     * Grades one question.
     *
     * @param student student name used in diagnostics
     * @param submissionRoot folder holding the student's question folders
     * @param question question folder name, such as {@code Q1}
     * @param testerDir the instructor's folder for this question
     * @return the number of testers whose output matched
     */
    static int grade(String student, Path submissionRoot, String question, Path testerDir,
            Duration timeout, long outputLimitBytes, ErrorReporter reporter) {
        // The student's folder for this question, also the "submission" shown in diagnostics.
        Path studentQuestion = submissionRoot.resolve(question);
        // The instructor's tester classes for this question, sorted so the output is stable.
        List<Path> testers = testerFiles(testerDir);
        // No testers means the instructor setup for this question is incomplete; nobody can earn marks.
        if (testers.isEmpty()) {
            reporter.recoverable("question", "missing-tester",
                    "No *Tester.java files were found in " + JavaGrader.display(testerDir),
                    "Add one or more <Name>Tester.java classes to the question folder in the testers folder.",
                    student, JavaGrader.display(studentQuestion), question);
            return 0;
        }
        // Without the student's question folder there is nothing to compile.
        if (!Files.isDirectory(studentQuestion)) {
            reporter.recoverable("submission", "missing-question",
                    "Question directory is missing: " + question,
                    "Add the question directory from the starter template, or check the question name.",
                    student, JavaGrader.display(submissionRoot), question);
            return 0;
        }
        // The student's own files for this question: Java sources plus resources such as persons.txt.
        List<Path> studentFiles = studentFiles(studentQuestion);
        // A folder without any Java source cannot satisfy any tester.
        if (studentFiles.stream().noneMatch(file -> file.getFileName().toString().endsWith(".java"))) {
            reporter.recoverable("submission", "missing-file",
                    "Question directory contains no .java files.",
                    "Add the required .java files from the starter template and resubmit.",
                    student, JavaGrader.display(studentQuestion), question);
            return 0;
        }

        // Fresh build folder per student and question so classes never leak between submissions.
        Path build = null;
        // Everything below can throw IOException (copying, starting javac) or be interrupted; the catches at the bottom record those.
        try {
            // Create the empty temporary build folder; build is declared outside the try so the finally block can delete it.
            build = Files.createTempDirectory("cs102-grader-");
            // Copy the student's files first; the instructor's testers are copied after so they win a name clash.
            for (Path file : studentFiles) {
                // Same file name in the build folder; REPLACE_EXISTING makes a repeat copy harmless.
                Files.copy(file, build.resolve(file.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
            }
            // Copy every tester source next to the student's files; expected files stay in the instructor's folder.
            for (Path tester : testers) {
                // A tester with the same name as a student file overwrites it, so the student cannot replace the instructor's tester.
                Files.copy(tester, build.resolve(tester.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
            }

            // Compile and run each tester on its own, the way the testers' readme says to do it by hand,
            // so a compile error in Q2b.java does not also cost the marks for Q2a.
            int marks = 0;
            for (Path tester : testers) {
                // Each tester gets its own output folder for its compiled classes.
                String className = tester.getFileName().toString().replace(".java", "");
                Path out = Files.createDirectories(build.resolve("out-" + className));
                // javac compiles the tester plus only the student files it uses (-sourcepath), and finds
                // the template's .class files (e.g. DataException.class, Shape.class) on the classpath (-cp).
                List<String> compile = List.of("javac", "-encoding", "UTF-8",
                        "-d", out.toAbsolutePath().toString(),
                        "-cp", build.toAbsolutePath().toString(),
                        "-sourcepath", build.toAbsolutePath().toString(),
                        build.resolve(tester.getFileName().toString()).toAbsolutePath().toString());
                ProcessRunner.Result compilation = ProcessRunner.run(compile, "", timeout, build, outputLimitBytes);
                // A compiler that floods its output is stopped and this tester scores zero.
                if (compilation.outputLimitExceeded()) {
                    reporter.recoverable("question", "output-limit",
                            "Compiler output exceeded the combined stdout/stderr limit of "
                                    + JavaGrader.outputLimitDescription(outputLimitBytes) + "; retained output is truncated.",
                            "Reduce compiler diagnostics or raise the output limit (menu option 2, or --output-limit-bytes).",
                            student, JavaGrader.display(studentQuestion), question);
                    continue;
                }
                // A compile that never finishes is stopped at the deadline.
                if (compilation.timedOut()) {
                    reporter.recoverable("question", "timeout",
                            "Compilation exceeded " + JavaGrader.timeoutDescription(timeout) + ".",
                            "Check the submission and retry with valid Java source.",
                            student, JavaGrader.display(studentQuestion), question);
                    continue;
                }
                // Any compile error (including a class the tester needs but the student did not provide) scores zero.
                if (compilation.exitCode() != 0) {
                    reporter.recoverable("question", "compile", className + " did not compile: "
                                    + firstLines(JavaGrader.details(compilation.stderr(),
                                    "javac exited with status " + compilation.exitCode() + "."), 8),
                            "Fix the Java compiler errors and resubmit; check that every file from the template is present.",
                            student, JavaGrader.display(studentQuestion), question);
                    continue;
                }
                // Compiled: run it. runTester records any failure itself and returns the marks earned.
                marks += runTester(student, studentQuestion, question, tester, build, out, timeout, outputLimitBytes,
                        reporter);
            }
            // Marks earned for this question.
            return marks;
        } catch (IOException exception) {
            reporter.recoverable("question", "filesystem",
                    "Could not prepare or run the submission: " + exception.getMessage(),
                    "Install a JDK and ensure the temporary build directory is writable.",
                    student, JavaGrader.display(studentQuestion), question);
            return 0;
        } catch (InterruptedException interrupted) {
            // Keep the interrupt visible to callers, then record that this question was cut short.
            Thread.currentThread().interrupt();
            reporter.recoverable("question", "interrupted",
                    "Grading was interrupted while running the submission.",
                    "Retry the grading session.", student, JavaGrader.display(studentQuestion), question);
            return 0;
        } finally {
            // Always remove the build folder so repeated sessions do not fill the temp directory.
            if (build != null) {
                // Delegate to JavaGrader's best-effort recursive delete of the build folder and its class files.
                JavaGrader.deleteTree(build);
            }
        }
    }

    // Input: the student and question (for messages), the tester source, the build folder (student files and
    //        template .class files), the folder holding this tester's compiled classes, and the limits.
    // Function: Runs one compiled tester. There are two kinds of tester:
    //           - with a <Name>Tester.expected.txt next to it: the output must match that file exactly (1 mark);
    //           - without one (the course testers, e.g. Q1aTester): the tester prints the student's score on
    //             its last line (e.g. "3.0"), and that number is the mark.
    //           Any failure (timeout, crash, too much output, no score) is recorded and scores 0.
    // Output: the marks this tester gives.
    private static int runTester(String student, Path studentQuestion, String question, Path tester, Path build,
            Path out, Duration timeout, long outputLimitBytes, ErrorReporter reporter)
            throws IOException, InterruptedException {
        // File name of the tester source, e.g. Q1aTester.java.
        String fileName = tester.getFileName().toString();
        // Class name to run: the file name without ".java".
        String className = fileName.substring(0, fileName.length() - ".java".length());
        // Expected output sits next to the tester in the instructor's folder, e.g. Q1aTester.expected.txt.
        Path expectedPath = tester.resolveSibling(className + EXPECTED_SUFFIX);
        // No expected file means the tester prints its own score.
        boolean printsScore = !Files.isRegularFile(expectedPath);
        // Read the expected output as UTF-8 text; an unreadable file is a setup problem for this tester only.
        String expected = "";
        if (!printsScore) {
            try {
                expected = Files.readString(expectedPath, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                reporter.recoverable("question", "fixture-read",
                        "Could not read " + JavaGrader.display(expectedPath) + ": " + exception.getMessage(),
                        "Ensure the expected output file is readable.",
                        student, JavaGrader.display(studentQuestion), question);
                return 0;
            }
        }
        // No shell: java gets the class path and class name as separate arguments. The class path has the
        // tester's compiled classes first, then the build folder for the template's .class files. The working
        // directory is the build folder so relative names like "persons.txt" find the student's copy.
        String classPath = out.toAbsolutePath() + File.pathSeparator + build.toAbsolutePath();
        List<String> execute = List.of("java", "-cp", classPath, className);
        // Testers read no stdin (empty input), and the run gets the same timeout and output cap as compiling.
        ProcessRunner.Result execution = ProcessRunner.run(execute, "", timeout, build, outputLimitBytes);
        // Check the output cap before the timeout because stopping a flooding process also ends the wait.
        if (execution.outputLimitExceeded()) {
            reporter.recoverable("question", "output-limit",
                    className + " output exceeded the combined stdout/stderr limit of "
                            + JavaGrader.outputLimitDescription(outputLimitBytes) + "; retained output is truncated. "
                            + "stdout=" + execution.stdout().getBytes(StandardCharsets.UTF_8).length
                            + " bytes, stderr=" + execution.stderr().getBytes(StandardCharsets.UTF_8).length + " bytes.",
                    "Reduce or remove diagnostic output; the process was terminated.",
                    student, JavaGrader.display(studentQuestion), question);
            return 0;
        }
        // A tester that runs past the deadline (usually an infinite loop in the student's code) is killed.
        if (execution.timedOut()) {
            reporter.recoverable("question", "timeout",
                    className + " exceeded " + JavaGrader.timeoutDescription(timeout) + " and was terminated.",
                    "Check for an infinite loop or reduce the program's runtime.",
                    student, JavaGrader.display(studentQuestion), question);
            return 0;
        }
        // A non-zero exit means an uncaught exception, so show the stack trace the student's code produced.
        if (execution.exitCode() != 0) {
            reporter.recoverable("question", "runtime", className + " failed: " + firstLines(JavaGrader.details(
                            execution.stderr(), "Program exited with status " + execution.exitCode() + "."), 8),
                    "Fix the runtime exception and resubmit.",
                    student, JavaGrader.display(studentQuestion), question);
            return 0;
        }

        // A course tester: the last line it prints is the score, e.g. "3.0".
        if (printsScore) {
            String lastLine = lastLine(execution.stdout());
            int score;
            try {
                // Round, because the testers keep the score as a double (3.0).
                score = (int) Math.round(Double.parseDouble(lastLine));
            } catch (NumberFormatException exception) {
                reporter.recoverable("question", "no-score",
                        className + " did not print a score on its last line (it printed " + JavaGrader.quote(lastLine) + ").",
                        "Make the tester print the score last, or add " + className + EXPECTED_SUFFIX + " next to it.",
                        student, JavaGrader.display(studentQuestion), question);
                return 0;
            }
            reporter.info(String.format("  %s %s: %d mark(s)", student, className, score));
            return score;
        }

        // Compare with line endings normalised so a Windows (\r\n) run still matches a Unix expected file.
        if (!normalise(execution.stdout()).equals(normalise(expected))) {
            reporter.recoverable("question", "output-mismatch",
                    className + ": expected " + JavaGrader.quote(expected) + ", received "
                            + JavaGrader.quote(execution.stdout()) + ".",
                    "Compare the output text and whitespace with " + className + EXPECTED_SUFFIX + ".",
                    student, JavaGrader.display(studentQuestion), question);
            return 0;
        }
        // Matching output earns this tester's single mark.
        reporter.info(String.format("PASS student=%s submission=%s question=%s tester=%s score=1",
                student, JavaGrader.display(studentQuestion), question, className));
        // One mark for this tester.
        return 1;
    }

    // Input: everything a program printed
    // Function: finds the last line that is not blank
    // Output: that line without spaces around it, or "" if nothing was printed
    private static String lastLine(String output) {
        String[] lines = output.split("\r?\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            if (!lines[i].trim().isEmpty()) {
                return lines[i].trim();
            }
        }
        return "";
    }

    // Input: a long message (e.g. compiler errors) and how many lines to keep
    // Function: keeps only the first lines, so one broken submission does not flood the screen and report
    // Output: the shortened message, ending with "... (N more lines)" when lines were cut
    static String firstLines(String text, int count) {
        String[] lines = text.split("\r?\n");
        if (lines.length <= count) {
            return text;
        }
        String result = "";
        for (int i = 0; i < count; i++) {
            result += lines[i] + "\n";
        }
        return result + "... (" + (lines.length - count) + " more lines)";
    }

    // Input: the instructor's folder for one question
    // Function: works out how many marks the question is worth. A tester with an expected file is worth 1.
    //           A course tester is worth the sum of the numbers in its "score += N;" lines
    //           (Q1aTester has three "score += 1;", so it is worth 3).
    // Output: the question's full marks (at least 1 per tester)
    static int fullMarks(Path testerDir) {
        int total = 0;
        for (Path tester : testerFiles(testerDir)) {
            String className = tester.getFileName().toString().replace(".java", "");
            if (Files.isRegularFile(tester.resolveSibling(className + EXPECTED_SUFFIX))) {
                total += 1;
                continue;
            }
            int worth = 0;
            try {
                java.util.regex.Matcher matcher = java.util.regex.Pattern
                        .compile("score\\s*\\+=\\s*(\\d+)").matcher(Files.readString(tester, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    worth += Integer.parseInt(matcher.group(1));
                }
            } catch (IOException exception) {
                worth = 0; // Unreadable: counted as 1 below
            }
            total += Math.max(worth, 1);
        }
        return total;
    }

    // Input: the testers folder the instructor configured
    // Function: The course gives all testers in one folder (Q1aTester.java, Q1bTester.java, Q2aTester.java, ...).
    //           The grader needs one folder per question, so this copies them into a temporary folder:
    //           Q1aTester and Q1bTester go to Q1/, Q3Tester goes to Q3/ (the question is the name up to
    //           the first number, e.g. "Q1"). Any <Name>Tester.expected.txt files are copied along.
    // Output: the temporary folder (delete it after grading), or null when the folder already has
    //         question sub-folders or has no tester files at the top level.
    static Path groupFlatTesters(Path testersRoot) throws IOException {
        List<Path> testers = testerFiles(testersRoot);
        if (testers.isEmpty()) {
            return null;
        }
        // Already one folder per question: use it as it is
        try (Stream<Path> paths = Files.list(testersRoot)) {
            if (paths.anyMatch(Files::isDirectory)) {
                return null;
            }
        }
        Path grouped = Files.createTempDirectory("cs102-testers-");
        java.util.regex.Pattern questionName = java.util.regex.Pattern.compile("^([A-Za-z]*\\d+)");
        for (Path tester : testers) {
            String className = tester.getFileName().toString().replace(".java", "");
            String baseName = className.substring(0, className.length() - "Tester".length()); // e.g. Q1a
            java.util.regex.Matcher matcher = questionName.matcher(baseName);
            String question = matcher.find() ? matcher.group(1) : baseName; // e.g. Q1
            Path folder = Files.createDirectories(grouped.resolve(question));
            Files.copy(tester, folder.resolve(tester.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            Path expected = tester.resolveSibling(className + EXPECTED_SUFFIX);
            if (Files.isRegularFile(expected)) {
                Files.copy(expected, folder.resolve(expected.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return grouped;
    }

    // Converts Windows line endings to Unix ones so only the text itself decides the comparison.
    private static String normalise(String text) {
        // Replace every carriage return plus line feed pair with a single line feed.
        return text.replace("\r\n", "\n");
    }

    // True when the folder directly contains a sub-folder named after one of the questions.
    private static boolean holdsQuestion(Path folder, List<String> questions) {
        // True as soon as one configured question name exists as a directory inside the folder.
        return questions.stream().anyMatch(question -> Files.isDirectory(folder.resolve(question)));
    }

    // The instructor's tester sources in a question folder, sorted by name; empty if the folder is unreadable.
    private static List<Path> testerFiles(Path testerDir) {
        // The listing holds an OS handle, so try-with-resources closes it.
        try (Stream<Path> paths = Files.list(testerDir)) {
            // Keep plain files whose name ends in Tester.java; the .expected.txt files and sub-folders are left out. Sorting makes the order stable.
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(TESTER_SUFFIX))
                    .sorted().toList();
        } catch (IOException exception) {
            // An unreadable folder looks like one with no testers, which grade() then reports as missing-tester.
            return List.of();
        }
    }

    // Input: the student's folder for one question
    // Function: lists the files to copy into the build folder: sources, resources such as persons.txt, and
    //           .class files that have no .java source next to them (the template's DataException.class,
    //           Shape.class, ...). A .class file that has its .java next to it is an old compiled copy of the
    //           student's code and is left out, so the source is always what gets graded. Hidden files
    //           such as .DS_Store are left out too.
    // Output: the files, sorted by name
    private static List<Path> studentFiles(Path studentQuestion) {
        // The listing holds an OS handle, so try-with-resources closes it.
        try (Stream<Path> paths = Files.list(studentQuestion)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .filter(path -> !path.getFileName().toString().endsWith(".class")
                            || !Files.exists(path.resolveSibling(
                                    path.getFileName().toString().replace(".class", ".java"))))
                    .sorted().toList();
        } catch (IOException exception) {
            // An unreadable folder looks like one with no files, which grade() then reports as missing-file.
            return List.of();
        }
    }
}
