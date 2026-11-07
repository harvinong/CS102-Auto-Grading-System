package autogradesys;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Duration;
import java.nio.file.Path;
import java.util.Scanner;

import autogradesys.grading.ErrorHistory;
import autogradesys.grading.ErrorReporter;
import autogradesys.grading.GradeResult;
import autogradesys.grading.HtmlReportWriter;
import autogradesys.grading.JavaGrader;
import autogradesys.input.CLI;
import autogradesys.input.Field;
// Feature import: the read-only submission check.
import autogradesys.preflight.SubmissionPreflight;
import autogradesys.submission.StagedSubmissions;

public class AutoGradeMenu {

    // Perform this small step of the grading workflow.
    private Scanner sc;
    // Perform this small step of the grading workflow.
    private AutoGradeCtrl ctrl;

    public AutoGradeMenu(Scanner sc, AutoGradeCtrl ctrl) {
        // Prepare or update the value needed by the next grading step.
        this.sc = sc;
        // Prepare or update the value needed by the next grading step.
        this.ctrl = ctrl;

        // Check if config is initialized, then prompt
        boolean loadSuccess = ctrl.loadConfig();
        // Check this condition so grading follows the intended path.
        if (!loadSuccess) {
            // No config file initialized. User is new
            newView();
        }
    }

    public void displayTitle() {
        // Prepare or update the value needed by the next grading step.
        System.out.println("+== Auto-Grading System ==+");
    }

    public void displayMenu() {
        // Perform this small step of the grading workflow.
        displayTitle();
        // Perform this small step of the grading workflow.
        System.out.println("1. View configuration paths");
        // Perform this small step of the grading workflow.
        System.out.println("2. Edit configuration paths");
        // Perform this small step of the grading workflow.
        System.out.println("3. Grade students (run auto-grader)");
        // Perform this small step of the grading workflow.
        System.out.println("0. Quit Application");
        // Perform this small step of the grading workflow.
        System.out.print("Enter option: ");
    }

    public void readOption() {
        // Perform this small step of the grading workflow.
        int menuOption;
        // Repeat this operation for each relevant grading item.
        do {
            // Perform this small step of the grading workflow.
            displayMenu();
            // Prepare or update the value needed by the next grading step.
            menuOption = sc.nextInt();
            // Perform this small step of the grading workflow.
            sc.nextLine();

            // Perform this small step of the grading workflow.
            switch (menuOption) {
                // Perform this small step of the grading workflow.
                case 1:
                    // Perform this small step of the grading workflow.
                    viewConfig();
                    // Perform this small step of the grading workflow.
                    break;
                // Perform this small step of the grading workflow.
                case 2:
                    // Perform this small step of the grading workflow.
                    editConfig();
                    // Perform this small step of the grading workflow.
                    break;
                // Perform this small step of the grading workflow.
                case 3:
                    // Perform this small step of the grading workflow.
                    runGrader();
                    // Perform this small step of the grading workflow.
                    break;
                // Perform this small step of the grading workflow.
                case 0:
                    // Perform this small step of the grading workflow.
                    System.out.println("\nThank you for using!");
                    // Perform this small step of the grading workflow.
                    break;
                // Perform this small step of the grading workflow.
                default:
                    // Perform this small step of the grading workflow.
                    System.out.println("\nPlease enter between 0 to 3.\n");
                    // Perform this small step of the grading workflow.
                    break;
            }
        // Prepare or update the value needed by the next grading step.
        } while (menuOption != 0);

    }

    public void newView() {
        // display new view for new user
        displayTitle();
        System.out.println();
        System.out.println(getWelcomeText());
        System.out.println();

        // Ask for user input
        inputConfig(true);
    }

    public void viewConfig() {
        // Perform this small step of the grading workflow.
        System.out.println();
        // Perform this small step of the grading workflow.
        System.out.println("__ Configuration __");
        // Perform this small step of the grading workflow.
        System.out.println("Starting folder: " + ctrl.getStartingFolder());
        // Perform this small step of the grading workflow.
        System.out.println("Testers folder:  " + ctrl.getTestersFolder());
        // Perform this small step of the grading workflow.
        System.out.println("Scoresheet file: " + ctrl.getScoresheetFile());
        // Perform this small step of the grading workflow.
        System.out.println();
    }

    public void editConfig() {
        // Perform this small step of the grading workflow.
        inputConfig(false);
    }

    public void runGrader() {
        // Freshly prompted so instructor can input folder of submissions.
        // Keep the folder the instructor typed (the original call discarded it) so the check can scan it.
        File submissions = CLI.promptFile(Field.SUBMISSIONS, null, sc);

        ErrorReporter reporter = new ErrorReporter(
                new ErrorHistory(Path.of(".grader-error-history.jsonl")), System.out);

        try (StagedSubmissions staged = ctrl.stageSubmissions(submissions, reporter)) {
            gradeFolder(staged.root(), reporter);
        } catch (IOException | IllegalArgumentException exception) {
            System.out.println("Could not prepare the submissions: " + exception.getMessage());
        }
    }

    private void gradeFolder(Path submissionsRoot, ErrorReporter reporter) {
        // Feature: read-only layout check before grading. It ASSUMES the Starting Folder is the starter
        // template (e.g. RenameToYourStudentID); this is the only place that assumption is made.
        // check(...) compares every submission with the template and returns a report (it changes nothing);
        // the template path comes from the saved configuration, the submissions path from the prompt above;
        // print(...) writes the report to the console.
        SubmissionPreflight.check(ctrl.getStartingFolder().toPath(), submissionsRoot).print(System.out);

        // Perform this small step of the grading workflow.
        System.out.println("\nRunning autograder!\n");
        GradeResult result = JavaGrader.grade(submissionsRoot,
                ctrl.getTestersFolder().toPath(), ctrl.getScoresheetFile().toPath(),
                // Perform this small step of the grading workflow.
                Duration.ofSeconds(2), reporter);
        // Perform this small step of the grading workflow.
        try {
            // Prepare or update the value needed by the next grading step.
            Path report = Path.of(".grader-report.html");
            // Perform this small step of the grading workflow.
            HtmlReportWriter.write(report, result, reporter.records());
            // Perform this small step of the grading workflow.
            System.out.println("HTML report: " + report.toAbsolutePath().normalize());
        } catch (java.io.IOException exception) {
            // Perform this small step of the grading workflow.
            System.out.println("Could not write HTML report: " + exception.getMessage());
        }
        System.out.printf("Completed: %d submission(s), %d reported issue(s).%n",
                // Perform this small step of the grading workflow.
                result.studentCount(), result.issueCount());
    }

    // Helper function
    private void inputConfig(boolean askNewFiles) {
        File startingFolder = CLI.promptFile(
                Field.STARTING_FOLDER,
                askNewFiles ? null : ctrl.getStartingFolder(),
                // Perform this small step of the grading workflow.
                sc);
        File testers = CLI.promptFile(
                Field.TESTERS,
                askNewFiles ? null : ctrl.getTestersFolder(),
                // Perform this small step of the grading workflow.
                sc);
        File scoresheet = CLI.promptFile(
                Field.SCORESHEET,
                askNewFiles ? null : ctrl.getScoresheetFile(),
                // Perform this small step of the grading workflow.
                sc);

        // Prepare or update the value needed by the next grading step.
        boolean saveSuccess = ctrl.saveConfig(startingFolder, testers, scoresheet);

        // Check this condition so grading follows the intended path.
        if (saveSuccess) {
            // Perform this small step of the grading workflow.
            System.out.println("\nConfiguration saved!\n");
        } else {
            // Perform this small step of the grading workflow.
            System.out.println("\nSave unsuccessful\n");
        }
    }

    private static String getWelcomeText() {
        try (Scanner sc = new Scanner(new File("./assets/welcome.md"))) {
            StringBuilder result = new StringBuilder();
            while (sc.hasNextLine()) {
                result.append(sc.nextLine());
                result.append('\n');
            }
            return result.toString().trim();
        } catch (FileNotFoundException e) {
            return "Welcome to the Auto-Grading System!";
        }
    }
}
