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

    private Input() {} // disable instancing

    /**<p>
     * Ask user for the path of the file/directory and returns the file object.
     * </p><p>
     * When this method is called, the terminal will show a prompt, then the user
     * inputs the
     * absolute path of the file/directory.
     * </p><p>
     * The method will ask the user to re-enter the path if:
     * </p>
     * <ul>
        * <li>the user enters a non-absolute (relative) path;</li>
        * <li>the file/directory does not exist;</li>
        * <li>the prompt ask for directory and user inputs a file, and vice versa; and</li>
        * <li>the file extension is invalid.</li>
     * </ul>
     *
     * @param fileField   The prompt to be printed out to the user.
     * @param defaultFile The default file returned if user enters none.
     * @param sc          The scanner object to read user input.
     * @return The file/directory object. If user enters none, returns the
     *         defaultFile.
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
                    // A testers folder with no testers is accepted (it can be filled in later), but say so now
                    // instead of only when grading finds nothing to run.
                    if (fileField == Field.TESTERS && !FileManager.containsTesters(file)) {
                        System.out.println(TextColor.WARNING.getActionTag() + " No *Tester.java files or question"
                                + " folders were found in " + file + ". Grading needs the instructor's tester files.\n");
                    }
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

    /**<p>
     * Ask the user for a positive whole number no larger than a maximum, such as the grading timeout in seconds.
     * </p><p>
     * Re-prompts when the line is not a whole number, is zero or negative, or is above the maximum,
     * the same way {@link #promptFile} re-prompts on a bad path.
     * </p>
     * @param label        Name shown in the prompt, e.g. "Grading timeout in seconds".
     * @param defaultValue The current value, kept when the user just presses Enter.
     * @param maxValue     The largest value accepted.
     * @param sc           The scanner object to read user input.
     * @return The number typed (from 1 to maxValue), or defaultValue if the line was blank.
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
     * Prompt an option, which is a number from <code>min</code> to <code>max</code>
     * with 0 usually used to go to the previous menu or exit the program.
     * 
     * @param min the lowest option
     * @param max the highest option
     * @param sc the Scanner instance to read user input
     * @return option
     * @throws InputEndedException
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
     * Ask a yes/no question, retrying until a valid answer is entered.
     * @param question to be printed as the prompt
     * @param sc the scanner used to read user input
     * @return boolean value representing the user input ("Y" -> <code>true</code>; "N" -> <code>false</code>)
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
     * Ask user for the filepath.
     *
     * @param prompt          The prompt that is printed in the terminal.
     * @param label           What is being asked for, named if the input ends.
     * @param defaultFilePath The default file path.
     * @param sc              The reader to reads the user input.
     * @return The path the user has input in absolute form. If user inputs nothing,
     *         it returns <code>defaultFilePath</code>.
     * @throws IllegalArgumentException If the path is blank (with no default) or not absolute.
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
