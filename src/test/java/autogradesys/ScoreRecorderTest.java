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

    // Input: None (a GradeResult is built inside the test)
    // Function: Checks that exportResults writes username, questions, final_score and the % full marks row
    // Output: Passes if the exported CSV matches
    @Test
    void exportResultsWritesTheResultsCsv() throws IOException {
        // alice gets full marks on both questions, bob only on Q2. Q1 is worth 3 marks, Q2 is worth 1.
        Map<String, Map<String, Integer>> scores = new LinkedHashMap<>();
        scores.put("alice", Map.of("Q1", 3, "Q2", 1));
        scores.put("bob", Map.of("Q1", 1, "Q2", 1));
        GradeResult result = new GradeResult(2, 0, false, scores, Map.of("Q1", 3, "Q2", 1));

        Path file = ScoreRecorder.exportResults(result);
        try {
            assertEquals("username,\"Q1\",\"Q2\",final_score\r\n"
                    + "\"alice\",3,1,4\r\n"
                    + "\"bob\",1,1,2\r\n"
                    + "\"% full marks\",50.0%,100.0%,\r\n", Files.readString(file));
        } finally {
            // The export lands in the current folder, so remove it after checking.
            Files.deleteIfExists(file);
        }
    }
}
