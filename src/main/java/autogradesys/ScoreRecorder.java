package autogradesys;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import autogradesys.grading.CsvScoresheet;

/** Compatibility facade that delegates scoresheet creation to the single CSV writer. */
public final class ScoreRecorder {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSSSSSSSS");
    private ScoreRecorder() { }

    public static Path recordScores(Map<String, int[]> results) throws IOException {
        Map<String, Map<String, Integer>> scores = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> entry : results.entrySet()) {
            String[] path = entry.getKey().split(" > ");
            if (path.length == 1) scores.computeIfAbsent(path[0], ignored -> new LinkedHashMap<>());
            else if (path.length == 3) scores.computeIfAbsent(path[0], ignored -> new LinkedHashMap<>())
                    .put(path[2], entry.getValue()[0]);
        }
        Path file = Path.of(LocalDateTime.now().format(TIMESTAMP) + ".csv");
        CsvScoresheet.create(file, scores);
        return file;
    }
}
