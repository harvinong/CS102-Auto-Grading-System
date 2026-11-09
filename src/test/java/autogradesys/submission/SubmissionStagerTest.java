package autogradesys.submission;

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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import autogradesys.config.AppSettings;
import autogradesys.grading.ErrorHistory;
import autogradesys.grading.ErrorReporter;

class SubmissionStagerTest {
    @TempDir
    Path dir;

    private final ByteArrayOutputStream console = new ByteArrayOutputStream();

    private Path submissionsDir() throws IOException {
        return Files.createDirectories(dir.resolve("submissions"));
    }

    private Path cacheDir() {
        return dir.resolve("cache");
    }

    private ErrorReporter reporter() {
        return new ErrorReporter(new ErrorHistory(dir.resolve("history.jsonl")),
                new PrintStream(console, true, StandardCharsets.UTF_8));
    }

    private SubmissionStager stager() {
        return new SubmissionStager(
                new AppSettings(cacheDir(), AppSettings.defaultSettings().getZipNamePattern()));
    }

    private List<String> childNames(Path folder) throws IOException {
        try (Stream<Path> children = Files.list(folder)) {
            return children.map(path -> path.getFileName().toString()).sorted().collect(Collectors.toList());
        }
    }

    @Test
    void folderWithoutZipsIsUsedAsItIs() throws IOException {
        Files.createDirectories(submissionsDir().resolve("alice/Q1"));

        try (StagedSubmissions staged = stager().stage(submissionsDir(), reporter())) {
            assertEquals(submissionsDir(), staged.root());
            assertFalse(staged.isExtracted());
        }

        assertTrue(Files.isDirectory(submissionsDir().resolve("alice/Q1")));
        assertFalse(Files.exists(cacheDir()));
    }

    @Test
    void extractsEachZipIntoItsOwnFolderKeepingTheLayout() throws IOException {
        TestZips.create(submissionsDir().resolve("2023-2024-chee.teo.2022.zip"), "Q1/Q1a.java");
        TestZips.create(submissionsDir().resolve("2023-2024-ping.lee.2023.zip"),
                "ping.lee.2023/Q1/Q1a.java", "ping.lee.2023/Q2/");
        TestZips.create(submissionsDir().resolve("2023-2024-fen.lai.2022.zip"),
                "RenameToYourStudentID/Q1/Q1a.java");

        try (StagedSubmissions staged = stager().stage(submissionsDir(), reporter())) {
            assertTrue(staged.isExtracted());
            assertEquals(List.of("chee.teo.2022", "fen.lai.2022", "ping.lee.2023"), childNames(staged.root()));
            assertTrue(Files.exists(staged.root().resolve("chee.teo.2022/Q1/Q1a.java")));
            assertTrue(Files.exists(staged.root().resolve("ping.lee.2023/ping.lee.2023/Q1/Q1a.java")));
            assertTrue(Files.exists(staged.root().resolve("fen.lai.2022/RenameToYourStudentID/Q1/Q1a.java")));
        }
    }

    @Test
    void closingDeletesTheExtractedFolderButNotTheZips() throws IOException {
        Path zip = TestZips.create(submissionsDir().resolve("2023-2024-david.2024.zip"), "Q1/Q1a.java");
        Path stagingRoot;

        try (StagedSubmissions staged = stager().stage(submissionsDir(), reporter())) {
            stagingRoot = staged.root();
            assertTrue(Files.exists(stagingRoot));
        }

        assertFalse(Files.exists(stagingRoot));
        assertTrue(Files.exists(zip));
    }

    @Test
    void corruptedZipIsReportedAndTheOtherStudentsAreStillExtracted() throws IOException {
        Files.writeString(submissionsDir().resolve("2023-2024-broken.one.2020.zip"), "not a zip");
        TestZips.create(submissionsDir().resolve("2023-2024-good.one.2021.zip"), "Q1/Q1a.java");
        ErrorReporter reporter = reporter();

        try (StagedSubmissions staged = stager().stage(submissionsDir(), reporter)) {
            assertEquals(List.of("good.one.2021"), childNames(staged.root()));
        }

        assertEquals(1, reporter.records().size());
        assertEquals("broken.one.2020", reporter.records().get(0).student());
        assertTrue(reporter.records().get(0).canContinue());
        assertFalse(reporter.hasFatalError());
    }

    @Test
    void zipWithAnUnexpectedNameIsSkippedAndMentioned() throws IOException {
        TestZips.create(submissionsDir().resolve("weird.zip"), "Q1/Q1a.java");
        TestZips.create(submissionsDir().resolve("2023-2024-ping.lee.2023.zip"), "Q1/Q1a.java");

        try (StagedSubmissions staged = stager().stage(submissionsDir(), reporter())) {
            assertEquals(List.of("ping.lee.2023"), childNames(staged.root()));
        }

        assertTrue(console.toString(StandardCharsets.UTF_8).contains("weird.zip"));
    }

    @Test
    void duplicateUsernameIsReportedAndOnlyTheLastZipIsExtracted() throws IOException {
        TestZips.create(submissionsDir().resolve("2022-2023-ping.lee.2023.zip"), "Q1/old.java");
        TestZips.create(submissionsDir().resolve("2023-2024-ping.lee.2023.zip"), "Q1/new.java");
        ErrorReporter reporter = reporter();

        try (StagedSubmissions staged = stager().stage(submissionsDir(), reporter)) {
            assertEquals(List.of("ping.lee.2023"), childNames(staged.root()));
            assertTrue(Files.exists(staged.root().resolve("ping.lee.2023/Q1/new.java")));
            assertFalse(Files.exists(staged.root().resolve("ping.lee.2023/Q1/old.java")));
        }

        assertEquals(1, reporter.records().size());
        assertEquals("duplicate", reporter.records().get(0).category());
    }

    @Test
    void leftoversFromAnEarlierRunAreNotGradedAsStudents() throws IOException {
        Path stale = Files.createDirectories(
                cacheDir().resolve("staged-submissions").resolve("old.student.2019/Q1"));
        TestZips.create(submissionsDir().resolve("2023-2024-ping.lee.2023.zip"), "Q1/Q1a.java");

        try (StagedSubmissions staged = stager().stage(submissionsDir(), reporter())) {
            assertEquals(List.of("ping.lee.2023"), childNames(staged.root()));
            assertFalse(Files.exists(stale));
        }
    }

    @Test
    void printsOneProgressLinePerStudent() throws IOException {
        TestZips.create(submissionsDir().resolve("2023-2024-a.one.2020.zip"), "Q1/Q1a.java");
        TestZips.create(submissionsDir().resolve("2023-2024-b.two.2021.zip"), "Q1/Q1a.java");

        try (StagedSubmissions staged = stager().stage(submissionsDir(), reporter())) {
            assertTrue(staged.isExtracted());
        }

        String output = console.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("[1/2] a.one.2020"));
        assertTrue(output.contains("[2/2] b.two.2021"));
    }

    @Test
    void mentionsSkippedNonZipEntries() throws IOException {
        TestZips.create(submissionsDir().resolve("2023-2024-ping.lee.2023.zip"), "Q1/Q1a.java");
        Files.writeString(submissionsDir().resolve("notes.txt"), "hello");

        try (StagedSubmissions staged = stager().stage(submissionsDir(), reporter())) {
            assertTrue(staged.isExtracted());
        }

        assertTrue(console.toString(StandardCharsets.UTF_8).contains("notes.txt"));
    }
}
