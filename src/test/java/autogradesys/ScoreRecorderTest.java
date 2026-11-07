package autogradesys;
import java.io.IOException; // The error raised if the CSV file cannot be saved
import java.nio.file.Path; // Represents the location of the saved CSV file
import java.util.LinkedHashMap; // A map that remembers the order items were added
import java.util.Map; // Key -> value lookup table

class ScoreRecorderTest {
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
