package autogradesys;

import java.util.Scanner;
import java.io.File;

import autogradesys.config.Config;
import autogradesys.display.Cli;
import autogradesys.display.Prompt;

public class App {

    private static Config promptConfig(Scanner sc) {
        File submissions = Cli.promptFile(Prompt.SUBMISSIONS_PROMPT, sc);
        File testers = Cli.promptFile(Prompt.TESTERS_PROMPT, sc);
        File scoresheet = Cli.promptFile(Prompt.SCORESHEET_PROMPT, sc);
        return new Config(submissions, testers, scoresheet);
    }

    private static void displayMenu() {
        System.out.println("__Menu__________");
        System.out.println("1. Edit Paths");
        System.out.println("2. Run Auto-Grader");
        System.out.println("0. Exit");
    }

    public static void displayConfig(Config config) {
        System.out.println("__Configuration____");
        System.out.println("Submissions folder: " + config.getSubmissionsPath());
        System.out.println("Testers folder: " + config.getTestersPath());
        System.out.println("Scoresheet file: " + config.getScoresheetPath());
    }

    public static void main(String[] args) {
        // Title
        System.out.println("Auto-Grader System");
        System.out.println();

        // Prompt user for dirpaths
        Scanner sc = new Scanner(System.in);

        // TODO: Improve the UX by making a save file and config.
        Config config;

        if (!Config.configInitialized()) {
            config = promptConfig(sc);
            boolean saveSuccess = config.saveConfig();
            if (saveSuccess) {
                System.out.println("Saved successfully");
            } else {
                System.out.println("Save unsuccessful");
            }
        } else {
            config = Config.loadConfig();
        }

        
        int menuOption;
        do {
            displayConfig(config);
            System.out.println();
            displayMenu();
            System.out.print("Enter option: ");
            menuOption = sc.nextInt();
            sc.nextLine();

            switch (menuOption) {
                case 1:
                    config = promptConfig(sc);
                    boolean saveSuccess = config.saveConfig();
                    if (saveSuccess) {
                        System.out.println("\nPreference saved!\n");
                    } else {
                        System.out.println("\nSave unsuccessful\n");
                    }
                    break;
                case 2:
                    // TODO: Put the function that passes in the three file objects.
                    System.out.println("\nRunning autograder!\n");
                    break;
                case 0:
                    break;
                default:
                    System.out.println("\nOption unregistered. Try again.\n");
                    break;
            }
        } while (menuOption != 0);


        // Close the scanner after the user exits the program.
        sc.close();

        // TODO: Display exit messages
        System.out.println("\nThank you for using!");
        // System.out.println();
        // System.out.printf("\"%s\"%n\"%s\"%n\"%s\"%n", submissions, testers, scoresheet);

    }

}
