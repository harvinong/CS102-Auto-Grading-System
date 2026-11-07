package autogradesys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import autogradesys.grading.ErrorHistory;
import autogradesys.grading.ErrorReporter;

/** Checks the ScoreRecorder entry point that live grading (JavaGrader) uses to save marks. */
class ScoreRecorderTest {
    @TempDir
    Path dir;

    @Test
    void liveGradingEntryPointWritesMarksIntoTheScoresheet() throws IOException {
        // A scoresheet in the documented format: a student column and one column per question.
        Path scoresheet = Files.writeString(dir.resolve("scores.csv"), "student,Q1,Q2\nalice,,\nbob,,\n");
        ErrorReporter reporter = new ErrorReporter(new ErrorHistory(dir.resolve("history.jsonl")),
                new PrintStream(new ByteArrayOutputStream()));
        // Marks per student and question, as JavaGrader collects them.
        Map<String, Map<String, Integer>> scores = new LinkedHashMap<>();
        scores.put("alice", Map.of("Q1", 2, "Q2", 1));
        scores.put("bob", Map.of("Q1", 0, "Q2", 2));

        assertTrue(ScoreRecorder.recordScores(scoresheet, scores, reporter));

        // Each mark lands in its student's row and question's column, and nothing went wrong.
        assertEquals("student,Q1,Q2\nalice,2,1\nbob,0,2\n", Files.readString(scoresheet));
        assertTrue(reporter.records().isEmpty(), reporter.records().toString());
    }
}
