package autogradesys.filemanager;

import java.io.File;
import java.util.Arrays;

public class FileValidator {
    
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
        
        if (!Arrays.asList(fileExtensions).contains(getExtension(file))) {
            throw new IllegalArgumentException(
                    "Please enter a file with extensions: " + String.join(", ", fileExtensions));
        }

    }

    /**
     * Get the extension of the file.
     * 
     * If the file is a directory, or the file has no extension, it will return an
     * empty string.
     * 
     * @param file the file object.
     * @return the extension without the dot separator. E.g. "pdf", "csv".
     */
    public static String getExtension(File file) {
        // Ref:
        // https://www.geeksforgeeks.org/java/how-to-get-the-file-extension-in-java/
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');

        // if there is no dot or ends with a dot
        if (dotIndex == -1 || dotIndex == fileName.length() - 1) {
            return ""; // No file extension found
        } else {
            return fileName.substring(dotIndex + 1);
        }
    }


}
