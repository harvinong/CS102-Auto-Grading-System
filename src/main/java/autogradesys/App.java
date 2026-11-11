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
    /**
     * Prevents instantiation; this class only offers the static {@link #main} entry point.
     */
    private App() {}

    /**
     * Program entry point. Starts the interactive menu when run with no arguments or with the single
     * argument {@code --debug}; any other argument list prints a usage line and exits without starting.
     *
     * @param args command-line arguments; empty for a normal run, or exactly {@code ["--debug"]} to show full
     *         stack traces when startup fails. Any other content is rejected with a usage message.
     */
    public static void main(String[] args) {
        
        boolean debugMode = ( args.length == 1 && args[0].equals("--debug") );
        if (args.length == 0 || debugMode) {
            startApp(debugMode);
        } else {
            System.out.println(TextColor.ERROR.getActionTag() + " Usage: ./run.sh (Mac) or run.bat (Windows), optionally with --debug");
        }
    }

    /**
     * Menu mode: creates the main menu and controller on standard input and runs it until the user exits.
     * Input that ends mid-prompt is reported as an info line, and an unexpected runtime failure is printed as an
     * error line (with a stack trace only in debug mode) instead of crashing the program.
     *
     * @param debugMode true to print the full stack trace of an unexpected failure; false to print only its message
     */
    private static void startApp(boolean debugMode) {
        try (Scanner scanner = new Scanner(System.in)) {
            MainMenu menu = new MainMenu(scanner, new MainCtrl());
            menu.readOption();

        } catch (InputEndedException exception) {
            // The input ended (end of a piped script, or Ctrl+D) while a prompt was waiting for an input.
            // State the unfinished action that was not saved or run.
            System.out.println();
            System.out.println(TextColor.INFO.getActionTag() + " " + exception.getMessage() + "; exiting.");

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

        }
    }
}
