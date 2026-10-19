package autogradesys;

import java.util.Scanner;
import java.io.File;

import autogradesys.config.Config;
import autogradesys.display.CLI;
import autogradesys.display.Field;

public class App {

    private static Config appConfig;

    private static Config promptConfig(Scanner sc, boolean askNewFiles) {
        if (askNewFiles) {
            appConfig = Config.get(null, null, null);
        }
        File starting_folder = CLI.promptFile(Field.STARTING_FOLDER, appConfig.getStartingFolderPath(), sc);
        File testers = CLI.promptFile(Field.TESTERS, appConfig.getTestersPath(), sc);
        File scoresheet = CLI.promptFile(Field.SCORESHEET, appConfig.getScoresheetPath(), sc);
        return Config.get(starting_folder, testers, scoresheet);
    }

    private static void displayMenu() {
        System.out.println("__Menu__________");
        System.out.println("1. Edit Paths");
        System.out.println("2. Run Auto-Grader");
        System.out.println("0. Exit");
    }

    public static void displayConfig(Config config) {
        System.out.println("__Configuration____");
        System.out.println("Starting folder: " + config.getStartingFolderPath());
        System.out.println("Testers folder: " + config.getTestersPath());
        System.out.println("Scoresheet file: " + config.getScoresheetPath());
    }

    public static void main(String[] args) {
        // Title
        System.out.println("Auto-Grader System");
        System.out.println();

        // Prompt user for dirpaths
        Scanner sc = new Scanner(System.in);

        appConfig = Config.loadConfig();

        if (appConfig == null) {
            appConfig = promptConfig(sc, true);
            boolean saveSuccess = appConfig.saveConfig();
            if (saveSuccess) {
                System.out.println("Saved successfully");
            } else {
                System.out.println("Save unsuccessful");
            }
        }

        displayConfig(appConfig);
        
        int menuOption;
        do {
            System.out.println();
            displayMenu();
            System.out.print("Enter option: ");
            menuOption = sc.nextInt();
            sc.nextLine();

            switch (menuOption) {
                case 1:
                    appConfig = promptConfig(sc, false);
                    boolean saveSuccess = appConfig.saveConfig();
                    if (saveSuccess) {
                        System.out.println("\nPreference saved!\n");
                    } else {
                        System.out.println("\nSave unsuccessful\n");
                    }
                    displayConfig(appConfig);
                    break;
                case 2:
                    CLI.promptFile(Field.SUBMISSIONS, null, sc); // Freshly prompted so instructor can input folder of submissions.
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

    }

}
