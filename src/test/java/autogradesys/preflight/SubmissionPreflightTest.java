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

class SubmissionPreflightTest {

    @TempDir
    Path root;

    Path template;
    Path subs;

    @BeforeEach
    void setUp() throws IOException {
        template = root.resolve("RenameToYourStudentID");
        file(template, "Q1/Q1a.java", "public class Q1a {}");
        file(template, "Q1/Q1b.java", "public class Q1b {}");
        file(template, "Q2/Q2a.java", "public class Q2a {}");
        file(template, "Q2/persons.txt", "x");
        file(template, "Q3/Q3.java", "public class Q3 {}");
        subs = Files.createDirectories(root.resolve("submissions"));
    }

    static void file(Path base, String rel, String content) throws IOException {
        Path p = base.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    void cleanStudent(Path base) throws IOException {
        file(base, "Q1/Q1a.java", "class Q1a { int x; }");
        file(base, "Q1/Q1b.java", "class Q1b { int x; }");
        file(base, "Q2/Q2a.java", "class Q2a { int x; }");
        file(base, "Q2/persons.txt", "p");
        file(base, "Q3/Q3.java", "class Q3 { int x; }");
    }

    PreflightReport run() {
        return SubmissionPreflight.check(template, subs);
    }

    List<PreflightFinding> of(PreflightReport r, String student, Category category) {
        return r.findings().stream()
                .filter(f -> f.student().equals(student) && f.category() == category)
                .toList();
    }

    @Test
    void cleanSubmissionIsReady() throws IOException {
        cleanStudent(subs.resolve("alice"));
        PreflightReport r = run();
        assertEquals(List.of("alice"), r.students());
        assertFalse(r.needsAttention("alice"));
        assertEquals(0, r.count(Severity.ERROR) + r.count(Severity.WARNING));
        assertEquals(1, r.readyCount());
    }

    @Test
    void unrenamedWrapperIsOneFindingAndInnerContentIsStillChecked() throws IOException {
        cleanStudent(subs.resolve("bob/RenameToYourStudentID"));
        PreflightReport r = run();
        List<PreflightFinding> wrapped = of(r, "bob", Category.WRAPPED_FOLDER);
        assertEquals(1, wrapped.size());
        assertEquals("bob/RenameToYourStudentID/", wrapped.get(0).path());
        assertTrue(wrapped.get(0).fix().contains("up into bob/"));
        assertTrue(of(r, "bob", Category.MISSING_QUESTION).isEmpty());
        assertTrue(r.needsAttention("bob"));
    }

    @Test
    void studentNumberWrapperStillReportsProblemsInside() throws IOException {
        cleanStudent(subs.resolve("dave/01400003"));
        Files.delete(subs.resolve("dave/01400003/Q3/Q3.java"));
        file(subs, "dave/01400003/Q1/Extra.java", "class Extra {}");
        PreflightReport r = run();
        assertEquals(1, of(r, "dave", Category.WRAPPED_FOLDER).size());
        assertEquals("dave/01400003/Q3/Q3.java", of(r, "dave", Category.MISSING_FILE).get(0).path());
        assertEquals("dave/01400003/Q1/Extra.java", of(r, "dave", Category.EXTRA_FILE).get(0).path());
    }

    @Test
    void missingQuestionListsExpectedFiles() throws IOException {
        cleanStudent(subs.resolve("carol"));
        deleteTree(subs.resolve("carol/Q3"));
        PreflightFinding f = of(run(), "carol", Category.MISSING_QUESTION).get(0);
        assertEquals("carol/Q3/", f.path());
        assertTrue(f.fix().contains("Q3.java"));
    }

    @Test
    void misnamedFileAndFolderAreNotAlsoMissingOrExtra() throws IOException {
        cleanStudent(subs.resolve("carol"));
        Files.move(subs.resolve("carol/Q1/Q1a.java"), subs.resolve("carol/Q1/q1a.tmp"));
        Files.move(subs.resolve("carol/Q1/q1a.tmp"), subs.resolve("carol/Q1/q1a.java"));
        Files.move(subs.resolve("carol/Q2"), subs.resolve("carol/q2tmp"));
        Files.move(subs.resolve("carol/q2tmp"), subs.resolve("carol/q2"));
        PreflightReport r = run();
        List<PreflightFinding> misnamed = of(r, "carol", Category.MISNAMED);
        assertEquals(2, misnamed.size());
        assertTrue(misnamed.stream().anyMatch(f -> f.fix().equals("Rename q1a.java to Q1a.java")));
        assertTrue(misnamed.stream().anyMatch(f -> f.fix().equals("Rename q2 to Q2")));
        assertTrue(of(r, "carol", Category.MISSING_FILE).isEmpty());
        assertTrue(of(r, "carol", Category.MISSING_QUESTION).isEmpty());
        assertTrue(of(r, "carol", Category.EXTRA_FILE).isEmpty());
    }

    @Test
    void missingRequiredFileIsReported() throws IOException {
        cleanStudent(subs.resolve("erin"));
        Files.delete(subs.resolve("erin/Q1/Q1b.java"));
        PreflightFinding f = of(run(), "erin", Category.MISSING_FILE).get(0);
        assertEquals("erin/Q1/Q1b.java", f.path());
        assertEquals("Add Q1b.java to Q1/", f.fix());
    }

    @Test
    void emptyAndWhitespaceOnlyFilesAreReported() throws IOException {
        cleanStudent(subs.resolve("erin"));
        Files.writeString(subs.resolve("erin/Q1/Q1a.java"), "");
        Files.writeString(subs.resolve("erin/Q1/Q1b.java"), " \n\t\r\n");
        assertEquals(2, of(run(), "erin", Category.EMPTY_FILE).size());
    }

    @Test
    void extraJavaFileAndFolderAreWarningsOnly() throws IOException {
        cleanStudent(subs.resolve("alice"));
        file(subs, "alice/Q3/ShapeComparators.java", "class ShapeComparators {}");
        Files.createDirectories(subs.resolve("alice/notes"));
        PreflightReport r = run();
        assertEquals(2, of(r, "alice", Category.EXTRA_FILE).size());
        assertFalse(r.needsAttention("alice"));
    }

    @Test
    void junkIsCountedAsNotesAndNeverNeedsAttention() throws IOException {
        cleanStudent(subs.resolve("alice"));
        file(subs, "alice/.DS_Store", "x");
        file(subs, "alice/Q2/.DS_Store", "x");
        file(subs, "alice/Q1/Q1a.class", "x");
        file(subs, "__MACOSX/alice/x", "x");
        PreflightReport r = run();
        assertEquals(List.of("alice"), r.students());
        assertEquals(4, r.count(Severity.NOTE));
        assertEquals(0, r.count(Severity.ERROR) + r.count(Severity.WARNING));
        assertFalse(r.needsAttention("alice"));
    }

    @Test
    void zipFilesAndZipOnlyFoldersNeedExtracting() throws IOException {
        file(subs, "frank.zip", "PK");
        file(subs, "gina/gina.zip", "PK");
        PreflightReport r = run();
        assertEquals(List.of("frank.zip", "gina"), r.students());
        assertEquals("Extract frank.zip into a folder before grading", of(r, "frank.zip", Category.UNEXTRACTED_ARCHIVE).get(0).fix());
        assertEquals(1, of(r, "gina", Category.UNEXTRACTED_ARCHIVE).size());
        assertEquals(0, r.readyCount());
    }

    @Test
    void folderNamedLikeTemplateIsFlagged() throws IOException {
        cleanStudent(subs.resolve("RenameToYourStudentID"));
        PreflightReport r = run();
        assertEquals(1, of(r, "RenameToYourStudentID", Category.TEMPLATE_COPY).size());
    }

    @Test
    void missingOrEmptyTemplateIsFatalAndScansNothing() throws IOException {
        cleanStudent(subs.resolve("alice"));
        PreflightReport missing = SubmissionPreflight.check(root.resolve("nope"), subs);
        assertTrue(missing.fatal());
        assertTrue(missing.students().isEmpty());
        assertEquals(Category.TEMPLATE_INVALID, missing.findings().get(0).category());

        Path noQuestions = Files.createDirectories(root.resolve("empty-template"));
        file(noQuestions, "loose.java", "x");
        assertTrue(SubmissionPreflight.check(noQuestions, subs).fatal());

        PreflightReport badSubs = SubmissionPreflight.check(template, root.resolve("nowhere"));
        assertTrue(badSubs.fatal());
        assertEquals(Category.SUBMISSIONS_INVALID, badSubs.findings().get(0).category());
    }

    @Test
    void symbolicLinkedSubmissionIsReportedNotFollowed() throws IOException {
        Path outside = Files.createDirectories(root.resolve("outside"));
        cleanStudent(outside);
        try {
            Files.createSymbolicLink(subs.resolve("sneaky"), outside);
        } catch (IOException | UnsupportedOperationException e) {
            return; // platform does not allow symlinks
        }
        PreflightReport r = run();
        assertEquals(List.of("sneaky"), r.students());
        assertEquals(1, of(r, "sneaky", Category.SYMLINK_NOT_FOLLOWED).size());
        assertTrue(r.needsAttention("sneaky"));
    }

    @Test
    void checkDoesNotChangeAnything() throws IOException {
        cleanStudent(subs.resolve("alice"));
        cleanStudent(subs.resolve("bob/RenameToYourStudentID"));
        file(subs, "frank.zip", "PK");
        file(subs, "alice/.DS_Store", "x");
        String before = snapshot();
        run().print(new PrintStream(new ByteArrayOutputStream()));
        assertEquals(before, snapshot());
    }

    @Test
    void outputIsDeterministicAndSorted() throws IOException {
        cleanStudent(subs.resolve("zed"));
        cleanStudent(subs.resolve("alice"));
        file(subs, "mike/Q1/q1a.java", "class Q1a { int x; }");
        String first = printed(run());
        String second = printed(run());
        assertEquals(first, second);
        assertEquals(List.of("alice", "mike", "zed"), run().students());
        assertTrue(first.indexOf("alice ") < first.indexOf("mike ") && first.indexOf("mike ") < first.indexOf("zed "));
        assertTrue(first.contains("Checked 3 submissions: 2 ready, 1 need attention"));
    }

    // Helpers

    static String printed(PreflightReport report) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        report.print(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return bytes.toString(StandardCharsets.UTF_8);
    }

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

    private static void deleteTree(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
