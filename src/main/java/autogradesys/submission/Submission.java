package autogradesys.submission;

import java.nio.file.Path;

// One student's zip file, with the username taken from its file name
public class Submission {
    private final String username;
    private final Path zipPath;

    /**
     * Associates a student username with the archive containing that submission.
     */
    public Submission(String username, Path zipPath) {
        this.username = username;
        this.zipPath = zipPath;
    }

    /**
     * Returns the username associated with this submission.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the archive path associated with this submission.
     */
    public Path getZipPath() {
        return zipPath;
    }

    @Override
    /**
     * Returns a readable representation of the submission or configuration value.
     */
    public String toString() {
        return username;
    }
}
