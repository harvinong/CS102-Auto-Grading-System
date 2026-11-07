package autogradesys.grading;

import java.util.Objects;

/**
 * A durable, contextual description of one grading problem.
 *
 * <p>This is the single shape shared by the console output, the JSONL history file and the HTML
 * report. {@code student}, {@code submission} and {@code question} are null for run-level errors.</p>
 *
 * @param canContinue whether grading may safely proceed after this problem
 */
public record ErrorRecord(
        String stage,
        String category,
        String severity,
        String message,
        String guidance,
        String student,
        String submission,
        String question,
        boolean canContinue) {

    // Compact constructor: runs on every new ErrorRecord before its fields are assigned.
    public ErrorRecord {
        // Reject a missing stage up front; the console, JSONL and HTML writers all assume it exists.
        Objects.requireNonNull(stage, "stage");
        // Same for category, the short machine-friendly error code such as "compile" or "timeout".
        Objects.requireNonNull(category, "category");
        // Severity is needed for the console prefix and the history file.
        Objects.requireNonNull(severity, "severity");
        // Every record must say what went wrong.
        Objects.requireNonNull(message, "message");
        // Every record must also say how to fix it; the optional student, submission and question fields may stay null.
        Objects.requireNonNull(guidance, "guidance");
    }
}
