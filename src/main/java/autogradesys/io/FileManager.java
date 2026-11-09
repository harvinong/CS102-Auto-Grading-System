package autogradesys.io;

import java.io.File;
import java.util.Arrays;

public class FileManager {

    /**
     * Validate the file object if it is a directory.
     *
     * @param file the file object to be the directory.
     * @throws IllegalArgumentException if the file object is not a directory.
     */
    public static void validateDirectory(File file) throws IllegalArgumentException {
        if (!file.isDirectory()) {
            throw new IllegalArgumentException("Please enter a directory path.");
        }
    }

    /**
     * Validate the file object if it is a file with correct extensions.
     *
     * @param file           the file object to be the file.
     * @param fileExtensions the supported file extensions, strictly lowercase.
     * @throws IllegalArgumentException if the file object is not a file, or has
     *                                  invalid extensions.
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

        if (validExtension) {
            throw new IllegalArgumentException(
                    "Please enter a file with extensions: " + String.join(", ", fileExtensions));
        }

    }

}
