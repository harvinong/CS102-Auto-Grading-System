package autogradesys.grading;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Append-only JSON Lines storage for reported grading errors. */
public final class ErrorHistory {
    private final Path path;

    public ErrorHistory(Path path) {
        this.path = path;
    }

    public Path path() {
        return path;
    }

    public void append(ErrorRecord record) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) {
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
                + "}\n";
        Files.writeString(absolute, line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }

    private static String optionalJson(String value) {
        return value == null ? "null" : json(value);
    }

    private static String json(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 0x20) {
                        result.append(String.format("\\u%04x", (int) character));
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        return result.append('"').toString();
    }
}
