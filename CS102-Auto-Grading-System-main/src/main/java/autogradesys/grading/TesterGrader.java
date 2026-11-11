package autogradesys.grading;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Grades one question of a template-shaped CS102 submission with the instructor's tester classes.
 *
 * <p>The testers folder holds one folder per question, each with {@code <Name>Tester.java} classes
 * (each with a {@code main} method). The instructors' own testers can also be given as loose files
 * ({@code Q1aTester.java}, ...); {@link #groupFlatTesters} sorts those into question folders first.
 * Each tester is compiled with the student's files for that question and run in that folder.</p>
 *
 * <p>A tester is scored in one of two ways. If a {@code <Name>Tester.expected.txt} sits next to it,
 * its output must match that file exactly (one mark). Otherwise the tester prints its own score as
 * the last line of its output, as the instructors' testers do (e.g. {@code 3.0}), and that number
 * is the mark.</p>
 *
 * <p>Defects in a single submission are reported as recoverable so grading continues with the next
 * tester, question and student.</p>
 */
final class TesterGrader {
    // Suffix that marks an instructor tester class file, e.g. Q1aTester.java.
    private static final String TESTER_SUFFIX = "Tester.java";
    // Suffix of the expected-output file that sits next to each tester, e.g. Q1aTester.expected.txt.
    private static final String EXPECTED_SUFFIX = ".expected.txt";
    // A loose instructor tester and its question: Q1aTester.java belongs to Q1, Q3Tester.java to Q3.
    private static final Pattern FLAT_TESTER = Pattern.compile("^(Q\\d+)[A-Za-z]?Tester\\.java$",
            Pattern.CASE_INSENSITIVE);
    // The score line an instructor tester prints last, e.g. "3.0".
    private static final Pattern SCORE_LINE = Pattern.compile("-?\\d+(\\.\\d+)?");
    // How an instructor tester adds one mark for a passed test case, e.g. "score += 1;".
    private static final Pattern SCORE_INCREMENT = Pattern.compile("score\\s*\\+=\\s*1\\b|score\\s*\\+\\+");

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

    /** Number of tester classes in the question folder. */
    static int testerCount(Path testerDir) {
        return testerFiles(testerDir).size();
    }

    /**
     * Marks the question is worth: one per tester with an expected file, and for a tester that prints
     * its own score, the number of {@code score += 1} lines in its source (at least one).
     */
    static int maxMarks(Path testerDir) {
        int total = 0;
        for (Path tester : testerFiles(testerDir)) {
            if (Files.isRegularFile(expectedFile(tester))) {
                total += 1;
                continue;
            }
            try {
                Matcher increments = SCORE_INCREMENT.matcher(Files.readString(tester, StandardCharsets.UTF_8));
                int count = 0;
                while (increments.find()) {
                    count++;
                }
                total += Math.max(1, count);
            } catch (IOException exception) {
                // An unreadable tester is reported when it is graded; count it as one mark here.
                total += 1;
            }
        }
        return total;
    }

    /**
     * True when the testers folder holds loose tester files such as {@code Q1aTester.java} and no
     * sub-folders, which is how the instructors hand out their testers.
     */
    static boolean isFlatTesterFolder(Path testersRoot) {
        try (Stream<Path> paths = Files.list(testersRoot)) {
            List<Path> entries = paths.toList();
            boolean hasFolders = entries.stream().anyMatch(Files::isDirectory);
            boolean hasTesters = entries.stream().anyMatch(path -> Files.isRegularFile(path)
                    && FLAT_TESTER.matcher(path.getFileName().toString()).matches());
            return !hasFolders && hasTesters;
        } catch (IOException exception) {
            return false;
        }
    }

    /**
     * Copies loose testers into a new temporary folder with one sub-folder per question, so
     * {@code Q1aTester.java} and {@code Q1bTester.java} end up in {@code Q1}. The caller deletes it.
     */
    static Path groupFlatTesters(Path testersRoot) throws IOException {
        Path grouped = Files.createTempDirectory("cs102-testers-");
        try (Stream<Path> paths = Files.list(testersRoot)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                String name = file.getFileName().toString();
                Matcher matcher = FLAT_TESTER.matcher(name);
                if (!matcher.matches()) {
                    // readme.txt and similar files are not testers.
                    continue;
                }
                Path question = Files.createDirectories(grouped.resolve(matcher.group(1).toUpperCase()));
                Files.copy(file, question.resolve(name));
                // An expected file next to a loose tester is kept with it.
                Path expected = expectedFile(file);
                if (Files.isRegularFile(expected)) {
                    Files.copy(expected, question.resolve(expected.getFileName().toString()));
                }
            }
        } catch (IOException exception) {
            JavaGrader.deleteTree(grouped);
            throw exception;
        }
        return grouped;
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

            // Compile and run each tester on its own, as the instructors do ("javac Q1aTester.java"), so a broken
            // Q1b.java does not cost the marks for Q1a. Each tester returns the marks it earned.
            int marks = 0;
            for (Path tester : testers) {
                // runTester returns the marks this tester earned, or 0 for any failure (which it records itself).
                marks += runTester(student, studentQuestion, question, tester, build, timeout, outputLimitBytes,
                        reporter);
            }
            // Marks earned for this question, from 0 up to maxMarks.
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

    // Compiles one tester with the student's files, runs it inside the build folder and returns the marks it earned.
    private static int runTester(String student, Path studentQuestion, String question, Path tester, Path build,
            Duration timeout, long outputLimitBytes, ErrorReporter reporter)
            throws IOException, InterruptedException {
        // File name of the tester source, e.g. Q1aTester.java.
        String fileName = tester.getFileName().toString();
        // Class name to run: the file name without ".java".
        String className = fileName.substring(0, fileName.length() - ".java".length());
        // Expected output sits next to the tester in the instructor's folder, e.g. Q1aTester.expected.txt.
        Path expectedPath = expectedFile(tester);
        // With an expected file the output is compared; without one the tester prints its own score.
        boolean printsScore = !Files.isRegularFile(expectedPath);
        String expected = null;
        if (!printsScore) {
            try {
                // Read the expected output as UTF-8 so it does not depend on the machine's default charset.
                expected = Files.readString(expectedPath, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                reporter.recoverable("question", "fixture-read",
                        "Could not read " + JavaGrader.display(expectedPath) + ": " + exception.getMessage(),
                        "Ensure the expected output file is readable.",
                        student, JavaGrader.display(studentQuestion), question);
                return 0;
            }
        }

        // Compile only this tester. javac finds the student classes it uses (Q1a.java, Shape.class, ...) in the
        // build folder, so a file this tester does not use cannot stop it compiling.
        String buildPath = build.toAbsolutePath().toString();
        List<String> compile = List.of("javac", "-encoding", "UTF-8", "-cp", buildPath, "-sourcepath", buildPath,
                "-d", buildPath, build.resolve(fileName).toAbsolutePath().toString());
        ProcessRunner.Result compilation = ProcessRunner.run(compile, "", timeout, build, outputLimitBytes);
        // A compiler that floods its output is stopped and this tester scores zero.
        if (compilation.outputLimitExceeded()) {
            reporter.recoverable("question", "output-limit",
                    "Compiler output for " + className + " exceeded the combined stdout/stderr limit of "
                            + JavaGrader.outputLimitDescription(outputLimitBytes) + "; retained output is truncated.",
                    "Reduce compiler diagnostics or raise the output limit (menu option 2).",
                    student, JavaGrader.display(studentQuestion), question);
            return 0;
        }
        // A compile that never finishes is stopped at the deadline.
        if (compilation.timedOut()) {
            reporter.recoverable("question", "timeout",
                    "Compiling " + className + " exceeded " + JavaGrader.timeoutDescription(timeout) + ".",
                    "Raise the grading timeout (menu option 2) if the computer is slow, then grade again.",
                    student, JavaGrader.display(studentQuestion), question);
            return 0;
        }
        // A compile error (including a class the tester needs but the student did not provide) scores zero.
        if (compilation.exitCode() != 0) {
            reporter.recoverable("question", "compile", className + ": " + JavaGrader.details(compilation.stderr(),
                            "javac exited with status " + compilation.exitCode() + "."),
                    "Fix the Java compiler errors and resubmit; check that every file from the template is present.",
                    student, JavaGrader.display(studentQuestion), question);
            return 0;
        }

        // No shell: java gets the class path and class name as separate arguments. The working
        // directory is the build folder so relative names like "persons.txt" find the student's copy.
        List<String> execute = List.of("java", "-cp", buildPath, className);
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
            // A score-printing tester never reaches its score line, so the test cases it passed before
            // it was stopped are counted from its "Passed" lines.
            int passed = printsScore ? countPassedLines(execution.stdout()) : 0;
            reporter.recoverable("question", "timeout",
                    className + " exceeded " + JavaGrader.timeoutDescription(timeout) + " and was terminated"
                            + (printsScore ? "; " + passed + " test case(s) passed before that." : "."),
                    "Check for an infinite loop or reduce the program's runtime.",
                    student, JavaGrader.display(studentQuestion), question);
            return passed;
        }
        // A non-zero exit means an uncaught exception, so show the stack trace the student's code produced.
        if (execution.exitCode() != 0) {
            reporter.recoverable("question", "runtime", className + " failed: " + JavaGrader.details(execution.stderr(),
                            "Program exited with status " + execution.exitCode() + "."),
                    "Fix the runtime exception and resubmit.",
                    student, JavaGrader.display(studentQuestion), question);
            return 0;
        }

        if (printsScore) {
            // The instructors' testers print their score as the last line, e.g. "3.0".
            OptionalDouble score = printedScore(execution.stdout());
            if (score.isEmpty()) {
                reporter.recoverable("question", "missing-score",
                        className + " did not print a score as its last line, and there is no "
                                + className + EXPECTED_SUFFIX + " to compare with.",
                        "Use a tester that prints its score last, or add " + className + EXPECTED_SUFFIX
                                + " next to it.",
                        student, JavaGrader.display(studentQuestion), question);
                return 0;
            }
            int marks = (int) Math.round(score.getAsDouble());
            reporter.info(String.format("SCORE student=%s submission=%s question=%s tester=%s score=%d",
                    student, JavaGrader.display(studentQuestion), question, className, marks));
            return marks;
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

    // The expected-output file for a tester: Q1aTester.java -> Q1aTester.expected.txt in the same folder.
    private static Path expectedFile(Path tester) {
        String fileName = tester.getFileName().toString();
        String className = fileName.substring(0, fileName.length() - ".java".length());
        return tester.resolveSibling(className + EXPECTED_SUFFIX);
    }

    // The number on the last non-blank line of the output, if that line is only a number.
    private static OptionalDouble printedScore(String stdout) {
        String[] lines = stdout.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            return SCORE_LINE.matcher(line).matches() ? OptionalDouble.of(Double.parseDouble(line)) : OptionalDouble.empty();
        }
        return OptionalDouble.empty();
    }

    // How many lines say exactly "Passed", which is what the instructors' testers print for each passed case.
    private static int countPassedLines(String stdout) {
        int count = 0;
        for (String line : stdout.split("\\R")) {
            if (line.trim().equals("Passed")) {
                count++;
            }
        }
        return count;
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

    // The student's regular files for a question, minus hidden OS files like .DS_Store and stale .class files.
    private static List<Path> studentFiles(Path studentQuestion) {
        // The listing holds an OS handle, so try-with-resources closes it.
        try (Stream<Path> paths = Files.list(studentQuestion)) {
            List<Path> files = paths.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .sorted().toList();
            // A .class file is kept only when there is no .java file for it. The template ships some classes
            // only in compiled form (DataException.class, Shape.class, ...), and the testers need them.
            // A Q1a.class next to Q1a.java is a stale copy, so it is dropped and the source is compiled instead.
            return files.stream().filter(path -> {
                String name = path.getFileName().toString();
                if (!name.endsWith(".class")) {
                    return true;
                }
                String outer = name.substring(0, name.length() - ".class".length()).split("\\$")[0];
                return !Files.isRegularFile(studentQuestion.resolve(outer + ".java"));
            }).toList();
        } catch (IOException exception) {
            // An unreadable folder looks like one with no files, which grade() then reports as missing-file.
            return List.of();
        }
    }
}
