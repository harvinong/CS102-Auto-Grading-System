package autogradesys.cli;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.regex.Pattern;

import autogradesys.io.FileManager;

/**
 *
 * autogradesys.cli.Input
 * provides utilities to facilitate user input and configuration
 * through a command line terminal.
 *
 */
public class Input {

    /**
     * Prevents instantiation; every prompt is a static method.
     */
    private Input() {} // disable instancing

    /**
     * <p>
     * Asks the user for the absolute path of a file or folder and returns it as a {@link File}.
     * </p><p>
     * The prompt is repeated, with an error line, until the answer is acceptable. It is rejected if:
     * </p>
     * <ul>
     * <li>the path is relative, or blank when there is no default;</li>
     * <li>the file or folder does not exist;</li>
     * <li>a folder was asked for and a file was given, or the other way round; or</li>
     * <li>a file was asked for and its extension is not one the field accepts.</li>
     * </ul>
     *
     * @param fileField which input is being asked for; supplies the prompt name, whether a folder or file is
     *         expected, and the accepted extensions
     * @param defaultFile the answer used when the user just presses Enter, and shown by name in the prompt;
     *         null when there is no default (a blank answer is then rejected)
     * @param sc the scanner that reads the user's answers
     * @return an existing file or folder that satisfies {@code fileField}; never null
     * @throws InputEndedException if the input ends before an acceptable answer is read
     */
    public static File promptFile(Field fileField, File defaultFile, Scanner sc) {
        File file = null;

        do {
            try {
                String prompt = String.format("Enter %s%s: ",
                        fileField.getName(), defaultFile != null ? " (" + defaultFile.getName() + ")" : "");

                Path filepath = promptPath(prompt, fileField.getName(), defaultFile != null ? defaultFile.toPath() : null, sc);
                file = filepath.toFile();

                // the file/directory does not exist.
                if (!file.exists()) {
                    System.out.println(TextColor.ERROR.getActionTag() + " File not found: " + filepath.toString() + "\n");
                    file = null;
                    continue;
                }

                if (fileField.isDir()) {
                    FileManager.validateDirectory(file);
                } else {
                    FileManager.validateFile(file, fileField.getExtensions());
                }

            } catch (IllegalArgumentException e) {
                // the user enters a:
                // - non-absolute (relative) path.
                // - directory path when prompted for a file path.
                // - file path with invalid extensions or when prompted for a directory path.
                System.out.println(TextColor.ERROR.getActionTag() + " Invalid input: " + e.getMessage() + "\n");
                file = null;
            }
        } while (file == null);

        return file;
    }

    /**
     * <p>
     * Asks the user for a whole number from 1 up to a maximum, such as the grading timeout in seconds.
     * </p><p>
     * Re-prompts when the line is not a whole number, is zero or negative, or is above the maximum,
     * the same way {@link #promptFile} re-prompts on a bad path.
     * </p>
     *
     * @param label name shown in the prompt and in the error message, e.g. "Grading timeout in seconds"
     * @param defaultValue the current value, shown in brackets and returned when the user just presses Enter
     * @param maxValue the largest value accepted (inclusive)
     * @param sc the scanner that reads the user's answers
     * @return the number typed (from 1 to {@code maxValue}), or {@code defaultValue} if the line was blank
     * @throws InputEndedException if the input ends before an answer is read
     */
    public static long promptPositiveLong(String label, long defaultValue, long maxValue, Scanner sc) {
        // One message for every kind of bad line, naming the accepted range so the user knows what to type.
        String invalid = TextColor.ERROR.getActionTag() + " Invalid input: " + label + " must be a whole number from 1 to " + maxValue + ".\n";
        // Loops until a valid value is returned from inside the body.
        while (true) {
            // Show the current value in brackets so Enter visibly means "keep this".
            System.out.print(String.format("Enter %s (%d): ", label, defaultValue));
            // Piped input can end mid-prompt; fail the same way promptPath does instead of spinning forever.
            if (!sc.hasNextLine()) {
                throw new InputEndedException(label);
            }
            // Trim so " 5 " counts as 5.
            String text = sc.nextLine().trim();
            // Blank line: keep the current value.
            if (text.isEmpty()) {
                return defaultValue;
            }
            try {
                // Long.parseLong rejects "1.5", "abc" and values beyond the long range, 
                // all as NumberFormatException.
                long value = Long.parseLong(text);
                // Zero and negatives would stop every process at once, and 
                // a huge value could hang or crash grading, so only 1 to maxValue is kept.
                if (value > 0 && value <= maxValue) {
                    return value;
                }
                System.out.println(invalid);
            } catch (NumberFormatException e) {
                System.out.println(invalid);
            }
        }
    }

    /**
     * Asks for a menu option, which is a number from {@code min} to {@code max}, with 0 usually used to go
     * back to the previous menu or exit the program. Anything else prints an error line and asks again.
     *
     * @param min the lowest number accepted (inclusive)
     * @param max the highest number accepted (inclusive)
     * @param sc the scanner that reads the user's answers
     * @return the option the user chose, between {@code min} and {@code max}
     * @throws InputEndedException if the input ends before a valid option is read
     */
    public static int promptOption(int min, int max, Scanner sc) {
        while (true) {
            System.out.print("Enter option: ");
            if (!sc.hasNextLine()) throw new InputEndedException("an option");
            try {
                int option = Integer.parseInt(sc.nextLine().trim());
                if (option >= min && option <= max) return option;
            } catch (NumberFormatException ignored) { }
            // Same wording as the main menu; the loop then shows "Enter option: " again.
            System.out.println(TextColor.ERROR.getActionTag() + " Please enter a number between " + min + " and " + max + ".");
        }
    }

    /**
     * Asks a yes/no question and keeps asking until the answer is Y or N, in either letter case.
     *
     * @param question the question text; " (Y/N): " is appended to it
     * @param sc the scanner that reads the user's answers
     * @return true if the user answered Y; false if the user answered N
     * @throws InputEndedException if the input ends before a Y or N is read
     */
    public static boolean promptYesNo(String question, Scanner sc) {
        while (true) {
            System.out.print(question + " (Y/N): ");
            if (!sc.hasNextLine()) throw new InputEndedException("a Y/N answer");
            String answer = sc.nextLine().trim();
            if (answer.equalsIgnoreCase("Y")) return true;
            if (answer.equalsIgnoreCase("N")) return false;
            System.out.println(TextColor.ERROR.getActionTag() + " Please enter Y or N.");
        }
    }

    // Helper Functions

    /**
     * Prints a prompt and reads one path from the user. Surrounding quotes are removed, backslash escapes
     * from dragging a file into the terminal are undone on Mac and Linux, and a leading {@code ~} becomes the home folder.
     *
     * @param prompt the full text printed before waiting for the answer
     * @param label what is being asked for, used in the message if the input ends
     * @param defaultFilePath the path returned when the line is blank; null if a blank line is not allowed
     * @param sc the scanner that reads the answer
     * @return the absolute path the user entered, or {@code defaultFilePath} for a blank line
     * @throws IllegalArgumentException if the line is blank and there is no default, or the path is not absolute
     * @throws InputEndedException if the input ends before a line is read
     */
    private static Path promptPath(String prompt, String label, Path defaultFilePath, Scanner sc)
            throws IllegalArgumentException {
        System.out.print(prompt);
        if (!sc.hasNextLine()) {
            throw new InputEndedException(label);
        }
        String pathString = sc.nextLine().trim();

        // if pathString is encased with quotation mark, trim it.
        // Try a double-quoted pattern, then a single-quoted one (paths pasted or dragged in often carry quotes).
        for (String p : new String[]{"\"(?:[^\"]|\"\")*\"", "'(?:[^']|'')*'"}) {
            // The whole text must be wrapped in the quotes.
            if (Pattern.matches(p, pathString)) {
                // Drop the first and last character, which are the quotes.
                pathString = pathString.substring(1, pathString.length() - 1);
                // Only one kind of quote is ever removed.
                break;
            }
        }

        // On a Mac (or Linux), dragging a file into Terminal types its path with a backslash before
        // spaces and some symbols, e.g. /Users/jon/My\ Drive/sheet.csv. Remove those backslashes.
        // (Windows paths use backslashes as separators, so they are left alone there.)
        if (File.separatorChar == '/') {
            pathString = pathString.replaceAll("\\\\(.)", "$1");
        }
        // "~" means the home folder, e.g. ~/Documents becomes /Users/jon/Documents.
        if (pathString.equals("~") || pathString.startsWith("~/")) {
            pathString = System.getProperty("user.home") + pathString.substring(1);
        }

        if (pathString.length() == 0) {
            if (defaultFilePath != null) {
                return defaultFilePath;
            }
            // Nothing typed and nothing to keep: ask for a path rather than complain that "" is not absolute.
            throw new IllegalArgumentException("Please enter a path.");
        }

        Path path = Paths.get(pathString);

        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("The path must be absolute.");
        }

        return path;
    }

}
