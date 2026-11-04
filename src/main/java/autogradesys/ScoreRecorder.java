package autogradesys;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

// Input: Map<String, Integer> Which is a map of usernames to their final scores
// Function: Takes the map and writes it to a CSV file
// Output: Path to the newly created CSV file
public class ScoreRecorder {
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSSSSSSSS");

    /** Writes scores to a new CSV in the working directory and returns its path. */
    public static Path recordScores(Map<String, Integer> scores) throws IOException {
        Path file = Path.of(LocalDateTime.now().format(TIMESTAMP) + ".csv");
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            writer.write("username,final_score\r\n");
            for (Map.Entry<String, Integer> entry : scores.entrySet()) {
                String username = entry.getKey().replace("\"", "\"\"");
                writer.write("\"" + username + "\"," + entry.getValue() + "\r\n");
            }
        }
        return file;
    }
}
