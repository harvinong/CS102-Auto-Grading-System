package autogradesys.grading;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Summary returned after all students and questions have had a chance to run. */
public record GradeResult(int studentCount, int issueCount, boolean fatal, Map<String, Map<String, Integer>> scores) {
    public GradeResult {
        // Prepare or update the value needed by the next grading step.
        Map<String, Map<String, Integer>> copy = new LinkedHashMap<>();
        scores.forEach((student, studentScores) -> copy.put(student,
                // Perform this small step of the grading workflow.
                Collections.unmodifiableMap(new LinkedHashMap<>(studentScores))));
        // Prepare or update the value needed by the next grading step.
        scores = Collections.unmodifiableMap(copy);
    }
}
