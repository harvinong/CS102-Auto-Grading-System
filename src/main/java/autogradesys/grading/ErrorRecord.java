package autogradesys.grading;

import java.util.Objects;

/** A durable, contextual description of one grading problem. */
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
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(guidance, "guidance");
    }
}
