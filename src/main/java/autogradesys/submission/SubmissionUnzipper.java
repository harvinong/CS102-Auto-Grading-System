package autogradesys.submission;

import java.io.IOException;
import java.nio.file.Path;

import autogradesys.validation.ArchiveExtractor;

// Unzips one student's zip file into a folder, and refuses files that would end up outside it
public final class SubmissionUnzipper {
    /**
     * Prevents instantiation; extraction is done through the static {@link #unzip} method.
     */
    private SubmissionUnzipper() {
    }

    // ArchiveExtractor is the sole extraction implementation, returns how many files were written
    /**
     * Unzips one student's zip into a folder, refusing entries that would land outside it. An existing
     * empty target folder is removed first so that extraction can create it fresh.
     *
     * @param zipFile the student's zip file
     * @param targetFolder where to extract; it may be absent or an empty folder
     * @return how many files were written
     * @throws IOException if the target folder already contains anything, or the zip cannot be read or extracted safely
     */
    public static int unzip(Path zipFile, Path targetFolder) throws IOException {
        if (java.nio.file.Files.isDirectory(targetFolder)) { 
            // safety check for existing folder for the student so it doesn't overwrite, extracts straight if none found.
            try (java.util.stream.Stream<Path> files = java.nio.file.Files.list(targetFolder)) {
                if (files.findAny().isPresent()) throw new IOException("target folder is not empty");
                // stops if folder already has something in it 
            }
            // delete folder as it is empty. ArchiveExtractor creates the folder
            java.nio.file.Files.delete(targetFolder);
        }
        return ArchiveExtractor.extract(zipFile, targetFolder);
    }
}
