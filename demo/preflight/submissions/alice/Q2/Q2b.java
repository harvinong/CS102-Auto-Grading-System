import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Q2b {
    // Returns the name on the line with the highest age.
    public static String oldest(String fileName) throws IOException {
        String bestName = "";
        int bestAge = -1;
        for (String line : Files.readAllLines(Path.of(fileName))) {
            String[] parts = line.split(",");
            int age = Integer.parseInt(parts[1].trim());
            if (age > bestAge) {
                bestAge = age;
                bestName = parts[0].trim();
            }
        }
        return bestName;
    }
}
