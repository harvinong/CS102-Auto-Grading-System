package autogradesys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException; // The error raised if the CSV file cannot be saved
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path; // Represents the location of the saved CSV file
import java.util.LinkedHashMap; // A map that remembers the order items were added
import java.util.Map; // Key -> value lookup table

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import autogradesys.grading.ErrorHistory;
import autogradesys.grading.ErrorReporter;
import autogradesys.grading.GradeResult;

/** Checks the two ScoreRecorder entry points the app uses: updating the scoresheet and exporting results. */
class ScoreRecorderTest {
    // Fresh temp folder per test, so no real scoresheet is touched.
    @TempDir
    Path dir;

    // Input: None (a small scoresheet and scores are built inside the test)
    // Function: Checks that the method JavaGrader calls writes each mark into the right row and column
    // Output: Passes if the CSV matches and no errors were reported
    @Test
    void liveGradingEntryPointWritesMarksIntoTheScoresheet() throws IOException {
        // A scoresheet in the documented format: a student column and one column per question.
        Path scoresheet = Files.writeString(dir.resolve("scores.csv"), "student,Q1,Q2\nalice,,\nbob,,\n");
        ErrorReporter reporter = new ErrorReporter(new ErrorHistory(dir.resolve("history.jsonl")),
                new PrintStream(new ByteArrayOutputStream()));
        // Marks per student and question, as JavaGrader collects them.
        Map<String, Map<String, Integer>> scores = new LinkedHashMap<>();
        scores.put("alice", Map.of("Q1", 2, "Q2", 1));
        scores.put("bob", Map.of("Q1", 0, "Q2", 2));

        assertTrue(ScoreRecorder.recordScores(scoresheet, scores, reporter));

        // Each mark lands in its student's row and question's column, and nothing went wrong.
        assertEquals("student,Q1,Q2\nalice,2,1\nbob,0,2\n", Files.readString(scoresheet));
        assertTrue(reporter.records().isEmpty(), reporter.records().toString());
    }

    // Input: None (a GradeResult with 5 students and 5 sub-questions is built inside the test)
    // Function: Checks that exportResults writes username, the sub-question columns (sorted),
    //           final_score and the % full marks row, including a student with a missing sub-question
    // Output: Passes if the exported CSV matches exactly
    @Test
    void exportResultsWritesTheResultsCsv() throws IOException {
        // How many marks each sub-question is worth (one mark per tester).
        Map<String, Integer> worth = new LinkedHashMap<>();
        worth.put("Q1a", 3);
        worth.put("Q1b", 2);
        worth.put("Q2a", 4);
        worth.put("Q2b", 1);
        worth.put("Q3", 5);

        // Marks each student earned: 
        Map<String, Map<String, Integer>> scores = new LinkedHashMap<>();
        scores.put("chee.teo.2022", marks(3, 2, 4, 1, 5));
        scores.put("jing.lim.2021", marks(1, 0, 4, 1, 2));
        Map<String, Integer> david = marks(3, 2, 0, 0, 0);
        david.remove("Q3");
        scores.put("david.2024", david);
        scores.put("ann.tan.2023", marks(3, 2, 2, 0, 4));
        scores.put("ben.ng.2023", marks(0, 0, 0, 0, 0));

        GradeResult result = new GradeResult(scores.size(), 0, false, scores, worth);

        Path file = ScoreRecorder.exportResults(result);
        try {
            // Full marks per sub-question, out of 5 students:
            // Q1a: chee, david, ann = 3 -> 60.0%   Q1b: chee, david, ann = 3 -> 60.0%
            // Q2a: chee, jing       = 2 -> 40.0%   Q2b: chee, jing       = 2 -> 40.0%
            // Q3:  chee             = 1 -> 20.0%
            assertEquals("username,\"Q1a\",\"Q1b\",\"Q2a\",\"Q2b\",\"Q3\",final_score\r\n"
                    + "\"chee.teo.2022\",3,2,4,1,5,15\r\n"
                    + "\"jing.lim.2021\",1,0,4,1,2,8\r\n"
                    + "\"david.2024\",3,2,0,0,0,5\r\n"
                    + "\"ann.tan.2023\",3,2,2,0,4,11\r\n"
                    + "\"ben.ng.2023\",0,0,0,0,0,0\r\n"
                    + "\"% full marks\",60.0%,60.0%,40.0%,40.0%,20.0%,\r\n", Files.readString(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    // Input: one student's marks for Q1a, Q1b, Q2a, Q2b and Q3
    // Function: builds that student's marks map. Q3 is put in first on purpose, so the test
    //           also checks that the export sorts the columns into Q1a, Q1b, Q2a, Q2b, Q3.
    // Output: the map, e.g. {"Q3" -> 5, "Q1a" -> 3, "Q1b" -> 2, "Q2a" -> 4, "Q2b" -> 1}
    private static Map<String, Integer> marks(int q1a, int q1b, int q2a, int q2b, int q3) {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("Q3", q3);
        result.put("Q1a", q1a);
        result.put("Q1b", q1b);
        result.put("Q2a", q2a);
        result.put("Q2b", q2b);
        return result;
    }
}
