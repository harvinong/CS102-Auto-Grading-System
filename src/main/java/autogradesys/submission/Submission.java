package autogradesys.submission;

import java.nio.file.Path;

// One student's zip file, with the username taken from its file name
public class Submission {
    private final String username;
    private final Path zipPath;

    /**
     * Creates a submission instance.
     */
    public Submission(String username, Path zipPath) {
        this.username = username;
        this.zipPath = zipPath;
    }

    /**
     * Returns username.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns zip path.
     */
    public Path getZipPath() {
        return zipPath;
    }

    @Override
    /**
     * To string.
     */
    public String toString() {
        return username;
    }
}
