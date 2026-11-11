package autogradesys.io;
 
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
 
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
 
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
 
/** Checks that ScoresheetDAO reads and writes ScoreRecorder's CSV format. */
class ScoresheetDAOTest {
 
    // The CSV ScoreRecorder writes for its sample students (see ScoreRecorderTest).
    private static final String RECORDER_CSV =
            "username,\"Q1a\",\"Q1b\",final_score\r\n"
            + "\"chee.teo.2022\",3,2,5\r\n"
            + "\"jing.lim.2021\",1,0,1\r\n"
            + "\"david.2024\",0,0,0\r\n"
            + "\"% full marks\",33.3%,0.0%,\r\n";
 
    // Fresh temp folder per test, so no real scoresheet is touched.
    @TempDir
    Path root;
 
    // Input: the CSV text to put in the file.
    // Function: writes a scoresheet.csv into the temp folder.
    // Output: the file.
    private File sheet(String text) throws IOException {
        Path file = root.resolve("scoresheet.csv");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file.toFile();
    }
    // test that ScoresheetDAO can read the CSV ScoreRecorder writes, and that it can write it back out unchanged.
    @Test
    void loadReadsScoreRecorderFormat() throws IOException {
        ScoresheetDAO dao = new ScoresheetDAO(sheet(RECORDER_CSV));
 
        assertTrue(dao.load());
        // The "% full marks" row is not a student.
        assertEquals(List.of("chee.teo.2022", "jing.lim.2021", "david.2024"), dao.getUsernames());
 
        ScoresheetRecord chee = dao.get("chee.teo.2022");
        assertEquals("chee.teo.2022", chee.username());
        assertEquals(3, chee.marks().get("Q1a"));
        assertEquals(2, chee.marks().get("Q1b"));
        assertEquals(5, chee.finalScore());
        assertNull(dao.get("nobody"));
    }
    // test that ScoresheetDAO can write the CSV ScoreRecorder writes, and that it can read it back unchanged.
    @Test
    void saveWithoutChangesWritesTheSameFile() throws IOException {
        File file = sheet(RECORDER_CSV);
        ScoresheetDAO dao = new ScoresheetDAO(file);
        assertTrue(dao.load());
        assertTrue(dao.save());
        assertEquals(RECORDER_CSV, Files.readString(file.toPath(), StandardCharsets.UTF_8));
    }
 
    @Test
    void setThenSaveUpdatesMarksAndFinalScore() throws IOException {
        File file = sheet(RECORDER_CSV);
        ScoresheetDAO dao = new ScoresheetDAO(file);
        assertTrue(dao.load());
 
        // jing's Q1b now gets 2 marks, and Q2a is a new sub-question.
        ScoresheetRecord jing = dao.get("jing.lim.2021");
        jing.marks().put("Q1b", 2);
        jing.marks().put("Q2a", 4);
        assertTrue(dao.set("jing.lim.2021", jing));
        // A student who is not in the scoresheet cannot be set (no create).
        assertFalse(dao.set("carol", jing));
        assertTrue(dao.save());
 
        assertEquals("username,\"Q1a\",\"Q1b\",\"Q2a\",final_score\r\n"
                + "\"chee.teo.2022\",3,2,0,5\r\n"
                + "\"jing.lim.2021\",1,2,4,7\r\n"
                + "\"david.2024\",0,0,0,0\r\n"
                + "\"% full marks\",33.3%,0.0%,,\r\n",
                Files.readString(file.toPath(), StandardCharsets.UTF_8));
 
        // A second DAO reading the saved file sees the same marks.
        ScoresheetDAO again = new ScoresheetDAO(file);
        assertTrue(again.load());
        assertEquals(4, again.get("jing.lim.2021").marks().get("Q2a"));
    }

    @Test
    void loadRejectsBrokenFiles() throws IOException {
        assertFalse(new ScoresheetDAO(root.resolve("missing.csv").toFile()).load());
        assertFalse(new ScoresheetDAO(sheet("")).load());
        assertFalse(new ScoresheetDAO(sheet("name,\"Q1a\",final_score\r\n\"a\",1,1\r\n")).load());
        assertFalse(new ScoresheetDAO(sheet("username,\"Q1a\",\"Q1a\",final_score\r\n\"a\",1,1,2\r\n")).load());
        assertFalse(new ScoresheetDAO(sheet("username,\"Q1a\",final_score\r\n\"a\",1,1,extra\r\n")).load());
        assertFalse(new ScoresheetDAO(sheet("username,\"Q1a\",final_score\r\n\"a,1,1\r\n")).load());
        assertFalse(new ScoresheetDAO(sheet("username,\"Q1a\",final_score\r\n\"a\",1,1\r\n\"a\",0,0\r\n")).load());
        assertFalse(new ScoresheetDAO(sheet("username,\"Q1a\",final_score\r\n\"a\",abc,1\r\n")).load());
    }
 
    // If the file is missing, save() does nothing and returns false.
    @Test
    void saveBeforeLoadDoesNothing() throws IOException {
        File file = sheet(RECORDER_CSV);
        assertFalse(new ScoresheetDAO(file).save());
        assertEquals(RECORDER_CSV, Files.readString(file.toPath(), StandardCharsets.UTF_8));
    }
}
 