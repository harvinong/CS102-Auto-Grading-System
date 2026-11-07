package autogradesys;

import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
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
import autogradesys.validation.CacheReport;

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
        // Start at -1 so the loop condition below is not already satisfied before the first choice is read.
        int menuOption = -1;
        // Show the menu at least once and keep showing it until the user picks 0.
        do {
            // Print the numbered options and the "Enter option" prompt.
            displayMenu();
            // Piped or closed input has no next line; stop cleanly instead of throwing NoSuchElementException.
            if (!sc.hasNextLine()) {
                System.out.println("\nInput ended; exiting.");
                return;
            }
            // Read the whole line and trim it, so stray spaces around the number do not matter.
            String optionText = sc.nextLine().trim();
            try {
                // Convert the typed text to a number for the switch below.
                menuOption = Integer.parseInt(optionText);
            } catch (NumberFormatException exception) {
                // Text such as "abc" is not a number; ask again rather than crash.
                System.out.println("\nPlease enter a number between 0 and 3.\n");
                // Skip the switch and go straight to the loop condition, then show the menu again.
                continue;
            }

            // Dispatch the choice: 1 and 2 manage configuration, 3 runs the grading flow, 0 quits.
            switch (menuOption) {
                case 1:
                    viewConfig();
                    // End this case here, otherwise Java would fall through into the next menu option.
                    break;
                case 2:
                    editConfig();
                    // Stop at this break so editing the configuration does not also start grading.
                    break;
                case 3:
                    runGrader();
                    // Stop here so grading does not fall through into the quit case; the loop then shows the menu again.
                    break;
                case 0:
                    System.out.println("\nThank you for using!");
                    // Exit the switch; the do-while condition (menuOption != 0) now ends the loop and returns to App.
                    break;
                default:
                    System.out.println("\nPlease enter a number between 0 and 3.\n");
                    // Leave the switch so the loop redisplays the menu after the invalid-choice message.
                    break;
            }
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
        System.out.println();
        System.out.println("__ Configuration __");
        System.out.println("Starting folder: " + ctrl.getStartingFolder());
        System.out.println("Testers folder:  " + ctrl.getTestersFolder());
        System.out.println("Scoresheet file: " + ctrl.getScoresheetFile());
        // The two grading limits come from config.json (or the defaults when it has none), so show what grading will really use.
        System.out.println("Grading timeout: " + ctrl.getTimeoutSeconds() + " second(s) per process");
        System.out.println("Output limit:    " + ctrl.getOutputLimitBytes() + " bytes per process");
        System.out.println();
    }

    public void editConfig() {
        inputConfig(false);
    }

    /**
     * Menu option 3: the full grade-students flow. Ask for the submissions folder, run the
     * read-only preflight check (stopping if its setup is fatal), grade every student with
     * JavaGrader (compile, run, compare, update the CSV), then write the HTML report and print a summary.
     */
    public void runGrader() {
        // Ask for the submissions folder every time, since it changes between assignments; keep the answer so both the preflight and the grader receive it.
        File submissions = CLI.promptFile(Field.SUBMISSIONS, null, sc);

        // One reporter owns the durable audit trail for preflight and grading diagnostics.
        ErrorReporter reporter = new ErrorReporter(
                new ErrorHistory(Path.of(".grader-error-history.jsonl")), System.out);
        // Prepare a safe working copy: folders are copied and archives are extracted with zip-slip checks.
        CacheReport working = ctrl.prepareWorkingCopy(submissions);
        working.print(System.out);
        long cachedIgnored = working.events().stream()
                .filter(event -> event.type() == autogradesys.validation.ValidationEvent.Type.IGNORED_JUNK)
                .peek(event -> reporter.ignored("preflight", "ignored-junk", event.path() + " ignored",
                        "Remove this generated or OS metadata file if desired", event.path()))
                .count();
        if (!working.usable()) return;
        Path gradingPath = working.workingDir();
        // Feature: read-only layout check before grading. It ASSUMES the Starting Folder is the starter
        // template (e.g. RenameToYourStudentID); this is the only place that assumption is made.
        // check(...) compares every submission with the template and returns a report (it changes nothing);
        // the template path comes from the saved configuration, the submissions path from the prompt above;
        // print(...) writes the report to the console.
        var preflight = SubmissionPreflight.check(ctrl.getStartingFolder().toPath(), gradingPath);
        // Persist ignored clutter silently; PreflightReport intentionally omits it from console output.
        long ignored = preflight.findings().stream()
                .filter(f -> f.category() == autogradesys.preflight.PreflightFinding.Category.IGNORED_JUNK)
                .peek(f -> reporter.ignored("preflight", "ignored-junk", f.path() + " ignored",
                        "Remove this generated or OS metadata file if desired", f.path()))
                .count();
        // Show the findings (per-student ready or needs-attention list) before any code is compiled.
        preflight.print(System.out);
        long ignoredTotal = cachedIgnored + ignored;
        if (ignoredTotal > 0) System.out.println(ignoredTotal + " ignored files logged to the error report.");
        // A fatal finding means the template or submissions folder is unusable, so grading would only produce noise.
        if (preflight.fatal()) {
            // Print the same banner as a normal run so the output layout is the same either way.
            System.out.println("\nRunning autograder!\n");
            // Say plainly that grading did not start, so nobody mistakes the empty result for a pass.
            System.out.println("Autograder not started because submission setup is invalid.");
            // Back to the menu without touching the scoresheet, history or report.
            return;
        }
        // Keep the validation feature reachable: the instructor may fix, recheck, or proceed.
        while (true) {
            System.out.println("1. Fix common mistakes automatically");
            System.out.println("2. Check the working copy again");
            System.out.println("3. Start grading");
            System.out.println("0. Cancel and return to the main menu");
            int choice = CLI.promptOption(0, 3, sc);
            if (choice == 0) return;
            if (choice == 3) break;
            if (choice == 2) {
                preflight = SubmissionPreflight.check(ctrl.getStartingFolder().toPath(), gradingPath);
                preflight.print(System.out);
            } else {
                try {
                    ctrl.validateSubmissions(gradingPath, preflight, question -> CLI.promptYesNo(question, sc)).print(System.out);
                } catch (IOException exception) {
                    System.out.println("Could not validate the submissions: " + exception.getMessage());
                }
                preflight = SubmissionPreflight.check(ctrl.getStartingFolder().toPath(), gradingPath);
                preflight.print(System.out);
            }
        }

        // Separate the preflight report from the grading output with a blank line and a banner.
        System.out.println("\nRunning autograder!\n");
        // A zip or folder that could not be put into the working copy is not graded; record it so it is in the report and history too.
        for (autogradesys.validation.ValidationEvent event : working.events()) {
            if (!event.fixed()) {
                reporter.recoverable("submission", "working-copy", event.path() + " was not graded: " + event.message(),
                        "Ask the student to resubmit a valid zip file or folder.",
                        event.student().isEmpty() ? null : event.student(), event.path(), null);
            }
        }
        // Grade with the configured testers folder (questions) and scoresheet; the configured timeout (default 2 seconds) stops infinite loops and the output limit (default 1 MiB) stops flooding.
        // A copy of the starter template in the submissions folder is not a student, so it is skipped.
        GradeResult result = JavaGrader.grade(gradingPath,
                ctrl.getTestersFolder().toPath(), ctrl.getScoresheetFile().toPath(),
                Duration.ofSeconds(ctrl.getTimeoutSeconds()), ctrl.getOutputLimitBytes(),
                java.util.Set.of(preflight.templateName()), reporter);
        // Writing the report can fail on disk problems; the grades are already saved, so only a message is shown.
        try {
            // The report lands in the current folder, overwritten on every run.
            Path report = Path.of(".grader-report.html");
            // Render the scores and every recorded error into one HTML file.
            HtmlReportWriter.write(report, result, reporter.records());
            // Print the absolute path so the instructor can open the report.
            System.out.println("HTML report: " + report.toAbsolutePath().normalize());
        } catch (java.io.IOException exception) {
            // Explain why no report was produced instead of crashing the menu.
            System.out.println("Could not write HTML report: " + exception.getMessage());
        }
        // Final one-line summary: students graded and total issues recorded (fatal or not).
        System.out.printf("Completed: %d submission(s), %d reported issue(s).%n",
                result.studentCount(), result.issueCount());
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

        // First-run setup keeps the three prompts it always had and uses the defaults; only "Edit configuration paths" asks for the limits.
        long timeoutSeconds = ctrl.getTimeoutSeconds();
        long outputLimitBytes = ctrl.getOutputLimitBytes();
        if (!askNewFiles) {
            // Enter keeps the current value; anything else must be a whole number from 1 to the maximum or the prompt repeats.
            timeoutSeconds = CLI.promptPositiveLong("Grading timeout in seconds", timeoutSeconds,
                    ctrl.getMaxTimeoutSeconds(), sc);
            outputLimitBytes = CLI.promptPositiveLong("Output limit in bytes", outputLimitBytes,
                    ctrl.getMaxOutputLimitBytes(), sc);
        }

        boolean saveSuccess = ctrl.saveConfig(startingFolder, testers, scoresheet, timeoutSeconds, outputLimitBytes);

        if (saveSuccess) {
            System.out.println("\nConfiguration saved!\n");
        } else {
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
