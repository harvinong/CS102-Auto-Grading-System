package autogradesys.submission;

import java.nio.file.Path;

// One student's zip file, with the username taken from its file name
public class Submission {
    private final String username;
    private final Path zipPath;

    /**
     * Associates a student username with the archive containing that submission.
     * @param username the value used by this method.
     * @param zipPath the value used by this method.
     */
    public Submission(String username, Path zipPath) {
        this.username = username;
        this.zipPath = zipPath;
    }

    /**
     * Returns the username associated with this submission.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the archive path associated with this submission.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    public Path getZipPath() {
        return zipPath;
    }

    @Override
    /**
     * Returns a readable representation of the submission or configuration value.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    public String toString() {
        return username;
    }
}
