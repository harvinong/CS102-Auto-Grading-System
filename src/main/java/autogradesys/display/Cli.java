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

                if (!filepath.isAbsolute()) {
                    // the user enters a non-absolute (relative) path.
                    System.out.println("The path must be absolute.");
                    file = null;
                    continue;
                }

                file = Cli.getFile(filepath);

                // Case 1: prompt asks for a directory
                if (prompt.isDir()) {
                    if (!file.isDirectory()) {
                        // the user enters a directory path when prompted for a file path.
                        System.out.println("Invalid input: Please input a directory path.\n");
                        file = null;
                    }
                // Case 2: prompt asks for a file
                } else {
                    if (!file.isFile()) {
                        // the user enters a file path when prompted for a directory path.
                        System.out.println("Invalid input: Please input a file path.\n");
                        file = null;
                    } else if (!validExtension(file, prompt.getExtensions())) {
                        // the user enters a file path with invalid extensions.
                        System.out.println("Please enter a file with extensions: " + String.join(", ", prompt.getExtensions()) + "\n");
                        file = null;
                    }
                }
                
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
     * @return The path the user has input.
     */
    private static Path promptPath(String prompt, Scanner reader) {
        System.out.print(prompt);
        String pathString = reader.nextLine();
        Path filepath = Paths.get(pathString);

        return filepath;
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
            return null; // No file extension found
        } else {
            return fileName.substring(dotIndex + 1);
        }
    }

    /**
     * Check if a file has a valid extension.
     * 
     * @param file file with the extension to be checked.
     * @param extensions a list of valid extensions.
     * @return `true` if `file`'s extension is in `extensions`, false otherwise.
     */
    private static boolean validExtension(File file, String... extensions) {
        return Arrays.asList(extensions).contains(getExtension(file));
    }

}
