package autogradesys;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;

class ScoreRecorderTest {
    public static void main(String[] args) {

        // Create a sample map of usernames to final scores
        HashMap<String, Integer> scores = new HashMap<>();
        scores.put("alice.america.2025", 85);
        scores.put("bob.america.2025", 92);
        scores.put("charlie.america.2025", 78);

        // Call the recordScores method and print the path of the created CSV file
        try {
            Path file = ScoreRecorder.recordScores(scores);
            System.out.println("Scores saved to: " + file.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Could not save scores: " + e.getMessage());
        }
    }
}
