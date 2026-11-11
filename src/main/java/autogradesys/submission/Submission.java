package autogradesys.submission;

import java.nio.file.Path;

// AI use: the docstrings (/** ... */ comments) in this file were generated with Claude AI.
/**
 * One student's zip file, with the username taken from its file name.
 * For example, 2023-2024-ping.lee.2023.zip belongs to the student ping.lee.2023.
 */
public class Submission {
    private final String username;
    private final Path zipPath;

    /**
     * Creates a record of one student's submission.
     *
     * @param username the student's username, taken from the zip file name (e.g. ping.lee.2023)
     * @param zipPath  where the student's zip file is
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
     * @return the path to the zip file
     */
    public Path getZipPath() {
        return zipPath;
    }

    /**
     * Describes the submission by its owner, so it reads clearly when printed in lists and messages.
     *
     * @return the student's username
     */
    @Override
    public String toString() {
        return username;
    }
}
