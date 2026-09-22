package autogradesys.display;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

import java.util.Arrays;

/**
 * 
 * autogradesys.display.Cli (Command Line Interface)
 * provides utilities to facilitate user input and configuration
 * through a command line terminal.
 * 
 */
public class Cli {

    /**
     * Ask user for the path of the file/directory and returns the file object.
     * 
     * When this method is called, the terminal will show a prompt, then the user
     * inputs the
     * absolute path of the file/directory.
     * 
     * The method will ask the user to re-enter the path if:
     * - the user enters a non-absolute (relative) path;
     * - the file/directory does not exist; and
     * - the prompt ask for directory and user inputs a file, and vice versa.
     * - the file extension is invalid.
     * 
     * @param prompt The prompt to be printed out to the user.
     * @param reader The scanner object to read user input.
     * @return The file/directory object.
     */
    public static File promptFile(Prompt prompt, Scanner reader) {
        File file = null;

        while (file == null) {
            try {

                Path filepath = Cli.promptPath(prompt.getField(), reader);
                file = Cli.getFile(filepath);

                if (prompt.isDir()) {
                    validateDirectory(file);
                } else {
                    validateFile(file, prompt.getExtensions());
                }

            } catch (IllegalArgumentException e) {
                // the user enters a non-absolute (relative) path.
                // the user enters a directory path when prompted for a file path.
                // the user enters a file path when prompted for a directory path.
                // the user enters a file path with invalid extensions.
                System.out.println("Invalid input: " + e.getMessage() + "\n");
                file = null;
            } catch (FileNotFoundException e) {
                // the file/directory does not exist.
                System.out.println("File not found: " + e.getMessage() + "\n");
                file = null;
            }
        }

        return file;
    }

    // Helper Functions

    /**
     * Ask user for the filepath.
     * 
     * @param prompt The prompt that is printed in the terminal.
     * @param reader The reader to reads the user input.
     * @return The path the user has input in absolute form.
     * @throws IllegalArgumentException If the path is not absolute.
     */
    private static Path promptPath(String prompt, Scanner reader) throws IllegalArgumentException {
        System.out.print(prompt);
        String pathString = reader.nextLine();
        Path dirpath = Paths.get(pathString);

        if (!dirpath.isAbsolute()) {
            throw new IllegalArgumentException("The path must be absolute.");
        }

        return dirpath;
    }

    /**
     * Get the file/directory object from the given filepath.
     * 
     * @param path The path of the file/directory.
     * @return The file/directory object that exist in the file system.
     * @throws FileNotFoundException If the file/directory does not exist.
     */
    private static File getFile(Path path) throws FileNotFoundException {
        File file = path.toFile();

        if (!file.exists()) {
            throw new FileNotFoundException(path.toString());
        }

        return file;
    }

    /**
     * Validate the file object if it is a directory.
     * 
     * @param file the file object to be the directory.
     * @throws IllegalArgumentException if the file object is not a directory.
     */
    private static void validateDirectory(File file) throws IllegalArgumentException {
        if (!file.isDirectory()) {
            throw new IllegalArgumentException("Please enter a directory path.");
        }
    }

    /**
     * Validate the file object if it is a file with correct extensions.
     * 
     * @param file           the file object to be the file.
     * @param fileExtensions the supported file extensions.
     * @throws IllegalArgumentException if the file object is not a file, or has
     *                                  invalid extensions.
     */
    private static void validateFile(File file, String... fileExtensions) throws IllegalArgumentException {
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
    private static String getExtension(File file) {
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
