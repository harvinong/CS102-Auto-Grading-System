package autogradesys.io;

import java.io.File;

public class FileManager {

    /**
     * Checks that a path the user supplied is a folder.
     *
     * @param file the path to check; it should already exist
     * @throws IllegalArgumentException if {@code file} is not a directory (including when it is a regular file)
     */
    public static void validateDirectory(File file) throws IllegalArgumentException {
        if (!file.isDirectory()) {
            throw new IllegalArgumentException("Please enter a directory path.");
        }
    }

    /**
     * Checks that a path the user supplied is a regular file whose name ends with one of the allowed extensions.
     * The comparison is a case-sensitive {@code endsWith}, so the extensions must be written exactly as they
     * appear in file names.
     *
     * @param file the path to check; it should already exist
     * @param fileExtensions the accepted endings in lowercase, e.g. {@code "csv"}; with none given every file is rejected
     * @throws IllegalArgumentException if {@code file} is not a regular file, or its name ends with none of the extensions
     */
    public static void validateFile(File file, String... fileExtensions) throws IllegalArgumentException {
        if (!file.isFile()) {
            throw new IllegalArgumentException("Please enter a file path.");
        }

        boolean validExtension = false;
        for (String ext : fileExtensions) {
            if (file.getName().endsWith(ext)) {
                validExtension = true;
            }
        }

        if (!validExtension) {
            throw new IllegalArgumentException(
                    "Please enter a file with extensions: " + String.join(", ", fileExtensions));
        }

    }

}
