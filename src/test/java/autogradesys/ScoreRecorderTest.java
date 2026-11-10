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

/** Checks the ScoreRecorder entry point that live grading (JavaGrader) uses to save marks. */
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

    // Input: None (sample results are built inside the method)
    // Function: Builds sample results in the grader's format and writes them to a CSV with ScoreRecorder
    // Output: Prints the path of the created CSV file, or an error message if it could not be saved
    public static void main(String[] args) {

        // Sample results in the same shape as PerStudentListener's scores map: {passed, total}
        Map<String, int[]> results = new LinkedHashMap<>(); // Empty scoreboard
        results.put("chee.teo.2022 > Q1 > Q1a", new int[]{3, 3}); // Q1a: passed 3 of 3 tests
        results.put("chee.teo.2022 > Q1 > Q1b", new int[]{2, 3}); // Q1b: passed 2 of 3 tests
        results.put("chee.teo.2022 > Q1", new int[]{5, 6}); // All of Q1 (ignored by ScoreRecorder)
        results.put("chee.teo.2022", new int[]{5, 6}); // Whole student (only used to make sure they get a row)

        // Q1b did not compile: the grader records it as one failed test
        results.put("jing.lim.2021 > Q1 > Q1a", new int[]{1, 3}); // Q1a: passed 1 of 3 tests
        results.put("jing.lim.2021 > Q1 > Q1b", new int[]{0, 1}); // Q1b: compile failure, 0 marks
        results.put("jing.lim.2021 > Q1", new int[]{1, 4}); // All of Q1
        results.put("jing.lim.2021", new int[]{1, 4}); // Whole student

        // Only a student-level failure, e.g. "Setup failed": still gets a row of zeros
        results.put("david.2024", new int[]{0, 1});

        // Call the recordScores method and print the path of the created CSV file
        try {
            Path file = ScoreRecorder.recordScores(results); // Write the CSV and get its location back
            System.out.println("Scores saved to: " + file.toAbsolutePath()); // Show the full path
        } catch (IOException e) { // Saving failed, e.g. folder is read-only or the disk is full
            System.err.println("Could not save scores: " + e.getMessage()); // Explain what went wrong
        }
    }
}