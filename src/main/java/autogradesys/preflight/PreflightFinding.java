package autogradesys.preflight;

import autogradesys.cli.TextColor;

/**
 * One thing the preflight noticed about a submission.
 *
 * @param student  The submission folder (or archive) name; empty for findings about the
 *                 submissions folder or the template as a whole.
 * @param category What kind of problem this is.
 * @param severity How much it matters.
 * @param path     Where it was found, relative to the submissions folder (or the template name).
 * @param message  What is wrong.
 * @param fix      What the instructor or student can do about it; empty when nothing is needed.
 */
// A record is an immutable value holder: Java generates the constructor, getters and equals for us.
// Describes one submission or setup defect found before compilation begins.
public record PreflightFinding(
        // Which submission this belongs to ("" means it is about the whole run, not one student).
        String student,
        // The kind of problem, from the Category list below.
        Category category,
        // How serious it is, from the Severity list below.
        Severity severity,
        // Where the problem is, shown relative to the submissions folder.
        String path,
        // A short description of what is wrong.
        String message,
        // A short instruction for putting it right ("" when nothing needs doing).
        String fix) {

    // Every kind of thing the preflight can report. The names are what the console prints in [BRACKETS].
    public enum Category {
        // The Starting Folder is missing, unreadable, empty of questions, or one level too high.
        TEMPLATE_INVALID,
        // The Submissions Folder is missing or unreadable.
        SUBMISSIONS_INVALID,
        // A .zip that has not been extracted into a folder yet.
        UNEXTRACTED_ARCHIVE,
        // A .zip that sits next to the folder it was already extracted into (not a separate student).
        ARCHIVE_ALREADY_EXTRACTED,
        // A student folder that has the same name as the starter template.
        TEMPLATE_COPY,
        // The real work is inside one extra folder (e.g. RenameToYourStudentID/ or a student number).
        WRAPPED_FOLDER,
        // A folder or file whose name differs from the template only by upper/lower case.
        MISNAMED,
        // A question folder from the template is absent.
        MISSING_QUESTION,
        // A required .java file is absent.
        MISSING_FILE,
        // A required .java file exists but has no real content.
        EMPTY_FILE,
        // A .java file or folder that the template does not have.
        EXTRA_FILE,
        // A non-Java file from the template (e.g. persons.txt) is absent.
        MISSING_RESOURCE,
        // A symbolic link, which is reported but never followed.
        SYMLINK_NOT_FOLLOWED,
        // A folder that could not be read (permissions and similar).
        UNREADABLE,
        // Harmless clutter such as .DS_Store, __MACOSX or .class files.
        IGNORED_JUNK,
        // A folder name that is not a registered username but is close to one (probably a typo).
        USERNAME_TYPO, MISSING_HEADER, INCOMPLETE_HEADER
    }

    /** FATAL stops the whole check, ERROR needs attention, WARNING is worth a look, NOTE is information. */
    public enum Severity {
        // Stops the whole check: nothing is scanned.
        FATAL,
        // Makes the student "need attention".
        ERROR,
        // Worth a look but the student stays "ready".
        WARNING,
        // Information only.
        NOTE
    }

    /**<p>
     * Get a tag like "  [CATEGORY] " (padded with spaces)
     * applied with TextColor based on its severity:
     * </p>
     * - NOTE is applied with TextColor.INFO;
     * - WARNING is applied with TextColor.WARNING;
     * - ERROR and FATAL is applied with TextColor.ERROR;
     */
    public String getTagWithColor() {
        TextColor color;
        switch (this.severity()) {
            case NOTE:
                color = TextColor.INFO;
                break;
            case WARNING:
                color = TextColor.WARNING;
                break;
            default:
                color = TextColor.ERROR;
                break;
        }
        return color.getActionTag("" + this.category());
    }
}
