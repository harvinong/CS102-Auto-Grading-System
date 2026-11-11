package autogradesys;
import java.io.IOException; // The error Java raises when reading/writing a file fails
import java.nio.file.Path; // Represents a file location
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter; // Formats a date/time into a string
import java.util.LinkedHashMap;
import java.util.Map;

import autogradesys.grading.GradeResult; // What JavaGrader.grade hands back: the scores
import autogradesys.io.ScoresheetDAO; // Reads and writes the course scoresheet
import autogradesys.io.ScoresheetRecord; // One student's marks

// Saves the grader's scores into a dated copy of the course scoresheet (e.g. IS442-ScoreSheet.csv).
// ScoresheetDAO does the file reading and writing; ScoreRecorder decides what marks go in and where
// the copy is saved. The original scoresheet is never changed.
public class ScoreRecorder {
    // Pattern for the date and time added to the file name, e.g. 2026-11-11_11-04-00
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    // Input: the GradeResult that JavaGrader.grade returns, and the course scoresheet file. The result holds
    //        scores(): student -> (question -> marks earned), e.g. "ping.lee.2023" -> {"Q1" -> 6, "Q2" -> 10}
    // Function: Loads the scoresheet with ScoresheetDAO, puts each graded student's marks in their record,
    //           and saves everything to a copy next to the original, named with the date and time,
    //           e.g. IS442-ScoreSheet_2026-11-11_11-04-00.csv. The copy has one column per question and
    //           each student's total in "Calculated Final Grade Numerator".
    //           A student in the sheet with no graded submission keeps empty mark cells.
    //           A graded student who is not in the sheet is listed in a warning.
    // Output: Path to the newly created copy
    public static Path exportResults(GradeResult result, Path scoresheet) throws IOException {
        // Read the scoresheet
        ScoresheetDAO dao = new ScoresheetDAO(scoresheet.toFile());
        if (!dao.load()) {
            throw new IOException("Could not read the scoresheet (it needs a Username column): " + scoresheet);
        }

        // Put each graded student's marks into their record
        for (Map.Entry<String, Map<String, Integer>> entry : result.scores().entrySet()) {
            String username = entry.getKey();
            ScoresheetRecord record = new ScoresheetRecord(username, new LinkedHashMap<>(entry.getValue()));
            // set() is false when the student is not in the scoresheet
            if (!dao.set(username, record)) {
                System.out.println("[WARNING] " + username + " was graded but is not in the scoresheet, "
                        + "so their marks are not in the saved copy.");
            }
        }

        // Name the copy after the original plus the date and time, and save it next to the original
        String name = scoresheet.getFileName().toString();
        String baseName = name.toLowerCase().endsWith(".csv") ? name.substring(0, name.length() - 4) : name;
        Path copy = scoresheet.toAbsolutePath().resolveSibling(
                baseName + "_" + LocalDateTime.now().format(TIMESTAMP) + ".csv");
        if (!dao.saveAs(copy.toFile())) {
            throw new IOException("Could not write " + copy);
        }
        return copy;
    }
}
