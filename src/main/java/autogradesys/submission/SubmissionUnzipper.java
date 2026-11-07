package autogradesys.submission;

import java.io.IOException;
import java.nio.file.Path;

import autogradesys.validation.ArchiveExtractor;

/** Unzips one student's zip file into a folder, and refuses files that would end up outside it. */
public final class SubmissionUnzipper {
    private SubmissionUnzipper() {
    }

    /** Compatibility facade. ArchiveExtractor is the sole extraction implementation. */
    public static void unzip(Path zipFile, Path targetFolder) throws IOException {
        if (java.nio.file.Files.isDirectory(targetFolder)) {
            try (java.util.stream.Stream<Path> files = java.nio.file.Files.list(targetFolder)) {
                if (files.findAny().isPresent()) throw new IOException("target folder is not empty");
            }
            java.nio.file.Files.delete(targetFolder);
        }
        ArchiveExtractor.extract(zipFile, targetFolder);
    }
}
