package autogradesys.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import autogradesys.io.ScoresheetReader;

class ScoresheetReaderTest {
    @TempDir
    Path dir;

    private List<Student> read(String csv) throws IOException {
        Path file = Files.writeString(dir.resolve("scores.csv"), csv);
        return ScoresheetReader.read(file.toFile());
    }

    @Test
    void gradingScoresheetWithAStudentColumnIsRead() throws IOException {
        // The format grading writes marks into, as in the README and demo/preflight/scores.csv.
        assertEquals(List.of(new Student("alice", "", ""), new Student("bob", "", "")),
                read("student,Q1,Q2,Q3\nalice,,,\nbob,2,1,0\n"));
    }

    @Test
    void titleBasedExportUsesItsUsernameNameAndEmailColumns() throws IOException {
        assertEquals(List.of(new Student("ping.lee.2023", "PING LEE", "ping@example.edu")),
                read("Username,Last Name,First Name,Email\n#ping.lee.2023,LEE,PING,ping@example.edu\n"));
    }

    @Test
    void scoresheetWithoutAnyUsernameColumnNamesBothAcceptedTitles() {
        IOException error = assertThrows(IOException.class, () -> read("name,Q1\nalice,\n"));
        assertTrue(error.getMessage().contains("'Username' or 'student'"), error.getMessage());
    }
}
