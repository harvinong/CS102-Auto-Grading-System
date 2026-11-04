package autogradesys.grading;

import java.util.Objects;

/** A durable, contextual description of one grading problem.
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

    public ErrorRecord {
        // Perform this small step of the grading workflow.
        Objects.requireNonNull(stage, "stage");
        // Perform this small step of the grading workflow.
        Objects.requireNonNull(category, "category");
        // Perform this small step of the grading workflow.
        Objects.requireNonNull(severity, "severity");
        // Perform this small step of the grading workflow.
        Objects.requireNonNull(message, "message");
        // Perform this small step of the grading workflow.
        Objects.requireNonNull(guidance, "guidance");
    }
}
