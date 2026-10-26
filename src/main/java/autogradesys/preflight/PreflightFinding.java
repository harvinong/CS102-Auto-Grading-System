package autogradesys.preflight;

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
public record PreflightFinding(
        String student,
        Category category,
        Severity severity,
        String path,
        String message,
        String fix) {

    public enum Category {
        TEMPLATE_INVALID,
        SUBMISSIONS_INVALID,
        UNEXTRACTED_ARCHIVE,
        TEMPLATE_COPY,
        WRAPPED_FOLDER,
        MISNAMED,
        MISSING_QUESTION,
        MISSING_FILE,
        EMPTY_FILE,
        EXTRA_FILE,
        MISSING_RESOURCE,
        SYMLINK_NOT_FOLLOWED,
        UNREADABLE,
        IGNORED_JUNK
    }

    /** FATAL stops the whole check, ERROR needs attention, WARNING is worth a look, NOTE is information. */
    public enum Severity {
        FATAL, ERROR, WARNING, NOTE
    }
}
