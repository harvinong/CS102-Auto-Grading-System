package autogradesys.grading;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import autogradesys.cli.TextColor;

/**
 * Central place where every grading problem is reported.
 *
 * <p>JavaGrader, CsvScoresheet and the menu call {@link #recoverable} or {@link #fatal}; each call
 * builds one {@link ErrorRecord}, keeps it in memory for the HTML report, prints it to the console,
 * and appends it to the {@link ErrorHistory} file.</p>
 *
 * <p>Prints an actionable error and persists the identical structured record.
 *
 * <p>Recoverability belongs to each record, allowing the grader to continue
 * through submission issues while still stopping on fatal setup failures.</p>
 */
public final class ErrorReporter {
    // Where each record is also saved on disk (the append-only JSONL file), so errors survive after the program exits.
    private final ErrorHistory history;
    // Where messages are shown to the person running the grader; System.out normally, a buffer in tests.
    private final PrintStream output;
    // Every record from this session, in order; it feeds the GradeResult counts and the HTML report.
    private final List<ErrorRecord> records = new ArrayList<>();
    // Set when saving to disk fails, so the run is later treated as untrustworthy even though no grading error caused it.
    private boolean historyFailure;

    /** Creates a reporter backed by durable history and user-facing output. */
    public ErrorReporter(ErrorHistory history, PrintStream output) {
        // Store the dependencies handed in by the caller; passing them in lets tests use a temp file and a fake stream.
        this.history = history;
        // Keep the console destination alongside the history file.
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
        // Bundle the nine fields into one immutable record, which is what all three outputs below share.
        ErrorRecord record = new ErrorRecord(stage, category, severity, message, guidance,
                student, submission, question, canContinue);
        // Remember the record in memory first, so it is counted even if printing or saving fails afterwards.
        records.add(record);
        // Show it on the console straight away so the operator sees problems while grading is still running.
        print(record);
        // Saving can fail (disk full, no permission), and that must not crash a grading run in progress.
        try {
            // Add one JSON line to the history file.
            history.append(record);
        // The disk write failed.
        } catch (IOException exception) {
            // Remember the failure so hasFatalError() reports it; losing the audit trail counts as fatal.
            historyFailure = true;
            // Tell the operator directly, because this failure cannot be written to the very history that just failed.
            printAppendErrorHistoryError(exception);
            // Same layout as print(): a guidance line telling them how to fix it.
            output.println("  guidance: Check the history path and filesystem permissions.");
        }
        // Give the caller the stored record in case it wants to inspect or reuse it.
        return record;
    }

    /** Records an issue that should not prevent other submissions from grading. */
    // Convenience wrapper for student or question problems.
    public ErrorRecord recoverable(String stage, String category, String message, String guidance,
            String student, String submission, String question) {
        // canContinue=true: this one student or question failed but the batch can safely carry on.
        return error(stage, category, "error", message, guidance, student, submission, question, true);
    }

    /** Records a setup or infrastructure issue that makes the result untrustworthy. */
    // Convenience wrapper for broken setup, e.g. a missing folder or unreadable CSV.
    public ErrorRecord fatal(String stage, String category, String message, String guidance) {
        // No student, submission or question applies, and canContinue=false marks the whole run as untrustworthy.
        return error(stage, category, "error", message, guidance, null, null, null, false);
    }

    /** Records an ignored input artifact without printing it to the console. */
    public ErrorRecord ignored(String stage, String category, String message, String guidance,
            String submission) {
        ErrorRecord record = new ErrorRecord(stage, category, "note", message, guidance,
                null, submission, null, true);
        records.add(record);
        try {
            history.append(record);
        } catch (IOException exception) {
            historyFailure = true;
            printAppendErrorHistoryError(exception);
        }
        return record;
    }

    /** Prints non-error progress information without adding history. */
    // Used for PASS lines and other progress text that is not a problem.
    public void info(String message) {
        // Console only: info lines are deliberately not saved to history or counted as issues.
        output.println(message);
    }

    /** Returns the diagnostics emitted during this grading session. */
    // Accessor used by JavaGrader for counts and by the menu for the HTML report.
    public List<ErrorRecord> records() {
        // Read-only view so outside code cannot add to or remove from the session's record.
        return Collections.unmodifiableList(records);
    }

    /** Returns whether grading encountered a fatal issue, including history failure. */
    // Asked at the end of grading to decide whether the result can be trusted.
    public boolean hasFatalError() {
        // Fatal if any record said it could not continue, or if saving history failed.
        return records.stream().anyMatch(record -> !record.canContinue()) || historyFailure;
    }

    // Formats one record as a few readable console lines.
    private void print(ErrorRecord record) {
        // Headline such as "[ERROR] scoresheet/scoresheet-read: message", with severity in capitals.
        output.printf(
            "%s %s/%s: %s%n", 
            TextColor.ERROR.apply("[" + record.severity().toUpperCase() + "]"), 
            record.stage(),
            record.category(), 
            record.message()
        );
        // Print a context line only when at least one of the three optional fields is present; fatal setup errors have none.
        if (record.student() != null || record.submission() != null || record.question() != null) {
            // Start the context line without a newline, because each present field is appended after it.
            output.print("  context:");
            // Show the student only when known.
            if (record.student() != null) {
                // Append the student's folder name.
                output.print(" student=" + record.student());
            }
            // Show the submission path only when known.
            if (record.submission() != null) {
                // Append the absolute path of the submission folder.
                output.print(" submission=" + record.submission());
            }
            // Show the question only when known.
            if (record.question() != null) {
                // Append the question folder name.
                output.print(" question=" + record.question());
            }
            // End the context line.
            output.println();
        }
        // Always print the suggested fix; this is what makes the message actionable.
        output.println("  guidance: " + record.guidance());
        // Tell the operator whether grading carried on (true) or the run is fatal (false).
        output.println("  continuing: " + record.canContinue());
    }

    /**
     * Tell the operator directly, because this failure cannot be written to the very history that just failed.
     * @param exception
     */
    private void printAppendErrorHistoryError(IOException exception) {
        output.printf("%s history/write: Could not append error history at %s: %s%n",
                    TextColor.ERROR.apply("[ERROR]"), history.path(), exception.getMessage());
    }
}
