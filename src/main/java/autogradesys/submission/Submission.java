package autogradesys.submission;

import java.nio.file.Path;

// One student's zip file, with the username taken from its file name
public class Submission {
    private final String username;
    private final Path zipPath;

    /**
     * Creates a record of one student's submission.
     *
     * @param username the student's username, taken from the zip file name
     * @param zipPath where the student's zip file is
     */
    public Submission(String username, Path zipPath) {
        this.username = username;
        this.zipPath = zipPath;
    }

    /**
     * Gives the student who made this submission.
     *
     * @return the username as it appeared in the zip file name
     */
    public String getUsername() {
        return username;
    }

    /**
     * Gives the location of the student's zip file.
     *
     * @return the path to the zip
     */
    public Path getZipPath() {
        return zipPath;
    }

    @Override
    /**
     * Describes the submission by its owner, which keeps it readable in lists and messages.
     *
     * @return the student's username
     */
    public String toString() {
        return username;
    }
}
