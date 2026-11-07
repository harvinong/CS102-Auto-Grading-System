package autogradesys.submission;

import java.io.IOException;
import java.nio.file.Path;

/** The folder to grade. If it was made from zips, it is deleted again when closed. */
public final class StagedSubmissions implements AutoCloseable {
    private final Path root;
    private final boolean extracted;

    StagedSubmissions(Path root, boolean extracted) {
        this.root = root;
        this.extracted = extracted;
    }

    public Path root() {
        return root;
    }

    public boolean isExtracted() {
        return extracted;
    }

    @Override
    public void close() {
        if (!extracted) {
            return;
        }
        try {
            FolderDeleter.delete(root);
        } catch (IOException e) {
            System.out.println("Could not delete the temporary folder " + root + ": " + e.getMessage());
        }
    }
}
