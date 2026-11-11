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
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks that ScoresheetDAO reads and writes the course scoresheet (IS442-ScoreSheet.csv layout). */
class ScoresheetDAOTest {

    // A blank course scoresheet, as downloaded.
    private static final String BLANK =
            "OrgDefinedId,Username,Last Name,First Name,Email,Calculated Final Grade Numerator,Calculated Final Grade Denominator,End-of-Line Indicator\n"
            + "#01400001,#ping.lee.2023,_,PING LEE,ping.lee.2023@computing.smu.edu.sg,,20,#\n"
            + "#01400002,#chee.teo.2022,_,TEO CHEE,chee.teo.2022@computing.smu.edu.sg,,20,#\n";

    // The same scoresheet after marks were saved: question columns plus the total.
    private static final String MARKED =
            "OrgDefinedId,Username,Last Name,First Name,Email,Calculated Final Grade Numerator,Calculated Final Grade Denominator,Q1,Q2,End-of-Line Indicator\n"
            + "#01400001,#ping.lee.2023,_,PING LEE,ping.lee.2023@computing.smu.edu.sg,16,20,6,10,#\n"
            + "#01400002,#chee.teo.2022,_,TEO CHEE,chee.teo.2022@computing.smu.edu.sg,,20,,,#\n";

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

    // Input: None
    // Function: Checks that load() finds every student (without the "#") and that a blank sheet has no marks
    // Output: Passes if the usernames and records are right
    @Test
    void loadReadsTheCourseScoresheet() throws IOException {
        ScoresheetDAO dao = new ScoresheetDAO(sheet(BLANK));

        assertTrue(dao.load());
        assertEquals(List.of("ping.lee.2023", "chee.teo.2022"), dao.getUsernames());
        assertTrue(dao.get("ping.lee.2023").marks().isEmpty());
        // A leading "#" in the lookup is ignored too.
        assertEquals("chee.teo.2022", dao.get("#chee.teo.2022").username());
        assertNull(dao.get("nobody"));
    }

    // Input: None
    // Function: Checks that set() + saveAs() add the question columns before End-of-Line Indicator,
    //           fill the total, keep every other cell, and leave the original file unchanged
    // Output: Passes if the new file matches MARKED exactly
    @Test
    void saveAsWritesTheMarksIntoANewFile() throws IOException {
        File original = sheet(BLANK);
        ScoresheetDAO dao = new ScoresheetDAO(original);
        assertTrue(dao.load());

        LinkedHashMap<String, Integer> marks = new LinkedHashMap<>();
        marks.put("Q2", 10);
        marks.put("Q1", 6);
        assertTrue(dao.set("ping.lee.2023", new ScoresheetRecord("ping.lee.2023", marks)));

        File copy = root.resolve("copy.csv").toFile();
        assertTrue(dao.saveAs(copy));
        assertEquals(MARKED, Files.readString(copy.toPath()));
        assertEquals(BLANK, Files.readString(original.toPath()));
        // saveAs never overwrites an existing file.
        assertFalse(dao.saveAs(copy));
    }

    // Input: None
    // Function: Checks that a sheet with marks loads them back, and saving it unchanged gives the same file
    // Output: Passes if the marks are read and the file is unchanged after save()
    @Test
    void markedSheetLoadsAndSavesUnchanged() throws IOException {
        File file = sheet(MARKED);
        ScoresheetDAO dao = new ScoresheetDAO(file);
        assertTrue(dao.load());

        ScoresheetRecord ping = dao.get("ping.lee.2023");
        assertEquals(6, ping.marks().get("Q1"));
        assertEquals(10, ping.marks().get("Q2"));
        assertEquals(16, ping.finalScore());
        assertTrue(dao.get("chee.teo.2022").marks().isEmpty());

        assertTrue(dao.save());
        assertEquals(MARKED, Files.readString(file.toPath()));
    }

    // Input: None
    // Function: Checks that set() refuses a student who is not in the sheet (no create)
    // Output: Passes if set() returns false
    @Test
    void setOnlyUpdatesExistingStudents() throws IOException {
        ScoresheetDAO dao = new ScoresheetDAO(sheet(BLANK));
        assertTrue(dao.load());
        assertFalse(dao.set("nobody", new ScoresheetRecord("nobody", new LinkedHashMap<>())));
        assertFalse(dao.set("ping.lee.2023", null));
    }

    // Input: None
    // Function: Checks that broken scoresheets are refused and that save() needs a successful load()
    // Output: Passes if load() and save() return false
    @Test
    void brokenSheetsAreRefused() throws IOException {
        assertFalse(new ScoresheetDAO(root.resolve("missing.csv").toFile()).load());
        assertFalse(new ScoresheetDAO(sheet("")).load());
        // No Username column
        assertFalse(new ScoresheetDAO(sheet("name,total\nalice,1\n")).load());
        // Repeated column
        assertFalse(new ScoresheetDAO(sheet("Username,Username\nalice,alice\n")).load());
        // Row longer than the header
        assertFalse(new ScoresheetDAO(sheet("Username,Email\nalice,a@x,extra\n")).load());
        // Unclosed quote
        assertFalse(new ScoresheetDAO(sheet("Username,Email\n\"alice,a@x\n")).load());
        // Same student twice
        assertFalse(new ScoresheetDAO(sheet("Username,Email\n#alice,a\n#alice,b\n")).load());
        // A mark that is not a whole number
        assertFalse(new ScoresheetDAO(sheet(
                "Username,Calculated Final Grade Denominator,Q1,End-of-Line Indicator\n#alice,20,abc,#\n")).load());
        // save() before load() does nothing
        assertFalse(new ScoresheetDAO(sheet(BLANK)).save());
    }
}
