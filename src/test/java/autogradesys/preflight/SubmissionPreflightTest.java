package autogradesys.preflight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import autogradesys.preflight.PreflightFinding.Category;
import autogradesys.preflight.PreflightFinding.Severity;

// Unit tests for the scanner. Every test builds a tiny synthetic folder tree in a temp directory.
class SubmissionPreflightTest {

    // JUnit creates a fresh empty temp folder per test and deletes it afterwards.
    @TempDir
    Path root;

    // The starter template folder used by every test.
    Path template;
    // The folder that holds the (synthetic) student submissions.
    Path subs;

    // Runs before each test: builds a small template with three questions.
    @BeforeEach
    void setUp() throws IOException {
        // The template folder carries the usual starter name.
        template = root.resolve("RenameToYourStudentID");
        // Q1 requires Q1a.java and Q1b.java.
        file(template, "Q1/Q1a.java", "public class Q1a {}");
        file(template, "Q1/Q1b.java", "public class Q1b {}");
        // Q2 requires Q2a.java and ships a resource, persons.txt.
        file(template, "Q2/Q2a.java", "public class Q2a {}");
        file(template, "Q2/persons.txt", "x");
        // Q3 requires Q3.java.
        file(template, "Q3/Q3.java", "public class Q3 {}");
        // An empty submissions folder that each test fills in.
        subs = Files.createDirectories(root.resolve("submissions"));
    }

    // Creates a file (and its parent folders) with the given text.
    static void file(Path base, String rel, String content) throws IOException {
        // Where the file goes.
        Path p = base.resolve(rel);
        // Make sure the parent folders exist.
        Files.createDirectories(p.getParent());
        // Write the content.
        Files.writeString(p, content);
    }

    // Builds a student folder that exactly matches the template (with real content).
    void cleanStudent(Path base) throws IOException {
        file(base, "Q1/Q1a.java", "class Q1a { int x; }");
        file(base, "Q1/Q1b.java", "class Q1b { int x; }");
        file(base, "Q2/Q2a.java", "class Q2a { int x; }");
        file(base, "Q2/persons.txt", "p");
        file(base, "Q3/Q3.java", "class Q3 { int x; }");
    }

    // Runs the scanner on the current template and submissions folders.
    PreflightReport run() {
        return SubmissionPreflight.check(template, subs);
    }

    // The findings of one category for one student.
    List<PreflightFinding> of(PreflightReport r, String student, Category category) {
        return r.findings().stream()
                .filter(f -> f.student().equals(student) && f.category() == category)
                .toList();
    }

    @Test
    void cleanSubmissionIsReady() throws IOException {
        // One student that matches the template exactly.
        cleanStudent(subs.resolve("alice"));
        PreflightReport r = run();
        // The student is discovered.
        assertEquals(List.of("alice"), r.students());
        // And is ready.
        assertFalse(r.needsAttention("alice"));
        // No errors or warnings at all.
        assertEquals(0, r.count(Severity.ERROR) + r.count(Severity.WARNING));
        // The summary count agrees.
        assertEquals(1, r.readyCount());
    }

    @Test
    void unrenamedWrapperIsOneFindingAndInnerContentIsStillChecked() throws IOException {
        // Everything is correct but sits inside an un-renamed starter folder.
        cleanStudent(subs.resolve("bob/RenameToYourStudentID"));
        PreflightReport r = run();
        // Exactly one WRAPPED_FOLDER finding, not one MISSING_QUESTION per question.
        List<PreflightFinding> wrapped = of(r, "bob", Category.WRAPPED_FOLDER);
        assertEquals(1, wrapped.size());
        // It points at the wrapper folder.
        assertEquals("bob/RenameToYourStudentID/", wrapped.get(0).path());
        // And tells the user where to move the contents.
        assertTrue(wrapped.get(0).fix().contains("up into bob/"));
        // No spurious missing-question errors.
        assertTrue(of(r, "bob", Category.MISSING_QUESTION).isEmpty());
        // The student still needs attention because of the wrapper.
        assertTrue(r.needsAttention("bob"));
    }

    @Test
    void studentNumberWrapperStillReportsProblemsInside() throws IOException {
        // A wrapper named after a student number, with problems inside it.
        cleanStudent(subs.resolve("dave/01400003"));
        // Remove a required file...
        Files.delete(subs.resolve("dave/01400003/Q3/Q3.java"));
        // ...and add an extra one.
        file(subs, "dave/01400003/Q1/Extra.java", "class Extra {}");
        PreflightReport r = run();
        // The wrapper is reported.
        assertEquals(1, of(r, "dave", Category.WRAPPED_FOLDER).size());
        // The missing file inside the wrapper is still found.
        assertEquals("dave/01400003/Q3/Q3.java", of(r, "dave", Category.MISSING_FILE).get(0).path());
        // So is the extra file.
        assertEquals("dave/01400003/Q1/Extra.java", of(r, "dave", Category.EXTRA_FILE).get(0).path());
    }

    @Test
    void missingQuestionListsExpectedFiles() throws IOException {
        // A good student with Q3 deleted.
        cleanStudent(subs.resolve("carol"));
        deleteTree(subs.resolve("carol/Q3"));
        PreflightFinding f = of(run(), "carol", Category.MISSING_QUESTION).get(0);
        // The missing folder is named...
        assertEquals("carol/Q3/", f.path());
        // ...and the fix lists the files that belong in it.
        assertTrue(f.fix().contains("Q3.java"));
    }

    @Test
    void misnamedFileAndFolderAreNotAlsoMissingOrExtra() throws IOException {
        cleanStudent(subs.resolve("carol"));
        // Rename Q1a.java to q1a.java via a temporary name (works on case-insensitive disks too).
        Files.move(subs.resolve("carol/Q1/Q1a.java"), subs.resolve("carol/Q1/q1a.tmp"));
        Files.move(subs.resolve("carol/Q1/q1a.tmp"), subs.resolve("carol/Q1/q1a.java"));
        // Likewise rename the folder Q2 to q2.
        Files.move(subs.resolve("carol/Q2"), subs.resolve("carol/q2tmp"));
        Files.move(subs.resolve("carol/q2tmp"), subs.resolve("carol/q2"));
        PreflightReport r = run();
        // Two MISNAMED findings: one file, one folder.
        List<PreflightFinding> misnamed = of(r, "carol", Category.MISNAMED);
        assertEquals(2, misnamed.size());
        // Each carries the exact rename instruction.
        assertTrue(misnamed.stream().anyMatch(f -> f.fix().equals("Rename q1a.java to Q1a.java")));
        assertTrue(misnamed.stream().anyMatch(f -> f.fix().equals("Rename q2 to Q2")));
        // And none of them is double-reported as missing or extra.
        assertTrue(of(r, "carol", Category.MISSING_FILE).isEmpty());
        assertTrue(of(r, "carol", Category.MISSING_QUESTION).isEmpty());
        assertTrue(of(r, "carol", Category.EXTRA_FILE).isEmpty());
    }

    @Test
    void missingRequiredFileIsReported() throws IOException {
        cleanStudent(subs.resolve("erin"));
        // Remove one required file.
        Files.delete(subs.resolve("erin/Q1/Q1b.java"));
        PreflightFinding f = of(run(), "erin", Category.MISSING_FILE).get(0);
        // The finding names the file and says where to add it.
        assertEquals("erin/Q1/Q1b.java", f.path());
        assertEquals("Add Q1b.java to Q1/", f.fix());
    }

    @Test
    void emptyAndWhitespaceOnlyFilesAreReported() throws IOException {
        cleanStudent(subs.resolve("erin"));
        // One zero-byte file and one that holds only whitespace.
        Files.writeString(subs.resolve("erin/Q1/Q1a.java"), "");
        Files.writeString(subs.resolve("erin/Q1/Q1b.java"), " \n\t\r\n");
        // Both count as empty.
        assertEquals(2, of(run(), "erin", Category.EMPTY_FILE).size());
    }

    @Test
    void extraJavaFileAndFolderAreWarningsOnly() throws IOException {
        cleanStudent(subs.resolve("alice"));
        // An extra helper class and an extra folder.
        file(subs, "alice/Q3/ShapeComparators.java", "class ShapeComparators {}");
        Files.createDirectories(subs.resolve("alice/notes"));
        PreflightReport r = run();
        // Both are reported as extras...
        assertEquals(2, of(r, "alice", Category.EXTRA_FILE).size());
        // ...but only as warnings, so the student stays ready.
        assertFalse(r.needsAttention("alice"));
    }

    @Test
    void junkIsCountedAsNotesAndNeverNeedsAttention() throws IOException {
        cleanStudent(subs.resolve("alice"));
        // Four kinds of clutter in different places.
        file(subs, "alice/.DS_Store", "x");
        file(subs, "alice/Q2/.DS_Store", "x");
        file(subs, "alice/Q1/Q1a.class", "x");
        file(subs, "__MACOSX/alice/x", "x");
        PreflightReport r = run();
        // Clutter never creates a "student" of its own.
        assertEquals(List.of("alice"), r.students());
        // All four are notes.
        assertEquals(4, r.count(Severity.NOTE));
        // No errors or warnings result.
        assertEquals(0, r.count(Severity.ERROR) + r.count(Severity.WARNING));
        // The student stays ready.
        assertFalse(r.needsAttention("alice"));
    }

    @Test
    void zipFilesAndZipOnlyFoldersNeedExtracting() throws IOException {
        // A zip in the submissions folder and a folder whose only content is a zip.
        file(subs, "frank.zip", "PK");
        file(subs, "gina/gina.zip", "PK");
        PreflightReport r = run();
        // Both are discovered as submissions, sorted.
        assertEquals(List.of("frank.zip", "gina"), r.students());
        // The root zip gets the "extract into a folder" fix.
        assertEquals("Extract frank.zip into a folder before grading", of(r, "frank.zip", Category.UNEXTRACTED_ARCHIVE).get(0).fix());
        // The zip-only folder is flagged as well.
        assertEquals(1, of(r, "gina", Category.UNEXTRACTED_ARCHIVE).size());
        // Neither is ready.
        assertEquals(0, r.readyCount());
    }

    @Test
    void zipBesideItsExtractedFolderIsNotACountedStudent() throws IOException {
        // frank.zip sits next to the frank/ folder it was extracted into (same name, any letter case).
        cleanStudent(subs.resolve("frank"));
        file(subs, "frank.zip", "PK");
        file(subs, "GINA.ZIP", "PK");
        cleanStudent(subs.resolve("gina"));
        PreflightReport r = run();
        // Only the two real folders are students; neither zip is counted.
        assertEquals(List.of("frank", "gina"), r.students());
        // Both students are ready: the leftover zips are not errors.
        assertEquals(2, r.readyCount());
        // Each ignored zip is explained by a run-level note.
        List<PreflightFinding> notes = of(r, "", Category.ARCHIVE_ALREADY_EXTRACTED);
        assertEquals(2, notes.size());
        // The note names the folder that is checked instead.
        assertTrue(notes.get(0).message().contains("frank/") || notes.get(1).message().contains("frank/"));
        // And no "extract me" error appears.
        assertTrue(r.findings().stream().noneMatch(f -> f.category() == Category.UNEXTRACTED_ARCHIVE));
    }

    @Test
    void folderNamedLikeTemplateIsFlagged() throws IOException {
        // A complete-looking folder that carries the template's own name.
        cleanStudent(subs.resolve("RenameToYourStudentID"));
        PreflightReport r = run();
        // It is flagged as a probable template copy.
        assertEquals(1, of(r, "RenameToYourStudentID", Category.TEMPLATE_COPY).size());
        // Grading skips it, so it is shown as not graded and counted on its own, not as ready.
        assertEquals(0, r.readyCount());
        assertEquals(1, r.notGradedCount());
        String printed = printed(r);
        assertTrue(printed.contains("RenameToYourStudentID ...... not graded"), printed);
        assertTrue(printed.contains("Checked 1 submissions: 0 ready, 0 need attention, 1 not graded"), printed);
    }

    @Test
    void missingOrEmptyTemplateIsFatalAndScansNothing() throws IOException {
        // A valid student exists, but the template will be invalid.
        cleanStudent(subs.resolve("alice"));
        // 1) The template folder does not exist.
        PreflightReport missing = SubmissionPreflight.check(root.resolve("nope"), subs);
        assertTrue(missing.fatal());
        // No students are scanned on a fatal report.
        assertTrue(missing.students().isEmpty());
        assertEquals(Category.TEMPLATE_INVALID, missing.findings().get(0).category());

        // 2) The template has no question folders.
        Path noQuestions = Files.createDirectories(root.resolve("empty-template"));
        file(noQuestions, "loose.java", "x");
        assertTrue(SubmissionPreflight.check(noQuestions, subs).fatal());

        // 3) The submissions folder does not exist.
        PreflightReport badSubs = SubmissionPreflight.check(template, root.resolve("nowhere"));
        assertTrue(badSubs.fatal());
        assertEquals(Category.SUBMISSIONS_INVALID, badSubs.findings().get(0).category());
    }

    @Test
    void startingFolderOneLevelAboveTheTemplateIsRejectedWithGuidance() throws IOException {
        // The Starting Folder is the PARENT of RenameToYourStudentID (it also holds submissions/).
        cleanStudent(subs.resolve("alice"));
        PreflightReport r = SubmissionPreflight.check(root, subs);
        // The check refuses to run instead of flooding every student with false errors.
        assertTrue(r.fatal());
        // No student is scanned or marked ready.
        assertTrue(r.students().isEmpty());
        // There is a single clear diagnostic.
        assertEquals(1, r.findings().size());
        PreflightFinding f = r.findings().get(0);
        assertEquals(Category.TEMPLATE_INVALID, f.category());
        // It explains the folder is one level too high...
        assertTrue(f.message().contains("one level too high"));
        // ...and names the exact folder to choose instead.
        assertTrue(f.fix().endsWith("RenameToYourStudentID"), f.fix());
        // The printed form says nothing was scanned.
        assertTrue(printed(r).contains("no submissions were scanned"));
    }

    @Test
    void symbolicLinkedSubmissionIsReportedNotFollowed() throws IOException {
        // A complete submission that lives OUTSIDE the submissions folder.
        Path outside = Files.createDirectories(root.resolve("outside"));
        cleanStudent(outside);
        try {
            // A link inside the submissions folder pointing at it.
            Files.createSymbolicLink(subs.resolve("sneaky"), outside);
        } catch (IOException | UnsupportedOperationException e) {
            return; // platform does not allow symlinks
        }
        PreflightReport r = run();
        // The link is listed as a submission so it is not silently dropped...
        assertEquals(List.of("sneaky"), r.students());
        // ...flagged as not followed...
        assertEquals(1, of(r, "sneaky", Category.SYMLINK_NOT_FOLLOWED).size());
        // ...and never reported as ready.
        assertTrue(r.needsAttention("sneaky"));
    }

    @Test
    void checkDoesNotChangeAnything() throws IOException {
        // A mix of clean, wrapped, archived and junk content.
        cleanStudent(subs.resolve("alice"));
        cleanStudent(subs.resolve("bob/RenameToYourStudentID"));
        file(subs, "frank.zip", "PK");
        file(subs, "alice/.DS_Store", "x");
        // Record every path, size and modification time.
        String before = snapshot();
        // Run the check and print the report.
        run().print(new PrintStream(new ByteArrayOutputStream()));
        // Nothing about the tree may differ afterwards.
        assertEquals(before, snapshot());
    }

    @Test
    void outputIsDeterministicAndSorted() throws IOException {
        // Create students in non-alphabetical order.
        cleanStudent(subs.resolve("zed"));
        cleanStudent(subs.resolve("alice"));
        file(subs, "mike/Q1/q1a.java", "class Q1a { int x; }");
        // Two runs must print identical text.
        String first = printed(run());
        String second = printed(run());
        assertEquals(first, second);
        // Students come out sorted by name.
        assertEquals(List.of("alice", "mike", "zed"), run().students());
        assertTrue(first.indexOf("alice ") < first.indexOf("mike ") && first.indexOf("mike ") < first.indexOf("zed "));
        // And the summary line is exact.
        assertTrue(first.contains("Checked 3 submissions: 2 ready, 1 need attention"));
    }

    // Helpers

    // Captures what print(...) would show on the console.
    static String printed(PreflightReport report) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        report.print(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return bytes.toString(StandardCharsets.UTF_8);
    }

    // One text line per path: relative name, size (or "dir") and modification time.
    private String snapshot() throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.sorted().map(p -> {
                try {
                    return root.relativize(p) + "|" + (Files.isDirectory(p) ? "dir" : Files.size(p))
                            + "|" + Files.getLastModifiedTime(p).toMillis();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.joining("\n"));
        }
    }

    // Deletes a folder and everything in it (used only on the test's own temp files).
    private static void deleteTree(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            // Delete children before their parents.
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
