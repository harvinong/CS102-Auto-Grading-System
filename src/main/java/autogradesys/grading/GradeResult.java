package autogradesys.grading;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Summary returned after all students and questions have had a chance to run.
 *
 * <p>The menu uses it to print totals and to feed {@link HtmlReportWriter}; {@code scores} maps each
 * student to a map of question name to 0 or 1.</p>
 */
public record GradeResult(int studentCount, int issueCount, boolean fatal, Map<String, Map<String, Integer>> scores) {
    // Compact constructor: makes a defensive, read-only copy before the fields are assigned.
    public GradeResult {
        // Build a new outer map; LinkedHashMap preserves the student order used by the CSV and report.
        Map<String, Map<String, Integer>> copy = new LinkedHashMap<>();
        // Copy each student's inner map too, wrapped as unmodifiable, so later edits to the grader's maps cannot change this result.
        scores.forEach((student, studentScores) -> copy.put(student,
                Collections.unmodifiableMap(new LinkedHashMap<>(studentScores))));
        // Replace the parameter with the read-only copy; the record stores this value.
        scores = Collections.unmodifiableMap(copy);
    }
}
