package autogradesys.io;

import java.io.File;

public class FileManager {

    /**
     * Validate the file object if it is a directory.
     *
     * @param file the value used by this method.
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
     * @param file the value used by this method.
     * @param fileExtensions the value used by this method.
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
