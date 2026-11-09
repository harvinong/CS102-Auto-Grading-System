package autogradesys;

import autogradesys.cli.InputEndedException;
import autogradesys.cli.TextColor;
import autogradesys.controller.MainCtrl;
import autogradesys.menu.MainMenu;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Scanner;

/**
 * CLI entry point for grading.
 *
 * <p>Three modes: {@code --demo} runs the sample fixtures, no arguments opens the interactive menu,
 * and {@code --debug} enables full stack trace when error happens.</p>
 */
public final class App {
    // Exit status constants
    private static final int EXIT_NORMAL = 0;        // Exit code 0: exit normally
    private static final int EXIT_RUNTIME = 1;       // Exit code 1: the session stopped abnormally
    private static final int EXIT_STARTUP_ERROR = 2; // Exit code 2: for a startup problem.

    // Entry-point class with only static methods; no instances.
    private App() {}

    /** Program start: runs the chosen mode and exits with its status code. */
    public static void main(String[] args) {
        // Hand the exit code from run() to the operating system so scripts and CI can detect failure.
        System.exit(run(args, System.out));
    }

    // Testable core of main(): takes the stream explicitly so tests can capture the output.
    public static int run(String[] args, PrintStream output) {
        // The single word "demo" runs the built-in sample fixtures.
        if (args.length == 1 && args[0].equals("--demo")) {
            // Grade the fixtures under the demo folder in the current directory.
            return DemoRunner.run(Path.of("demo"), output);
        }
        // No arguments: start the interactive menu for a person at the keyboard.
        boolean debugMode = ( args.length == 1 && args[0].equals("--debug") );
        if (args.length == 0 || debugMode) {
            // Menu mode, where option 3 runs preflight, grading and the report.
            return runInteractive(output, debugMode);
        }
        // Anything else is treated as an invalid runs.
        output.println(TextColor.ERROR.getActionTag() + " Usage: ./run.bat [--demo|--debug]");
        return EXIT_STARTUP_ERROR;
    }

    /** Menu mode: the controller holds session state and the menu drives it. */ 
    private static int runInteractive(PrintStream output, boolean debugMode) {
        try (Scanner scanner = new Scanner(System.in)) {
            // Create the menu with a fresh controller (the shared state of the session).
            MainMenu menu = new MainMenu(scanner, new MainCtrl());
            // Hand control to the menu, which shows options and handles them until the user quits.
            menu.readOption();
            
            return EXIT_NORMAL;
        // The input ended (end of a piped script, or Ctrl+D) while a prompt was waiting for an answer.
        } catch (InputEndedException exception) {
            // Say what was being asked; the unfinished action was not saved or run.
            output.println();
            output.println(TextColor.INFO.getActionTag() + " " + exception.getMessage() + "; exiting.");
            
            return EXIT_RUNTIME;
        // Unexpected failure while starting or running the menu.
        } catch (RuntimeException exception) {
            output.print(TextColor.ERROR.getActionTag() + " ");
            if (debugMode) {
                // Show a detailed message in debugging mode.
                exception.printStackTrace(output);
            } else {
                // Show a short message instead of a stack trace.
                output.println("Could not start grading: " + exception.getMessage());
            }
            return EXIT_RUNTIME;
        }
    }
}
