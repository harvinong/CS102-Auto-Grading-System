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
    // Perform this small step of the grading workflow.
    private final ErrorHistory history;
    // Perform this small step of the grading workflow.
    private final PrintStream output;
    // Prepare or update the value needed by the next grading step.
    private final List<ErrorRecord> records = new ArrayList<>();
    // Perform this small step of the grading workflow.
    private boolean historyFailure;

    /** Creates a reporter backed by durable history and user-facing output. */
    public ErrorReporter(ErrorHistory history, PrintStream output) {
        // Prepare or update the value needed by the next grading step.
        this.history = history;
        // Prepare or update the value needed by the next grading step.
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
                // Perform this small step of the grading workflow.
                student, submission, question, canContinue);
        // Perform this small step of the grading workflow.
        records.add(record);
        // Perform this small step of the grading workflow.
        print(record);
        // Perform this small step of the grading workflow.
        try {
            // Perform this small step of the grading workflow.
            history.append(record);
        } catch (IOException exception) {
            // Prepare or update the value needed by the next grading step.
            historyFailure = true;
            output.printf("[ERROR] history/write: Could not append error history at %s: %s%n",
                    // Perform this small step of the grading workflow.
                    history.path(), exception.getMessage());
            // Perform this small step of the grading workflow.
            output.println("  guidance: Check the history path and filesystem permissions.");
        }
        // Return this computed result to the caller.
        return record;
    }

    /** Records an issue that should not prevent other submissions from grading. */
    public ErrorRecord recoverable(String stage, String category, String message, String guidance,
            String student, String submission, String question) {
        // Return this computed result to the caller.
        return error(stage, category, "error", message, guidance, student, submission, question, true);
    }

    /** Records a setup or infrastructure issue that makes the result untrustworthy. */
    public ErrorRecord fatal(String stage, String category, String message, String guidance) {
        // Return this computed result to the caller.
        return error(stage, category, "error", message, guidance, null, null, null, false);
    }

    /** Prints non-error progress information without adding history. */
    public void info(String message) {
        // Perform this small step of the grading workflow.
        output.println(message);
    }

    /** Returns the diagnostics emitted during this grading session. */
    public List<ErrorRecord> records() {
        // Return this computed result to the caller.
        return Collections.unmodifiableList(records);
    }

    /** Returns whether grading encountered a fatal issue, including history failure. */
    public boolean hasFatalError() {
        // Return this computed result to the caller.
        return records.stream().anyMatch(record -> !record.canContinue()) || historyFailure;
    }

    private void print(ErrorRecord record) {
        output.printf("[%s] %s/%s: %s%n", record.severity().toUpperCase(), record.stage(),
                // Perform this small step of the grading workflow.
                record.category(), record.message());
        // Check this condition so grading follows the intended path.
        if (record.student() != null || record.submission() != null || record.question() != null) {
            // Perform this small step of the grading workflow.
            output.print("  context:");
            // Check this condition so grading follows the intended path.
            if (record.student() != null) {
                // Prepare or update the value needed by the next grading step.
                output.print(" student=" + record.student());
            }
            // Check this condition so grading follows the intended path.
            if (record.submission() != null) {
                // Prepare or update the value needed by the next grading step.
                output.print(" submission=" + record.submission());
            }
            // Check this condition so grading follows the intended path.
            if (record.question() != null) {
                // Prepare or update the value needed by the next grading step.
                output.print(" question=" + record.question());
            }
            // Perform this small step of the grading workflow.
            output.println();
        }
        // Perform this small step of the grading workflow.
        output.println("  guidance: " + record.guidance());
        // Perform this small step of the grading workflow.
        output.println("  continuing: " + record.canContinue());
    }
}
