package autogradesys.grading;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Prints an actionable error and persists the identical structured record.
 *
 * <p>Recoverability belongs to each record, allowing the grader to continue
 * through submission issues while still stopping on fatal setup failures.</p>
 */
public final class ErrorReporter {
    private final ErrorHistory history;
    private final PrintStream output;
    private final List<ErrorRecord> records = new ArrayList<>();
    private boolean historyFailure;

    /** Creates a reporter backed by durable history and user-facing output. */
    public ErrorReporter(ErrorHistory history, PrintStream output) {
        this.history = history;
        this.output = output;
    }

    /**
     * Records, prints, and persists one diagnostic.
     *
     * @return the structured record retained for the session summary
     */
    public ErrorRecord error(String stage, String category, String severity, String message,
            String guidance, String student, String submission, String question,
            boolean canContinue) {
        ErrorRecord record = new ErrorRecord(stage, category, severity, message, guidance,
                student, submission, question, canContinue);
        records.add(record);
        print(record);
        try {
            history.append(record);
        } catch (IOException exception) {
            historyFailure = true;
            output.printf("[ERROR] history/write: Could not append error history at %s: %s%n",
                    history.path(), exception.getMessage());
            output.println("  guidance: Check the history path and filesystem permissions.");
        }
        return record;
    }

    /** Records an issue that should not prevent other submissions from grading. */
    public ErrorRecord recoverable(String stage, String category, String message, String guidance,
            String student, String submission, String question) {
        return error(stage, category, "error", message, guidance, student, submission, question, true);
    }

    /** Records a setup or infrastructure issue that makes the result untrustworthy. */
    public ErrorRecord fatal(String stage, String category, String message, String guidance) {
        return error(stage, category, "error", message, guidance, null, null, null, false);
    }

    /** Prints non-error progress information without adding history. */
    public void info(String message) {
        output.println(message);
    }

    /** Returns the diagnostics emitted during this grading session. */
    public List<ErrorRecord> records() {
        return Collections.unmodifiableList(records);
    }

    /** Returns whether grading encountered a fatal issue, including history failure. */
    public boolean hasFatalError() {
        return records.stream().anyMatch(record -> !record.canContinue()) || historyFailure;
    }

    private void print(ErrorRecord record) {
        output.printf("[%s] %s/%s: %s%n", record.severity().toUpperCase(), record.stage(),
                record.category(), record.message());
        if (record.student() != null || record.submission() != null || record.question() != null) {
            output.print("  context:");
            if (record.student() != null) {
                output.print(" student=" + record.student());
            }
            if (record.submission() != null) {
                output.print(" submission=" + record.submission());
            }
            if (record.question() != null) {
                output.print(" question=" + record.question());
            }
            output.println();
        }
        output.println("  guidance: " + record.guidance());
        output.println("  continuing: " + record.canContinue());
    }
}
