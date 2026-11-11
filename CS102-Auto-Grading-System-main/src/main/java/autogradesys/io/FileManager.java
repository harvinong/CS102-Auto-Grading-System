package autogradesys.io;

import java.io.File;

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
     * Checks whether a testers folder holds anything to grade with: a {@code *Tester.java} file
     * directly inside it (the instructors' loose layout), or a sub-folder with a tester or an
     * {@code expected.txt} (one folder per question).
     *
     * @param folder the testers folder
     * @return true if at least one tester or question folder was found
     */
    public static boolean containsTesters(File folder) {
        File[] entries = folder.listFiles();
        if (entries == null) {
            return false;
        }
        for (File entry : entries) {
            if (entry.isFile() && entry.getName().endsWith("Tester.java")) {
                return true;
            }
            if (entry.isDirectory()) {
                File[] inside = entry.listFiles();
                if (inside == null) {
                    continue;
                }
                for (File file : inside) {
                    if (file.isFile() && (file.getName().endsWith("Tester.java") || file.getName().equals("expected.txt"))) {
                        return true;
                    }
                }
            }
        }
        return false;
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

        if (!validExtension) {
            throw new IllegalArgumentException(
                    "Please enter a file with extensions: " + String.join(", ", fileExtensions));
        }

    }

}
