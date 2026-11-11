package autogradesys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException; // The error raised if the CSV file cannot be read or saved
import java.nio.file.Files;
import java.nio.file.Path; // Represents the location of a CSV file
import java.util.LinkedHashMap; // A map that remembers the order items were added
import java.util.Map; // Key -> value lookup table

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import autogradesys.grading.GradeResult;

/** Checks that ScoreRecorder.exportResults writes a dated copy of the course scoresheet with the marks. */
class ScoreRecorderTest {
    // A small scoresheet in the same layout as IS442-ScoreSheet.csv
    private static final String SHEET =
            "OrgDefinedId,Username,Last Name,First Name,Email,Calculated Final Grade Numerator,Calculated Final Grade Denominator,End-of-Line Indicator\n"
            + "#01400001,#ping.lee.2023,_,PING LEE,ping.lee.2023@computing.smu.edu.sg,,20,#\n"
            + "#01400002,#chee.teo.2022,_,TEO CHEE,chee.teo.2022@computing.smu.edu.sg,,20,#\n"
            + "#01400003,#david.2024,_,DAVID KONG,david.2024@computing.smu.edu.sg,,20,#\n";

    // Fresh temp folder per test, so no real scoresheet is touched.
    @TempDir
    Path dir;

    // Input: None (a scoresheet and a GradeResult are built inside the test)
    // Function: Checks that the copy keeps every original cell, adds Q1/Q2/Q3 before End-of-Line Indicator,
    //           fills the total, leaves an ungraded student empty, and leaves the original file unchanged
    // Output: Passes if the copy matches exactly
    @Test
    void exportResultsWritesADatedCopyOfTheScoresheet() throws IOException {
        Path scoresheet = Files.writeString(dir.resolve("IS442-ScoreSheet.csv"), SHEET);

        // Marks each student earned. Q3 is put in first on purpose, to check the columns are sorted.
        Map<String, Map<String, Integer>> scores = new LinkedHashMap<>();
        scores.put("ping.lee.2023", marks(4, 6, 10));
        scores.put("chee.teo.2022", marks(0, 6, 6));
        // david.2024 was not graded, so his mark cells stay empty.

        GradeResult result = new GradeResult(scores.size(), 0, false, scores);
        Path copy = ScoreRecorder.exportResults(result);

        // The copy sits next to the original and is named with the date and time.
        assertEquals(dir.toAbsolutePath(), copy.getParent());
        assertTrue(copy.getFileName().toString()
                .matches("IS442-ScoreSheet_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}\\.csv"), copy.toString());
        assertNotEquals(scoresheet.toAbsolutePath(), copy);

        assertEquals("OrgDefinedId,Username,Last Name,First Name,Email,Calculated Final Grade Numerator,Calculated Final Grade Denominator,Q1,Q2,Q3,End-of-Line Indicator\n"
                + "#01400001,#ping.lee.2023,_,PING LEE,ping.lee.2023@computing.smu.edu.sg,20,20,6,10,4,#\n"
                + "#01400002,#chee.teo.2022,_,TEO CHEE,chee.teo.2022@computing.smu.edu.sg,12,20,6,6,0,#\n"
                + "#01400003,#david.2024,_,DAVID KONG,david.2024@computing.smu.edu.sg,,20,,,,#\n",
                Files.readString(copy));
        // The original scoresheet is not changed.
        assertEquals(SHEET, Files.readString(scoresheet));
    }

    // Input: None
    // Function: Checks that a scoresheet without a Username column is refused with a clear error
    // Output: Passes if an IOException is thrown
    @Test
    void scoresheetWithoutUsernameColumnIsRefused() throws IOException {
        Path scoresheet = Files.writeString(dir.resolve("bad.csv"), "name,total\nalice,\n");
        GradeResult result = new GradeResult(1, 0, false, Map.of("alice", Map.of("Q1", 1)));
        assertThrows(IOException.class, () -> ScoreRecorder.exportResults(result));
    }

    // Input: one student's marks for Q3, Q1 and Q2
    // Function: builds that student's marks map, with Q3 first
    // Output: the map, e.g. {"Q3" -> 4, "Q1" -> 6, "Q2" -> 10}
    private static Map<String, Integer> marks(int q3, int q1, int q2) {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("Q3", q3);
        result.put("Q1", q1);
        result.put("Q2", q2);
        return result;
    }
}
