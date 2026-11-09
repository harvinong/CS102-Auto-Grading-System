import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Q2a {
    // Counts the lines of the file, which is looked up relative to the working folder.
    public static int countPersons(String fileName) throws IOException {
        return Files.readAllLines(Path.of(fileName)).size();
    }
}
