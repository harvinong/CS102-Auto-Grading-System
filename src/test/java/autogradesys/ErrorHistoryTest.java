package autogradesys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import autogradesys.grading.ErrorHistory;
import autogradesys.grading.ErrorRecord;

/** Checks that error-history.jsonl lines are valid compact JSON that read back to the original text. */
class ErrorHistoryTest {

    @TempDir
    Path root;

    @Test
    void awkwardTextRoundTripsAsOneLinePerRecord() throws IOException {
        // Quotes, backslashes, a newline, a tab and non-ASCII text in the free-text fields.
        String message = "He said \"stop\" at C:\\temp\\x\nsecond line\ttab \u00e9 \u4e2d\u6587 \ud83d\ude00";
        String guidance = "Use \\ and \" carefully";
        ErrorRecord first = new ErrorRecord("submission", "compile", "error", message, guidance,
                "alice", "subs/alice", "q1", true);
        // Run-level record: the three optional fields are null and the flag is false.
        ErrorRecord second = new ErrorRecord("scoresheet", "scoresheet-read", "error", "plain", "fix",
                null, null, null, false);

        ErrorHistory history = new ErrorHistory(root.resolve("logs/history.jsonl"));
        history.append(first);
        // A second append must add a line, not overwrite the first.
        history.append(second);

        List<String> lines = Files.readAllLines(root.resolve("logs/history.jsonl"), StandardCharsets.UTF_8);
        // Newlines inside the message are escaped, so two records are exactly two lines.
        assertEquals(2, lines.size());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode one = mapper.readTree(lines.get(0));
        // Every field reads back identical to what was written.
        assertEquals(message, one.get("message").asText());
        assertEquals(guidance, one.get("guidance").asText());
        assertEquals("submission", one.get("stage").asText());
        assertEquals("compile", one.get("category").asText());
        assertEquals("error", one.get("severity").asText());
        assertEquals("alice", one.get("student").asText());
        assertEquals("subs/alice", one.get("submission").asText());
        assertEquals("q1", one.get("question").asText());
        assertTrue(one.get("canContinue").isBoolean() && one.get("canContinue").asBoolean());
        // The reader can rebuild the whole record from the line.
        assertEquals(first, mapper.treeToValue(one, ErrorRecord.class));

        JsonNode two = mapper.readTree(lines.get(1));
        // Null fields are present as JSON null and the boolean is a real false.
        assertTrue(two.get("student").isNull() && two.get("submission").isNull() && two.get("question").isNull());
        assertEquals(false, two.get("canContinue").asBoolean());
        assertEquals(second, mapper.treeToValue(two, ErrorRecord.class));
    }
}
