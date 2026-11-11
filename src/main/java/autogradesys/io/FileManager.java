package autogradesys.io;

import java.io.File;

public class FileManager {

    /**
     * Validate the file object if it is a directory.
     *
     * @param file the file being validated or processed.
     * @throws IllegalArgumentException when the operation cannot complete.
     */
    public static void validateDirectory(File file) throws IllegalArgumentException {
        if (!file.isDirectory()) {
            throw new IllegalArgumentException("Please enter a directory path.");
        }
    }

    /**
     * Validate the file object if it is a file with correct extensions.
     *
     *                                  invalid extensions.
     * @param file the file being validated or processed.
     * @param fileExtensions the argument supplied to this operation.
     * @throws IllegalArgumentException when the operation cannot complete.
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
