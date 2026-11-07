package autogradesys;

import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.time.Duration;
import java.nio.file.Path;
import java.util.Scanner;

import autogradesys.cli.Input;
import autogradesys.cli.TextColor;
import autogradesys.config.AppSettings;
import autogradesys.filemanager.FolderDeleter;
import autogradesys.cli.Field;
import autogradesys.grading.ErrorHistory;
import autogradesys.grading.ErrorReporter;
import autogradesys.grading.GradeResult;
import autogradesys.grading.HtmlReportWriter;
import autogradesys.grading.JavaGrader;
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

    public void readOption() {
        String menuOption;
        do {
            displayMenu();
            
            // Piped or closed input has no next line; stop cleanly instead of throwing NoSuchElementException.
            if (!sc.hasNextLine()) {
                System.out.println("\nInput ended; exiting.");
                return;
            }
            menuOption = sc.nextLine().trim();

            // Dispatch the choice: 1 and 2 manage configuration, 3 runs the grading flow, 0 quits.
            switch (menuOption) {
                case "1":
                    viewConfig();
                    break;
                case "2":
                    editConfig();
                    break;
                case "3":
                    runGrader();
                    break;
                case "4":
                    viewRecentReport();
                    break;
                case "5":
                    clearCache();
                    break;
                case "0":
                    System.out.println("\nThank you for using!");
                    break;
                default:
                    System.out.println("\nPlease enter a number between 0 and 5.\n");
                    break;
            }
        } while (!menuOption.equals("0"));

    }

    /**
     * Menu option 3: the full grade-students flow, in this order:
     * <ol>
     * <li>ask for the submissions folder and build the working copy (copy folders, extract zips);</li>
     * <li>run the read-only preflight check on the copy, recording ignored junk quietly and stopping
     *     if the setup is fatal;</li>
     * <li>let the instructor fix the copy, re-check it, or start grading;</li>
     * <li>grade every student with JavaGrader (compile, run the testers, compare output, update
     *     the CSV through ScoreRecorder);</li>
     * <li>write the HTML report from the result and the recorded errors, and print a summary.</li>
     * </ol>
     * Everything that goes wrong is recorded through one ErrorReporter, so the console, the
     * history file and the HTML report all show the same records.
     */
    public void runGrader() {
        // Ask for the submissions folder every time, since it changes between assignments; keep the answer so the working copy is built from it.
        File submissions = Input.promptFile(Field.SUBMISSIONS, null, sc);

        // One reporter owns the durable audit trail for preflight and grading diagnostics.
        ErrorReporter reporter = new ErrorReporter(
                new ErrorHistory(Path.of(".grader-error-history.jsonl")), System.out);
        // Prepare a safe working copy: folders are copied and archives are extracted with zip-slip checks.
        CacheReport working = ctrl.prepareWorkingCopy(submissions);
        // Show what the copy step did (new, reused, failed) before anything is checked or graded.
        working.print(System.out);
        // Junk the copy step skipped (.DS_Store, __MACOSX) is not printed above, so record each one as a quiet note instead.
        // peek runs once per matching event and count() then gives the total for the summary line further down.
        long cachedIgnored = working.events().stream()
                .filter(event -> event.type() == autogradesys.validation.ValidationEvent.Type.IGNORED_JUNK)
                .peek(event -> reporter.ignored("preflight", "ignored-junk", event.path() + " ignored",
                        "Remove this generated or OS metadata file if desired", event.path()))
                .count();
        // If the working copy could not be set up at all, there is nothing to check or grade, so go back to the menu.
        if (!working.usable()) return;
        // Preflight and grading both run on the copy, never on the original submissions.
        Path gradingPath = working.workingDir();
        // Feature: read-only layout check before grading. It ASSUMES the Starting Folder is the starter
        // template (e.g. RenameToYourStudentID); this is the only place that assumption is made.
        // check(...) compares every submission with the template and returns a report (it changes nothing);
        // the template path comes from the saved configuration, the submissions path from the working copy built above;
        // print(...) writes the report to the console.
        var preflight = SubmissionPreflight.check(ctrl.getStartingFolder().toPath(), gradingPath);
        // Persist ignored clutter silently; PreflightReport intentionally omits it from console output.
        // This is the same quiet recording as above, but for junk found inside the student folders.
        long ignored = preflight.findings().stream()
                .filter(f -> f.category() == autogradesys.preflight.PreflightFinding.Category.IGNORED_JUNK)
                .peek(f -> reporter.ignored("preflight", "ignored-junk", f.path() + " ignored",
                        "Remove this generated or OS metadata file if desired", f.path()))
                .count();
        // Show the findings (per-student ready or needs-attention list) before any code is compiled.
        preflight.print(System.out);
        // Both sources of ignored files added together, so the instructor is told how many entries were left out of the report on screen.
        long ignoredTotal = cachedIgnored + ignored;
        // Only mention it when something was ignored, and say where the details went.
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
        // The loop only ends by returning (option 0) or breaking out (option 3).
        while (true) {
            // The four choices offered after each preflight report.
            System.out.println("1. Fix common mistakes automatically");
            System.out.println("2. Check the working copy again");
            System.out.println("3. Start grading");
            System.out.println("0. Cancel and return to the main menu");
            // Read a whole number from 0 to 3, asking again on bad input.
            int choice = Input.promptOption(0, 3, sc);
          
            // Cancel: leave without grading, so no scoresheet, history or report changes happen.
            if (choice == 0) return;
            // Start grading: leave this loop and carry on with the banner and JavaGrader below.
            if (choice == 3) break;
            // Re-check only: useful after the instructor edited the working copy by hand.
            if (choice == 2) {
                // Run the layout check again so the report reflects the edits.
                preflight = SubmissionPreflight.check(ctrl.getStartingFolder().toPath(), gradingPath);
                // Show the fresh report before offering the choices again.
                preflight.print(System.out);
            } else {
                // Choice 1: run the automatic fixes (headers, folder names, missing files), asking yes/no questions where a fix is a guess.
                try {
                    ctrl.validateSubmissions(gradingPath, preflight, question -> Input.promptYesNo(question, sc)).print(System.out);
                // A fix that cannot read or write the working copy ends up here.
                } catch (IOException exception) {
                    // Tell the instructor and fall through to the re-check, so they still see the current state.
                    System.out.println("Could not validate the submissions: " + exception.getMessage());
                }
                // Check again after the fixes, because the earlier report no longer describes the copy.
                preflight = SubmissionPreflight.check(ctrl.getStartingFolder().toPath(), gradingPath);
                // Show the updated report.
                preflight.print(System.out);
            }
        }

        // Separate the preflight report from the grading output with a blank line and a banner.
        System.out.println("\nRunning autograder!\n");
        // A zip or folder that could not be put into the working copy is not graded; record it so it is in the report and history too.
        for (autogradesys.validation.ValidationEvent event : working.events()) {
            // Fixed events (for example a zip that extracted fine) are not problems; only the failed ones are recorded.
            if (!event.fixed()) {
                // A recoverable record: that student is skipped but the others are still graded. The student is null when the failure is not tied to one folder.
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

    // -- Displays --
    private void displayMenuTitle() {
        System.out.println("+== Auto-Grading System ==+");
    }

    /** Display the options and the "Enter option" prompt. */
    private void displayMenu() {
        displayMenuTitle();
        System.out.println("1. View configuration paths");
        System.out.println("2. Edit configuration paths");
        System.out.println("3. Grade students (run auto-grader)");
        System.out.println("4. View Recent HTML Report");
        System.out.println("5. Clear Grading Cache");
        System.out.println("0. Quit Application");
        System.out.print("Enter option: ");
    }

    /** display the new view for new user, while setting the config files. */
    private void newView() {
        displayMenuTitle();
        System.out.println();
        System.out.println(getWelcomeText());
        System.out.println();

        inputConfig(true);
    }

    // -- Menu Actions --
    /** Menu Option 1: shows all the configurations set */
    private void viewConfig() {
        System.out.println();
        System.out.println("__ Configuration __");
        System.out.println("Starting folder: " + ctrl.getStartingFolder());
        System.out.println("Testers folder:  " + ctrl.getTestersFolder());
        System.out.println("Scoresheet file: " + ctrl.getScoresheetFile());
        // The two grading limits come from config.json (or the defaults when it has none), 
        // so show what grading will really use.
        System.out.println("Grading timeout: " + ctrl.getTimeoutSeconds() + " second(s) per process");
        System.out.println("Output limit:    " + ctrl.getOutputLimitBytes() + " bytes per process");
        System.out.println();
    }

    /** Menu Option 2: edit the configurations */
    private void editConfig() {
        inputConfig(false);
    }

    /** Menu Option 4: open the recent html report */
    private void viewRecentReport() {
        // TODO: implement viewRecentReport()
        System.out.println("\nViewing recent report\n");
    }

    /** Menu Option 5: clear the cache folder */
    private void clearCache() {
        Path cachePath = AppSettings.load().getCacheDir();

        if (!cachePath.toFile().exists()) {
            System.out.printf("\n%s Cache has been cleared\n\n", TextColor.INFO.getActionTag());
            return;
        }

        System.out.printf("\n%s Clearing cache...\n", TextColor.INFO.getActionTag());
        try {
            FolderDeleter.delete(cachePath);
            System.out.printf("%s Cache cleared\n", TextColor.SUCCESS.getActionTag());
        } catch (IOException e) {
            System.out.printf("%s %s\n", TextColor.ERROR.getActionTag(), e.getMessage());
        } finally {
            System.out.println();
        }
    }

    // Helper functions
    private void inputConfig(boolean askNewFiles) {
        File startingFolder = Input.promptFile(
                Field.STARTING_FOLDER,
                askNewFiles ? null : ctrl.getStartingFolder(),
                sc);
        File testers = Input.promptFile(
                Field.TESTERS,
                askNewFiles ? null : ctrl.getTestersFolder(),
                sc);
        File scoresheet = Input.promptFile(
                Field.SCORESHEET,
                askNewFiles ? null : ctrl.getScoresheetFile(),
                sc);

        // First-run setup keeps the three prompts it always had and uses the defaults; 
        // only "Edit configuration paths" asks for the limits.
        long timeoutSeconds = ctrl.getTimeoutSeconds();
        long outputLimitBytes = ctrl.getOutputLimitBytes();
        if (!askNewFiles) {
            // Enter keeps the current value; anything else must be a whole number from 1 
            // to the maximum or the prompt repeats.
            timeoutSeconds = Input.promptPositiveLong("Grading timeout in seconds", timeoutSeconds,
                    ctrl.getMaxTimeoutSeconds(), sc);
            outputLimitBytes = Input.promptPositiveLong("Output limit in bytes", outputLimitBytes,
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
