package autogradesys.input;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

import autogradesys.filemanager.FileValidator;

/**
 *
 * autogradesys.display.CLI (Command Line Interface)
 * provides utilities to facilitate user input and configuration
 * through a command line terminal.
 *
 */
public class CLI {

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
     * @param fileField   The prompt to be printed out to the user.
     * @param defaultFile The default file returned if user enters none.
     * @param sc          The scanner object to read user input.
     * @return The file/directory object. If user enters none, returns the
     *         defaultFile.
     */
    public static File promptFile(Field fileField, File defaultFile, Scanner sc) {
        File file = null;

        while (file == null) {
            try {
                String prompt = String.format("Enter %s%s: ",
                        fileField.getName(), defaultFile != null ? " (" + defaultFile.getName() + ")" : "");

                Path filepath = promptPath(prompt, defaultFile != null ? defaultFile.toPath() : null, sc);
                file = getFile(filepath);

                if (fileField.isDir()) {
                    FileValidator.validateDirectory(file);
                } else {
                    FileValidator.validateFile(file, fileField.getExtensions());
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

    /**
     * Ask the user for a positive whole number no larger than a maximum, such as the grading timeout in seconds.
     *
     * Re-prompts when the line is not a whole number, is zero or negative, or is above the maximum,
     * the same way {@link #promptFile} re-prompts on a bad path.
     *
     * @param label        Name shown in the prompt, e.g. "Grading timeout in seconds".
     * @param defaultValue The current value, kept when the user just presses Enter.
     * @param maxValue     The largest value accepted.
     * @param sc           The scanner object to read user input.
     * @return The number typed (from 1 to maxValue), or defaultValue if the line was blank.
     */
    public static long promptPositiveLong(String label, long defaultValue, long maxValue, Scanner sc) {
        // One message for every kind of bad line, naming the accepted range so the user knows what to type.
        String invalid = "Invalid input: " + label + " must be a whole number from 1 to " + maxValue + ".\n";
        // Loops until a valid value is returned from inside the body.
        while (true) {
            // Show the current value in brackets so Enter visibly means "keep this".
            System.out.print(String.format("Enter %s (%d): ", label, defaultValue));
            // Piped input can end mid-prompt; fail the same way promptPath does instead of spinning forever.
            if (!sc.hasNextLine()) {
                throw new IllegalStateException("Input ended while waiting for " + label);
            }
            // Trim so " 5 " counts as 5.
            String text = sc.nextLine().trim();
            // Blank line: keep the current value.
            if (text.isEmpty()) {
                return defaultValue;
            }
            try {
                // Long.parseLong rejects "1.5", "abc" and values beyond the long range, all as NumberFormatException.
                long value = Long.parseLong(text);
                // Zero and negatives would stop every process at once, and a huge value could hang or crash grading, so only 1 to maxValue is kept.
                if (value > 0 && value <= maxValue) {
                    return value;
                }
                System.out.println(invalid);
            } catch (NumberFormatException e) {
                System.out.println(invalid);
            }
        }
    }

    // Helper Functions

    /**
     * Ask user for the filepath.
     *
     * @param prompt          The prompt that is printed in the terminal.
     * @param defaultFilePath The default file path.
     * @param sc              The reader to reads the user input.
     * @return The path the user has input in absolute form. If user inputs nothing,
     *         it returns [defaultFilePath].
     * @throws IllegalArgumentException If the path is not absolute.
     */
    private static Path promptPath(String prompt, Path defaultFilePath, Scanner sc) throws IllegalArgumentException {
        System.out.print(prompt);
        if (!sc.hasNextLine()) {
            throw new IllegalStateException("Input ended while waiting for " + prompt.trim());
        }
        String pathString = sc.nextLine();

        if (defaultFilePath != null && pathString.trim().length() == 0) {
            return defaultFilePath;
        }

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

}
