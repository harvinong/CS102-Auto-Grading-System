package autogradesys;

import java.io.File;
import java.util.Scanner;

import autogradesys.input.CLI;
import autogradesys.input.Field;
import autogradesys.preflight.SubmissionPreflight;

public class AutoGradeMenu {

    private Scanner sc;
    private AutoGradeCtrl ctrl;

    public AutoGradeMenu(Scanner sc, AutoGradeCtrl ctrl) {
        this.sc = sc;
        this.ctrl = ctrl;

        // Check if config is initialized, then prompt
        boolean loadSuccess = ctrl.loadConfig();
        if (!loadSuccess) {
            // No config file initialized. User is new
            newView();
        }
    }

    public void displayTitle() {
        System.out.println("+== Auto-Grading System ==+");
    }

    public void displayMenu() {
        displayTitle();
        System.out.println("1. View configuration paths");
        System.out.println("2. Edit configuration paths");
        System.out.println("3. Grade students (run auto-grader)");
        System.out.println("0. Quit Application");
        System.out.print("Enter option: ");
    }

    public void readOption() {
        int menuOption;
        do {
            displayMenu();
            menuOption = sc.nextInt();
            sc.nextLine();

            switch (menuOption) {
                case 1:
                    viewConfig();
                    break;
                case 2:
                    editConfig();
                    break;
                case 3:
                    runGrader();
                    break;
                case 0:
                    System.out.println("\nThank you for using!");
                    break;
                default:
                    System.out.println("\nPlease enter between 0 to 3.\n");
                    break;
            }
        } while (menuOption != 0);

    }

    public void newView() {
        // display new view for new user
        displayTitle();
        System.out.println(
            "\n" +
            "Welcome to the Auto-Grading System! Streamline grading\n" +
            "by batch-validate and test students' submissions, and\n" +
            "then write their performances in the scoresheet.\n" +
            "\n" +
            "This program requires three configuration parameters:\n" +
            "1. Starting Folder\n" +
            "2. Tester Files Folder\n" +
            "3. Scoresheet File\n"
        );

        // Ask for user input
        inputConfig(true);
    }

    public void viewConfig() {
        System.out.println();
        System.out.println("__ Configuration __");
        System.out.println("Starting folder: " + ctrl.getStartingFolder());
        System.out.println("Testers folder:  " + ctrl.getTestersFolder());
        System.out.println("Scoresheet file: " + ctrl.getScoresheetFile());
        System.out.println();
    }

    public void editConfig() {
        inputConfig(false);
    }

    public void runGrader() {
        // Freshly prompted so instructor can input folder of submissions.
        File submissions = CLI.promptFile(Field.SUBMISSIONS, null, sc);

        // Read-only layout check. Assumes the Starting Folder is the starter template (e.g. RenameToYourStudentID).
        SubmissionPreflight.check(ctrl.getStartingFolder().toPath(), submissions.toPath()).print(System.out);

        // TODO: Put the function that passes in the three file objects.
        System.out.println("\nRunning autograder!\n");
    }

    // Helper function
    private void inputConfig(boolean askNewFiles) {
        File startingFolder = CLI.promptFile(
                Field.STARTING_FOLDER,
                askNewFiles ? null : ctrl.getStartingFolder(),
                sc);
        File testers = CLI.promptFile(
                Field.TESTERS,
                askNewFiles ? null : ctrl.getTestersFolder(),
                sc);
        File scoresheet = CLI.promptFile(
                Field.SCORESHEET,
                askNewFiles ? null : ctrl.getScoresheetFile(),
                sc);

        boolean saveSuccess = ctrl.saveConfig(startingFolder, testers, scoresheet);

        if (saveSuccess) {
            System.out.println("\nConfiguration saved!\n");
        } else {
            System.out.println("\nSave unsuccessful\n");
        }
    }
}
