package autogradesys.grading;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Grades one question of a template-shaped CS102 submission with the instructor's tester classes.
 *
 * <p>The testers folder holds one folder per question. Each holds {@code <Name>Tester.java} classes
 * (each with a {@code main} method) and one {@code <Name>Tester.expected.txt} per tester. The
 * student's question files and the testers are compiled together, each tester is run, and its
 * output is compared with its expected file. Every passing tester is worth one mark.</p>
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
    /**
     * Creates a tester grader instance.
     */
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
            List<String> sources = new ArrayList<>();
            for (Path tester : testers) {
                // A tester with the same name as a student file overwrites it, so the student cannot replace the instructor's tester.
                Files.copy(tester, build.resolve(tester.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
            }
            // javac gets every .java file in the build folder, student and tester alike, in one compilation.
            // The listing holds an OS handle, so it is closed by try-with-resources; sorting keeps the javac argument order stable.
            try (Stream<Path> copied = Files.list(build)) {
                copied.filter(path -> path.getFileName().toString().endsWith(".java")).sorted()
                        .forEach(path -> sources.add(path.toAbsolutePath().toString()));
            }
            // Command is a list, not a shell string, so file names cannot be reinterpreted as shell syntax.
            List<String> compile = new ArrayList<>(List.of("javac", "-encoding", "UTF-8", "-d",
                    build.toAbsolutePath().toString()));
            // Append the source files after the options, then run javac under the timeout and output cap with the build folder as working directory.
            compile.addAll(sources);
            ProcessRunner.Result compilation = ProcessRunner.run(compile, "", timeout, build, outputLimitBytes);
            // A compiler that floods its output is stopped and every tester of the question scores zero.
            if (compilation.outputLimitExceeded()) {
                reporter.recoverable("question", "output-limit",
                        "Compiler output exceeded the combined stdout/stderr limit of "
                                + JavaGrader.outputLimitDescription(outputLimitBytes) + "; retained output is truncated.",
                        "Reduce compiler diagnostics or raise the output limit (menu option 2, or --output-limit-bytes).",
                        student, JavaGrader.display(studentQuestion), question);
                return 0;
            }
            // A compile that never finishes is stopped at the deadline.
            if (compilation.timedOut()) {
                reporter.recoverable("question", "timeout",
                        "Compilation exceeded " + JavaGrader.timeoutDescription(timeout) + ".",
                        "Check the submission and retry with valid Java source.",
                        student, JavaGrader.display(studentQuestion), question);
                return 0;
            }
            // Any compile error (including a class the tester needs but the student did not provide) scores zero.
            if (compilation.exitCode() != 0) {
                reporter.recoverable("question", "compile", JavaGrader.details(compilation.stderr(),
                                "javac exited with status " + compilation.exitCode() + "."),
                        "Fix the Java compiler errors and resubmit; check that every file from the template is present.",
                        student, JavaGrader.display(studentQuestion), question);
                return 0;
            }

            // Compilation succeeded, so run each tester separately and count the ones that pass.
            int marks = 0;
            for (Path tester : testers) {
                // runTester returns 1 for a pass and 0 for any failure (which it records itself), so the sum is the question's mark.
                marks += runTester(student, studentQuestion, question, tester, build, timeout, outputLimitBytes,
                        reporter);
            }
            // Marks earned for this question, from 0 up to the number of testers.
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

    // Runs one compiled tester inside the build folder and returns 1 if its output matches, else 0.
    /**
     * Run tester.
     */
    private static int runTester(String student, Path studentQuestion, String question, Path tester, Path build,
            Duration timeout, long outputLimitBytes, ErrorReporter reporter)
            throws IOException, InterruptedException {
        // File name of the tester source, e.g. Q1aTester.java.
        String fileName = tester.getFileName().toString();
        // Class name to run: the file name without ".java".
        String className = fileName.substring(0, fileName.length() - ".java".length());
        // Expected output sits next to the tester in the instructor's folder, e.g. Q1aTester.expected.txt.
        Path expectedPath = tester.resolveSibling(className + EXPECTED_SUFFIX);
        // A tester with no expected file cannot be judged, so it scores zero and the rest still run.
        if (!Files.isRegularFile(expectedPath)) {
            reporter.recoverable("question", "missing-expected",
                    "Expected output file is missing for " + className + ": " + JavaGrader.display(expectedPath),
                    "Add " + className + EXPECTED_SUFFIX + " next to the tester before grading.",
                    student, JavaGrader.display(studentQuestion), question);
            return 0;
        }
        // Read the expected output as UTF-8 text; an unreadable file is a setup problem for this tester only.
        final String expected;
        try {
            // final String above is assigned exactly once here; the decode is explicit so it does not depend on the machine's default charset.
            expected = Files.readString(expectedPath, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            reporter.recoverable("question", "fixture-read",
                    "Could not read " + JavaGrader.display(expectedPath) + ": " + exception.getMessage(),
                    "Ensure the expected output file is readable.",
                    student, JavaGrader.display(studentQuestion), question);
            return 0;
        }
        // No shell: java gets the class path and class name as separate arguments. The working
        // directory is the build folder so relative names like "persons.txt" find the student's copy.
        List<String> execute = List.of("java", "-cp", build.toAbsolutePath().toString(), className);
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
            reporter.recoverable("question", "runtime", className + " failed: " + JavaGrader.details(execution.stderr(),
                            "Program exited with status " + execution.exitCode() + "."),
                    "Fix the runtime exception and resubmit.",
                    student, JavaGrader.display(studentQuestion), question);
            return 0;
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

    // Converts Windows line endings to Unix ones so only the text itself decides the comparison.
    /**
     * Normalise.
     */
    private static String normalise(String text) {
        // Replace every carriage return plus line feed pair with a single line feed.
        return text.replace("\r\n", "\n");
    }

    // True when the folder directly contains a sub-folder named after one of the questions.
    /**
     * Holds question.
     */
    private static boolean holdsQuestion(Path folder, List<String> questions) {
        // True as soon as one configured question name exists as a directory inside the folder.
        return questions.stream().anyMatch(question -> Files.isDirectory(folder.resolve(question)));
    }

    // The instructor's tester sources in a question folder, sorted by name; empty if the folder is unreadable.
    /**
     * Tester files.
     */
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

    // The student's regular files for a question, minus compiled classes and hidden OS files like .DS_Store.
    /**
     * Student files.
     */
    private static List<Path> studentFiles(Path studentQuestion) {
        // The listing holds an OS handle, so try-with-resources closes it.
        try (Stream<Path> paths = Files.list(studentQuestion)) {
            // Regular files only, dropping hidden files (.DS_Store) and stale .class files so only source and resources are compiled; sorted for a stable order.
            return paths.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .filter(path -> !path.getFileName().toString().endsWith(".class"))
                    .sorted().toList();
        } catch (IOException exception) {
            // An unreadable folder looks like one with no files, which grade() then reports as missing-file.
            return List.of();
        }
    }
}
