package autogradesys;

import java.util.Scanner;

import autogradesys.cli.InputEndedException;
import autogradesys.cli.TextColor;
import autogradesys.controller.MainCtrl;
import autogradesys.menu.MainMenu;

/**
 * CLI entry point for grading.
 */
public final class App {
    // Entry-point class with only static methods; no instances.
    private App() {}

    // Exit status when the input ends (end of a piped script, or Ctrl+D) while a prompt is waiting.
    private static final int INPUT_ENDED = 1;
    // Exit status when the program stops because of an unexpected error.
    private static final int UNEXPECTED_ERROR = 1;
    // Exit status for a command line the program does not understand.
    private static final int USAGE_ERROR = 2;

    public static void main(String[] args) {
        boolean debugMode = (args.length == 1 && args[0].equals("--debug"));
        if (args.length == 0 || debugMode) {
            startApp(debugMode);
        } else {
            // Unknown arguments: show the usage and exit with a non-zero status so scripts can tell it failed.
            System.out.println(TextColor.ERROR.getActionTag() + " Usage: run.bat [--debug]  (or ./run.sh [--debug])");
            System.exit(USAGE_ERROR);
        }
    }

    /** Menu mode: the controller holds session state and the menu drives it. */ 
    private static void startApp(boolean debugMode) {
        try (Scanner scanner = new Scanner(System.in)) {
            MainMenu menu = new MainMenu(scanner, new MainCtrl());
            menu.readOption();

        } catch (InputEndedException exception) {
            // The input ended (end of a piped script, or Ctrl+D) while a prompt was waiting for an input.
            // State the unfinished action that was not saved or run.
            System.out.println();
            System.out.println(TextColor.INFO.getActionTag() + " " + exception.getMessage() + "; exiting.");
            // Non-zero so a script can tell the program stopped before the user chose to quit.
            System.exit(INPUT_ENDED);

        } catch (RuntimeException exception) {
            // Unexpected failure while starting or running the menu.
            System.out.print(TextColor.ERROR.getActionTag() + " ");

            if (debugMode) {
                // Show a detailed message in debugging mode.
                exception.printStackTrace(System.out);
            } else {
                // Show a short message instead of a stack trace.
                System.out.println("Could not start grading: " + exception.getMessage());
            }
            System.exit(UNEXPECTED_ERROR);

        }
    }
}
