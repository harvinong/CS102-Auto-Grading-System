package autogradesys.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import autogradesys.submission.TestZips;

class SubmissionCacheTest {
    @TempDir
    Path dir;

    @Test
    void namedArchiveIsExtractedOnceUnderItsUsernameWithTheRealFileCount() throws IOException {
        Path submissions = Files.createDirectories(dir.resolve("submissions"));
        TestZips.create(submissions.resolve("2023-2024-gina.zip"), "Q1/Q1a.java", "Q1/Q1b.java", "Q2/");
        TestZips.create(submissions.resolve("henry.zip"), "Q1/Q1a.java");

        CacheReport report = new SubmissionCache(dir.resolve("cache")).prepare(submissions);

        // One working folder per student: the named zip is not also extracted as "2023-2024-gina".
        assertTrue(Files.isDirectory(report.workingDir().resolve("gina")));
        assertFalse(Files.exists(report.workingDir().resolve("2023-2024-gina")));
        assertTrue(Files.isDirectory(report.workingDir().resolve("henry")));
        assertEquals(2, report.created());
        // Each zip has one event, with the number of files it really held.
        List<String> messages = report.events().stream()
                .map(event -> event.path() + ": " + event.message()).toList();
        assertEquals(List.of("2023-2024-gina.zip: extracted 2 files", "henry.zip: extracted 1 file"), messages);
    }
}
