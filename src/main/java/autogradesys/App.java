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

    public static void main(String[] args) {
        boolean debugMode = ( args.length == 1 && args[0].equals("--debug") );
        if (args.length == 0 || debugMode) {
            startApp(debugMode);
        }
        System.out.println(TextColor.ERROR.getActionTag() + " Usage: ./run.bat [--demo|--debug]");
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
