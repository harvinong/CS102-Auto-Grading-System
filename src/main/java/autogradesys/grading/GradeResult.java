package autogradesys.grading;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Summary returned after all students and questions have had a chance to run. */
public record GradeResult(int studentCount, int issueCount, boolean fatal, Map<String, Map<String, Integer>> scores) {
    public GradeResult {
        Map<String, Map<String, Integer>> copy = new LinkedHashMap<>();
        scores.forEach((student, studentScores) -> copy.put(student,
                Collections.unmodifiableMap(new LinkedHashMap<>(studentScores))));
        scores = Collections.unmodifiableMap(copy);
    }
}
