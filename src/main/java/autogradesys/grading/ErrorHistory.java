package autogradesys.grading;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Append-only JSON Lines storage for reported grading errors.
 *
 * <p>Each call from {@link ErrorReporter} adds one line such as
 * {@code {"stage":"submission","category":"compile",...}} to a single file, so the file grows
 * across grading sessions and can be read later by any tool that understands JSON Lines.</p>
 *
 * <p>Appending preserves earlier sessions as an audit trail; records are not
 * rewritten when a later grading session reports another issue.</p>
 */
public final class ErrorHistory {
    // Location of the JSONL history file; every record from every session is appended to it.
    private final Path path;

    /** Creates history storage at the supplied JSONL path. */
    public ErrorHistory(Path path) {
        // Remember the file location; the file itself is only touched by append().
        this.path = path;
    }

    /** Returns the configured history path for diagnostics and callers. */
    public Path path() {
        // Lets ErrorReporter name the file in a message when writing to it fails.
        return path;
    }

    /**
     * Appends one complete JSON object followed by a newline.
     *
     * @param record diagnostic to persist
     * @throws IOException when the history cannot be created or written
     */
    // Called by ErrorReporter after each error; failures propagate so the reporter can mark the history as failed.
    public void append(ErrorRecord record) throws IOException {
        // Resolve to a clean absolute path so relative inputs like "logs/../history.jsonl" behave predictably.
        Path absolute = path.toAbsolutePath().normalize();
        // Folder that must exist before the file can be created.
        Path parent = absolute.getParent();
        // A path at the filesystem root has no parent, so guard against null.
        if (parent != null) {
            // Create the folder (and any missing ancestors) on the first run; does nothing if it already exists.
            Files.createDirectories(parent);
        }
        // Build the JSON object by hand, which avoids a library dependency; strings go through json() for escaping.
        String line = "{"
                // Pipeline stage that failed, e.g. "submission" or "scoresheet".
                + "\"stage\":" + json(record.stage())
                // Specific error kind within the stage.
                + ",\"category\":" + json(record.category())
                // Severity label, currently "error".
                + ",\"severity\":" + json(record.severity())
                // Human-readable description of what went wrong.
                + ",\"message\":" + json(record.message())
                // Suggested fix.
                + ",\"guidance\":" + json(record.guidance())
                // Student, or the JSON literal null for run-level errors.
                + ",\"student\":" + optionalJson(record.student())
                // Submission path, or null.
                + ",\"submission\":" + optionalJson(record.submission())
                // Question name, or null.
                + ",\"question\":" + optionalJson(record.question())
                // A boolean needs no quotes in JSON, so it is appended directly.
                + ",\"canContinue\":" + record.canContinue()
                // Close the object and add a newline, so each record occupies exactly one line (JSON Lines).
                + "}\n";
        // Write the line as UTF-8 so non-ASCII student names survive.
        Files.writeString(absolute, line, StandardCharsets.UTF_8,
                // CREATE makes the file if missing, and APPEND adds to the end instead of overwriting earlier sessions' records.
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }

    // Helper for the nullable fields.
    private static String optionalJson(String value) {
        // Emit the bare JSON null, or the quoted and escaped string.
        return value == null ? "null" : json(value);
    }

    // Turns a Java string into a quoted, escaped JSON string literal.
    private static String json(String value) {
        // Start with the opening quote; the initial capacity covers the text plus both quotes.
        StringBuilder result = new StringBuilder(value.length() + 2).append('"');
        // Examine each character, since some must be escaped.
        for (int index = 0; index < value.length(); index++) {
            // Current character under inspection.
            char character = value.charAt(index);
            // Handle the characters that JSON requires to be escaped.
            switch (character) {
                // A double quote would end the string early.
                case '"' -> result.append("\\\"");
                // A backslash would start an escape sequence.
                case '\\' -> result.append("\\\\");
                // Control characters get their short JSON escape names.
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                // Everything else.
                default -> {
                    // Remaining control characters below space are illegal raw in JSON.
                    if (character < 0x20) {
                        // Write them as a four-digit hex \\u escape.
                        result.append(String.format("\\u%04x", (int) character));
                    } else {
                        // Ordinary characters are copied unchanged.
                        result.append(character);
                    }
                }
            }
        }
        // Add the closing quote and produce the finished literal.
        return result.append('"').toString();
    }
}
