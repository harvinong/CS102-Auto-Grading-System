package autogradesys.menu;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Scanner;

import autogradesys.cli.Field;
import autogradesys.cli.Input;
import autogradesys.cli.TextColor;
import autogradesys.config.AppSettings;
import autogradesys.controller.GraderCtrl;
import autogradesys.controller.MainCtrl;
import autogradesys.grading.HtmlReportWriter;
import autogradesys.io.FolderDeleter;

public class MainMenu {

    private Scanner sc;
    private MainCtrl ctrl;

    /**
     * Initializes the main menu with its input scanner and configuration controller.
     */
    public MainMenu(Scanner sc, MainCtrl ctrl) {
        this.sc = sc;
        this.ctrl = ctrl;

        // Print big title
        System.out.println(TextColor.WARNING.apply(getText("./assets/title.txt")));

        // Check if config is initialized, then prompt
        boolean loadSuccess = ctrl.loadConfig();
        if (!loadSuccess) {
            // No config file initialized. User is new
            newView();
        }
    }

    /**
     * Reads the instructor’s menu choice and dispatches the selected action.
     */
    public void readOption() {
        int menuOption;
        do {
            displayMenu();
            menuOption = Input.promptOption(0, 5, sc);

            // Dispatch the choice: 1 and 2 manage configuration, 3 runs the grading flow, 0 quits.
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
                case 4:
                    viewRecentReport();
                    break;
                case 5:
                    clearCache();
                    break;
                case 0:
                    System.out.println("\nThank you for using!");
                    break;
            }
        } while (menuOption != 0);

    }

    // -- Displays --
    /**
     * Prints the title and context for the current menu.
     */
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
        System.out.println("5. Clear Student Grading Cache");
        System.out.println("0. Quit Application");
    }

    /** display the new view for new user, while setting the config files. */
    private void newView() {
        System.out.println(getText("./assets/welcome.md"));
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

    /**Menu option 3: the full grade-students flow. */
    // Set to public to be used non-interactively.
    /**
     * Runs grading after preparing, validating, and displaying the working submissions.
     */
    public void runGrader() {
        // clear the cache folder before grading, so the working copy is always fresh.
        clearCache();

        GraderMenu innerMenu = new GraderMenu(sc, new GraderCtrl(ctrl.getConfig()));
        innerMenu.setup();
        innerMenu.readOption();
    }

    /** Menu Option 4: open the recent html report */
    private void viewRecentReport() {
        // Announce the action first, so the instructor sees something even if opening fails.
        System.out.printf("%n%s Viewing recent report...%n", TextColor.INFO.getActionTag());
        // The report file is always written to the same place by option 3.
        File report = HtmlReportWriter.HTML_REPORT_PATH.toFile();
        try {
            // No report yet: tell the instructor what to do and go back to the menu.
            if (!report.isFile()) {
                System.out.printf("%s No report yet: run option 3 (Grade students) first.%n", TextColor.WARNING.getActionTag());
                return;
            }
            // No desktop (SSH, headless, server): show the path so the instructor can open the file by hand.
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                System.out.printf("%s Cannot open a browser here. Open this file yourself: %s%n",
                        TextColor.WARNING.getActionTag(), report.getAbsolutePath());
                return;
            }
            // Ask the operating system to open the file in the default browser.
            Desktop.getDesktop().open(report);
            // Confirm that it worked.
            System.out.printf("%s Opened on your browser!%n", TextColor.SUCCESS.getActionTag());
        // Any failure to open must return to the menu, never end the whole program.
        } catch (IOException | RuntimeException e) {
            // Show the reason in the error colour.
            System.out.printf("%s %s%n", TextColor.ERROR.getActionTag(), e.getMessage());
        } finally {
            // Blank line before the menu comes back.
            System.out.println();
        }
    }

    /** Menu Option 5: clear the cache folder */
    private void clearCache() {
        // Where the working copies of the submissions are kept.
        Path cachePath = AppSettings.load().getCacheDir();

        // Nothing on disk means there is nothing to delete.
        if (!cachePath.toFile().exists()) {
            System.out.printf("%n%s Cache has been cleared%n%n", TextColor.INFO.getActionTag());
            return;
        }

        // Announce the action before deleting.
        System.out.printf("%n%s Clearing cache...%n", TextColor.INFO.getActionTag());
        try {
            // Remove the cache folder and everything inside it.
            FolderDeleter.delete(cachePath);
            // Confirm the deletion.
            System.out.printf("%s Cache cleared%n", TextColor.SUCCESS.getActionTag());
        } catch (IOException e) {
            // Show why the folder could not be deleted.
            System.out.printf("%s %s%n", TextColor.ERROR.getActionTag(), e.getMessage());
        } finally {
            // Blank line before the menu comes back.
            System.out.println();
        }
    }

    // Helper functions
    /**
     * Prompts for configuration paths and persists the selected values.
     */
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

        long timeoutSeconds = ctrl.getTimeoutSeconds();
        long outputLimitBytes = ctrl.getOutputLimitBytes();

        // Enter keeps the current value; anything else must be a whole number from 1 
        // to the maximum or the prompt repeats.
        timeoutSeconds = Input.promptPositiveLong(
            "Grading timeout in seconds", 
            timeoutSeconds, 
            ctrl.getMaxTimeoutSeconds(), 
            sc
        );
        outputLimitBytes = Input.promptPositiveLong(
            "Output limit in bytes", 
            outputLimitBytes, 
            ctrl.getMaxOutputLimitBytes(), 
            sc
        );

        boolean saveSuccess = ctrl.saveConfig(startingFolder, testers, scoresheet, timeoutSeconds, outputLimitBytes);

        if (saveSuccess) {
            System.out.printf("%n%s Configuration saved!%n%n", TextColor.SUCCESS.getActionTag());
        } else {
            System.out.printf("%n%s Save unsuccessful%n%n", TextColor.ERROR.getActionTag());
        }
    }

    /**
     * Reads and returns the text resource used by the menu display.
     */
    private static String getText(String textPathString) {
        // Read as UTF-8 so the box-drawing title survives a platform default such as windows-1252 (JDK 17 on Windows).
        try (Scanner sc = new Scanner(new File(textPathString), StandardCharsets.UTF_8)) {
            // Collect the whole file here.
            StringBuilder result = new StringBuilder();
            // Copy line by line, always ending a line with \n.
            while (sc.hasNextLine()) {
                result.append(sc.nextLine());
                result.append('\n');
            }
            // Trim the blank lines at both ends.
            return result.toString().trim();
        // A missing or unreadable file gives a short notice instead of stopping the program.
        } catch (IOException e) {
            return String.format("[%s not found. You may reinstall this program]", textPathString);
        }
    }
}
