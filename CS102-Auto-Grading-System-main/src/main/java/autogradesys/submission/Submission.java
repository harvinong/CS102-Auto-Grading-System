package autogradesys.submission;

import java.nio.file.Path;

// One student's zip file, with the username taken from its file name
public class Submission {
    private final String username;
    private final Path zipPath;

    public Submission(String username, Path zipPath) {
        this.username = username;
        this.zipPath = zipPath;
    }

    public String getUsername() {
        return username;
    }

    public Path getZipPath() {
        return zipPath;
    }

    @Override
    public String toString() {
        return username;
    }
}
