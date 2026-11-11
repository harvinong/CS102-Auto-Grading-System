package autogradesys.grading;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
    // One shared mapper: it is thread-safe once configured and creating one per record would be wasteful. Default settings write compact JSON with raw UTF-8 text.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Location of the JSONL history file; every record from every session is appended to it.
    public static final Path DEFAULT_PATH = Path.of(".grader-error-history.jsonl");
    private final Path path;

    /**
     * Creates history storage at {@link #DEFAULT_PATH}. The file is only touched when {@link #append} is called.
     */
    public ErrorHistory() {
        // Remember the file location; the file itself is only touched by append().
        this.path = DEFAULT_PATH;
    }

    /**
     * Creates history storage at a chosen file. The file is only touched when {@link #append} is called.
     *
     * @param path where the JSON Lines history is kept; it and its folders are created on the first append
     */
    public ErrorHistory(Path path) {
        // Remember the file location; the file itself is only touched by append().
        this.path = path;
    }

    /**
     * Names the history file, for example in a message when writing to it fails.
     *
     * @return the path given to the constructor, or {@link #DEFAULT_PATH}
     */
    public Path path() {
        // Lets ErrorReporter name the file in a message when writing to it fails.
        return path;
    }

    // Called by ErrorReporter after each error; failures propagate so the reporter can mark the history as failed.
    /**
     * Appends one error as a single JSON line, creating the file and any missing folders on first use.
     * Earlier sessions' records are kept. Failures are passed on so the reporter can mark the history as failed.
     *
     * @param record the error to persist; its optional fields are written as JSON null
     * @throws IOException if the folders cannot be created or the file cannot be written
     */
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
        // Jackson builds the object and does all escaping (quotes, backslashes, control characters), replacing the old hand-written json() helper.
        ObjectNode object = MAPPER.createObjectNode();
        // Pipeline stage that failed, e.g. "submission" or "scoresheet".
        object.put("stage", record.stage());
        // Specific error kind within the stage.
        object.put("category", record.category());
        // Severity label: "error" for real problems, "note" for ignored junk recorded by ErrorReporter.ignored.
        object.put("severity", record.severity());
        // Human-readable description of what went wrong.
        object.put("message", record.message());
        // Suggested fix.
        object.put("guidance", record.guidance());
        // Student, or JSON null for run-level errors: put(String, String) stores a null value as JSON null.
        object.put("student", record.student());
        // Submission path, or null.
        object.put("submission", record.submission());
        // Question name, or null.
        object.put("question", record.question());
        // A real JSON boolean, not a string.
        object.put("canContinue", record.canContinue());
        // writeValueAsString is compact (no pretty printing, so no embedded newlines); the appended newline makes each record exactly one line (JSON Lines).
        String line = MAPPER.writeValueAsString(object) + "\n";
        // Write the line as UTF-8 so non-ASCII student names survive.
        Files.writeString(absolute, line, StandardCharsets.UTF_8,
                // CREATE makes the file if missing, and APPEND adds to the end instead of overwriting earlier sessions' records.
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }
}
