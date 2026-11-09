package autogradesys;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Scanner;

import autogradesys.cli.Field;
import autogradesys.cli.Input;
import autogradesys.cli.TextColor;
import autogradesys.grading.ErrorHistory;
import autogradesys.grading.ErrorReporter;
import autogradesys.grading.GradeResult;
import autogradesys.grading.HtmlReportWriter;
import autogradesys.grading.JavaGrader;
import autogradesys.preflight.PreflightReport;
import autogradesys.preflight.SubmissionPreflight;
import autogradesys.validation.CacheReport;

/**
 * The inner menu when running option 3: RunGrader from the main menu.
 * Runs the full grade-students flow, in this order:
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
public class GraderMenu {

    private Scanner sc;
    private GraderCtrl ctrl;

    // Filled during setup
    private File submissions;
    private ErrorReporter reporter;
    private CacheReport working;
    private PreflightReport preflight;
    private Path gradingPath;

    public GraderMenu(Scanner sc, GraderCtrl ctrl) {
        this.sc = sc;
        this.ctrl = ctrl;
    }

    /**<p> 
     * Process run after the main menu and before the grader (inner) menu
     * </p><p>
     * Process:
     * </p>
     * <ol>
        * <li>ask for the submissions folder and build the working copy (copy folders, extract zips);</li>
        * <li>run the read-only preflight check on the copy, recording ignored junk quietly and stopping if the setup is fatal;</li>
     * </ol>
     * @throws IllegalStateException if this method is called twice or more.
     */
    public void setup() {
        if (this.submissions != null) {
            throw new IllegalStateException("Setup can only be run once. Create a new GraderMenu instance to run.");
        }

        // Ask for the submissions folder every time, since it changes between assignments; 
        // keep the answer so the working copy is built from it.
        this.submissions = Input.promptFile(Field.SUBMISSIONS, null, sc);
        // One reporter owns the durable audit trail for preflight and grading diagnostics.
        this.reporter = new ErrorReporter(new ErrorHistory(), System.out);
        // Prepare a safe working copy: folders are copied and archives are extracted with zip-slip checks.
        this.working = ctrl.prepareWorkingCopy(submissions);
        // Show what the copy step did (new, reused, failed) before anything is checked or graded.
        this.working.print(System.out);

        // Junk the copy step skipped (.DS_Store, __MACOSX) is not printed above, so record each one as a quiet note instead.
        // peek runs once per matching event and count() then gives the total for the summary line further down.
        long cachedIgnored = this.working.events().stream()
                .filter(event -> event.type() == autogradesys.validation.ValidationEvent.Type.IGNORED_JUNK)
                .peek(event -> reporter.ignored("preflight", "ignored-junk", event.path() + " ignored",
                        "Remove this generated or OS metadata file if desired", event.path()))
                .count();
        // If the working copy could not be set up at all, there is nothing to check or grade, so go back to the menu.
        if (!this.working.usable()) return;
        // Preflight and grading both run on the copy, never on the original submissions.
        this.gradingPath = this.working.workingDir();
        
        // read-only layout check before grading. It ASSUMES the Starting Folder is the starter
        // template (e.g. RenameToYourStudentID); this is the only place that assumption is made.
        // SubmissionPreflight.check(...) compares every submission with the template and returns a report (it changes nothing);
        // the template path comes from the saved configuration, the submissions path from the working copy built above;
        // SubmissionPreflight.print(...) writes the report to the console.
        this.preflight = SubmissionPreflight.check(ctrl.config.startingFolder().toPath(), gradingPath);
        // Persist ignored clutter silently; PreflightReport intentionally omits it from console output.
        // This is the same quiet recording as above, but for junk found inside the student folders.
        long ignored = this.preflight.findings().stream()
                .filter(f -> f.category() == autogradesys.preflight.PreflightFinding.Category.IGNORED_JUNK)
                .peek(f -> reporter.ignored("preflight", "ignored-junk", f.path() + " ignored",
                        "Remove this generated or OS metadata file if desired", f.path()))
                .count();

        // Show the findings (per-student ready or needs-attention list) before any code is compiled.
        this.preflight.print(System.out);
        // Both sources of ignored files added together, so the instructor is told how many entries were left out of the report on screen.
        long ignoredTotal = cachedIgnored + ignored;
        // Only mention it when something was ignored, and say where the details went.
        if (ignoredTotal > 0) System.out.println(TextColor.INFO.getActionTag() + " " + ignoredTotal + " ignored files logged to the error report.");
        // A fatal finding means the template or submissions folder is unusable, so grading would only produce noise.
        if (this.preflight.fatal()) {
            // Use the same tag-and-message banner as a normal run so the output layout is the same either way.
            // Say plainly that grading did not start, so nobody mistakes the empty result for a pass.
            System.out.printf("%n%s Autograder not started because submission setup is invalid.%n%n", TextColor.ERROR.getActionTag());
            // Back to the menu without touching the scoresheet, history or report.
            return;
        }
    }

    /**
     * Open a menu for the user (instructor). 
     * Let the instructor fix the copy, re-check it, or start grading;
     */
    public void readOption() {
        int menuOption;
        do {
            displayMenu();
            menuOption = Input.promptOption(0, 3, sc);

            // Dispatch the choice: 1 fixes, 2 re-checks, 3 grades, 0 cancels operation;
            // 0 and 3 exits the loop once operations are complete.
            switch (menuOption) {
                case 1:
                    fixSubmissions();
                    break;
                case 2:
                    checkSubmissions();
                    break;
                case 3:
                    startGrading();
                    break;
                case 0:
                    break;
            }
        } while (menuOption != 3 && menuOption != 0);
    }

    // -- Displays --
    private void displayMenuTitle() {
        System.out.println("--- Grader Options ---");
    }
    
    /** Display the options and the "Enter option" prompt. */
    private void displayMenu() {
        // The four choices offered after each preflight report.
        displayMenuTitle();
        System.out.println("1. Fix common mistakes automatically");
        System.out.println("2. Check the working copy again");
        System.out.println("3. Start grading");
        System.out.println("0. Cancel and return to the main menu");
    }

    // -- Menu Actions --
    /** Automatically fix common mistakes, and then report preflight. */
    private void fixSubmissions() {
        // run the automatic fixes (headers, folder names, missing files), asking yes/no questions where a fix is a guess.
        try {
            ctrl.validateSubmissions(gradingPath, preflight, question -> Input.promptYesNo(question, sc)).print(System.out);
        // A fix that cannot read or write the working copy ends up here.
        } catch (IOException exception) {
            // Tell the instructor and fall through to the re-check, so they still see the current state.
            System.out.println(TextColor.ERROR.getActionTag() + " Could not validate the submissions: " + exception.getMessage());
        }
        // Check again after the fixes, because the earlier report no longer describes the copy.
        preflight = SubmissionPreflight.check(ctrl.config.startingFolder().toPath(), gradingPath);
        // Show the updated report.
        preflight.print(System.out);
    }
    
    /** Re-check only: useful after the instructor edited the working copy by hand. */
    private void checkSubmissions() {
        // Run the layout check again so the report reflects the edits.
        preflight = SubmissionPreflight.check(ctrl.config.startingFolder().toPath(), gradingPath);
        // Show the fresh report before offering the choices again.
        preflight.print(System.out);
    }

    /**<p>
     * The next logical step of the whole program, which is to batch-grade students' submissions.
     * </p>
     * <ol>
        * <li>grade every student with JavaGrader (compile, run the testers, compare output, update the CSV through ScoreRecorder);</li>
        * <li>write the HTML report from the result and the recorded errors, and print a summary.</li>
     * </ol>
     */
    private void startGrading() {
        // Separate the preflight report from the grading output with a blank line and a banner.
        System.out.printf("%n%s Running autograder!%n", TextColor.INFO.getActionTag());

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
                ctrl.config.testersFolder().toPath(), ctrl.config.scoresheetFile().toPath(),
                Duration.ofSeconds(ctrl.config.timeoutSeconds()), ctrl.config.outputLimitBytes(),
                java.util.Set.of(preflight.templateName()), reporter);
        // Writing the report can fail on disk problems; the grades are already saved, so only a message is shown.
        try {
            // The report lands in the current folder, overwritten on every run.
            // Render the scores and every recorded error into one HTML file.
            HtmlReportWriter.write(result, reporter.records());
            // Print the absolute path so the instructor can open the report.
            System.out.println("HTML report: " + HtmlReportWriter.HTML_REPORT_PATH.toAbsolutePath().normalize());
        } catch (java.io.IOException exception) {
            // Explain why no report was produced instead of crashing the menu.
            System.out.println("Could not write HTML report: " + exception.getMessage());
        }
        // Final one-line summary: students graded and total issues recorded (fatal or not).
        System.out.printf("%s Completed: %d submission(s), %d reported issue(s).%n%n",
                TextColor.INFO.getActionTag(), result.studentCount(), result.issueCount());
    }

}
