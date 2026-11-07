package autogradesys;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.time.Duration;
import java.util.Scanner;

import autogradesys.grading.ErrorHistory;
import autogradesys.grading.ErrorReporter;
import autogradesys.grading.GradeResult;
import autogradesys.grading.HtmlReportWriter;
import autogradesys.grading.JavaGrader;
import autogradesys.input.CLI;
import autogradesys.input.Field;
// Feature import: the read-only submission check.
import autogradesys.preflight.PreflightReport;
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
                        "3. Scoresheet File\n");

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

    // Remove read-only attribute before deleting a file or directory
    private void makeWritable(Path path) throws IOException {
        DosFileAttributeView dosView = Files.getFileAttributeView(path, DosFileAttributeView.class);

        if (dosView != null) {
            dosView.setReadOnly(false);
        }
    }

    // Clear cache folder before running the grader
    private void clearCache() throws IOException {
        Path cacheDir = Path.of("cache");

        if (!Files.exists(cacheDir)) {
            return;
        }

        Files.walkFileTree(cacheDir, new SimpleFileVisitor<Path>() {

            // Delete individual files first
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {

                makeWritable(file);
                Files.delete(file);

                return FileVisitResult.CONTINUE;
            }

            // Delete directories after their contents
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                    throws IOException {

                if (exc != null) {
                    throw exc;
                }

                makeWritable(dir);
                Files.delete(dir);

                return FileVisitResult.CONTINUE;
            }
        });
    }

    public void runGrader() {
        // Freshly prompted so instructor can input folder of submissions.
        File submissions = CLI.promptFile(Field.SUBMISSIONS, null, sc);

        // Feature: work on a copy. Zips are extracted and folders copied into the cache
        // folder, and every step
        // below uses that copy, so the original submissions are never changed.
        // Clear previous cache before preparing a new working copy.
        try {
            clearCache();
            System.out.println("Previous cache cleared successfully.\n");
        } catch (IOException e) {
            System.out.println("\nFailed to clear cache!");

            System.out.println("Exception type: " + e.getClass().getName());
            System.out.println("Message: " + e.getMessage());

            if (e instanceof java.nio.file.FileSystemException) {
                java.nio.file.FileSystemException fse = (java.nio.file.FileSystemException) e;

                System.out.println("File: " + fse.getFile());
                System.out.println("Other file: " + fse.getOtherFile());
                System.out.println("Reason: " + fse.getReason());
            }

            e.printStackTrace();
            return;
        }

        CacheReport working = ctrl.prepareWorkingCopy(submissions);
        working.print(System.out);
        if (!working.usable()) {
            return;
        }
        Path workingDir = working.workingDir();

        // Feature: read-only layout check before grading. It ASSUMES the Starting
        // Folder is the starter
        // template (e.g. RenameToYourStudentID); this is the only place that assumption
        // is made.
        // The template path comes from the saved configuration, the folder to scan is
        // the working copy.
        PreflightReport preflight = checkWorkingCopy(workingDir);

        // Pause for manual changes. Skipped if the preflight could not scan the
        // submissions.
        if (!preflight.fatal() && !reviewWorkingCopy(workingDir)) {
            return;
        }

        System.out.println("\nRunning autograder!\n");

        ErrorReporter reporter = new ErrorReporter(
                new ErrorHistory(Path.of(".grader-error-history.jsonl")), System.out);
        GradeResult result = JavaGrader.grade(workingDir,
                ctrl.getTestersFolder().toPath(), ctrl.getScoresheetFile().toPath(),
                Duration.ofSeconds(2), reporter);
        try {
            Path report = Path.of(".grader-report.html");
            HtmlReportWriter.write(report, result, reporter.records());
            System.out.println("HTML report: " + report.toAbsolutePath().normalize());
        } catch (IOException exception) {
            System.out.println("Could not write HTML report: " + exception.getMessage());
        }
        System.out.printf("Completed: %d submission(s), %d reported issue(s).%n",
                result.studentCount(), result.issueCount());
    }

    // Returns true to start grading, false to go back to the main menu without grading.
    private boolean reviewWorkingCopy(Path workingDir) {
        while (true) {
            System.out.println("You can edit the files in the working copy by hand now:");
            System.out.println("  " + workingDir);
            System.out.println("1. Fix common mistakes automatically");
            System.out.println("2. Check the working copy again");
            System.out.println("3. Start grading");
            System.out.println("0. Cancel and return to the main menu");

            switch (CLI.promptOption(0, 3, sc)) {
                case 1:
                    fixWorkingCopy(workingDir);
                    break;
                case 2:
                    checkWorkingCopy(workingDir);
                    break;
                case 3:
                    return true;
                default:
                    return false;
            }
        }
    }

    private PreflightReport checkWorkingCopy(Path workingDir) {
        PreflightReport preflight = SubmissionPreflight.check(ctrl.getStartingFolder().toPath(), workingDir);
        preflight.print(System.out);
        return preflight;
    }

    private void fixWorkingCopy(Path workingDir) {
        // Check again first, without printing: the fixes follow the findings, which
        // must reflect any manual edits.
        PreflightReport preflight = SubmissionPreflight.check(ctrl.getStartingFolder().toPath(), workingDir);
        try {
            ctrl.validateSubmissions(workingDir, preflight, question -> CLI.promptYesNo(question, sc))
                    .print(System.out);
        } catch (IOException e) {
            System.out.println("Could not validate the submissions: " + e.getMessage() + "\n");
        }
        checkWorkingCopy(workingDir);
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
