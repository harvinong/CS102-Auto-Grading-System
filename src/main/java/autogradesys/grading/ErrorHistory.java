package autogradesys.grading;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Append-only JSON Lines storage for reported grading errors.
 *
 * <p>Appending preserves earlier sessions as an audit trail; records are not
 * rewritten when a later grading session reports another issue.</p>
 */
public final class ErrorHistory {
    // Perform this small step of the grading workflow.
    private final Path path;

    /** Creates history storage at the supplied JSONL path. */
    public ErrorHistory(Path path) {
        // Prepare or update the value needed by the next grading step.
        this.path = path;
    }

    /** Returns the configured history path for diagnostics and callers. */
    public Path path() {
        // Return this computed result to the caller.
        return path;
    }

    /**
     * Appends one complete JSON object followed by a newline.
     *
     * @param record diagnostic to persist
     * @throws IOException when the history cannot be created or written
     */
    public void append(ErrorRecord record) throws IOException {
        // Prepare or update the value needed by the next grading step.
        Path absolute = path.toAbsolutePath().normalize();
        // Prepare or update the value needed by the next grading step.
        Path parent = absolute.getParent();
        // Check this condition so grading follows the intended path.
        if (parent != null) {
            // Perform this small step of the grading workflow.
            Files.createDirectories(parent);
        }
        String line = "{"
                + "\"stage\":" + json(record.stage())
                + ",\"category\":" + json(record.category())
                + ",\"severity\":" + json(record.severity())
                + ",\"message\":" + json(record.message())
                + ",\"guidance\":" + json(record.guidance())
                + ",\"student\":" + optionalJson(record.student())
                + ",\"submission\":" + optionalJson(record.submission())
                + ",\"question\":" + optionalJson(record.question())
                + ",\"canContinue\":" + record.canContinue()
                // Perform this small step of the grading workflow.
                + "}\n";
        Files.writeString(absolute, line, StandardCharsets.UTF_8,
                // Perform this small step of the grading workflow.
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }

    private static String optionalJson(String value) {
        // Return this computed result to the caller.
        return value == null ? "null" : json(value);
    }

    private static String json(String value) {
        // Prepare or update the value needed by the next grading step.
        StringBuilder result = new StringBuilder(value.length() + 2).append('"');
        // Repeat this operation for each relevant grading item.
        for (int index = 0; index < value.length(); index++) {
            // Prepare or update the value needed by the next grading step.
            char character = value.charAt(index);
            // Perform this small step of the grading workflow.
            switch (character) {
                // Perform this small step of the grading workflow.
                case '"' -> result.append("\\\"");
                // Perform this small step of the grading workflow.
                case '\\' -> result.append("\\\\");
                // Perform this small step of the grading workflow.
                case '\b' -> result.append("\\b");
                // Perform this small step of the grading workflow.
                case '\f' -> result.append("\\f");
                // Perform this small step of the grading workflow.
                case '\n' -> result.append("\\n");
                // Perform this small step of the grading workflow.
                case '\r' -> result.append("\\r");
                // Perform this small step of the grading workflow.
                case '\t' -> result.append("\\t");
                default -> {
                    // Check this condition so grading follows the intended path.
                    if (character < 0x20) {
                        // Perform this small step of the grading workflow.
                        result.append(String.format("\\u%04x", (int) character));
                    } else {
                        // Perform this small step of the grading workflow.
                        result.append(character);
                    }
                }
            }
        }
        // Return this computed result to the caller.
        return result.append('"').toString();
    }
}
