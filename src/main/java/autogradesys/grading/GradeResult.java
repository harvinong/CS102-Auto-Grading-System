package autogradesys.grading;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Summary returned after all students and questions have had a chance to run.
 *
 * <p>The menu uses it to print totals and to feed {@link HtmlReportWriter}; {@code scores} maps each
 * student to a map of question name to the marks earned, and {@code availableMarks} maps each
 * question to the marks it is worth (its number of testers, or 1 for the {@code Main.java} layout).</p>
 * @param studentCount the value used by this method.
 * @param issueCount the value used by this method.
 * @param fatal the value used by this method.
 * @param scores the value used by this method.
 * @param availableMarks the value used by this method.
 */
public record GradeResult(int studentCount, int issueCount, boolean fatal, Map<String, Map<String, Integer>> scores,
        Map<String, Integer> availableMarks) {
    // Compact constructor: makes a defensive, read-only copy before the fields are assigned.
    public GradeResult {
        // Build a new outer map; LinkedHashMap preserves the student order used by the CSV and report.
        Map<String, Map<String, Integer>> copy = new LinkedHashMap<>();
        // Copy each student's inner map too, wrapped as unmodifiable, so later edits to the grader's maps cannot change this result.
        scores.forEach((student, studentScores) -> copy.put(student,
                Collections.unmodifiableMap(new LinkedHashMap<>(studentScores))));
        // Replace the parameter with the read-only copy; the record stores this value.
        scores = Collections.unmodifiableMap(copy);
        // Same read-only copy for the marks each question is worth.
        availableMarks = Collections.unmodifiableMap(new LinkedHashMap<>(availableMarks));
    }

    /** Result where every question is worth one mark, as in the {@code Main.java} and {@code expected.txt} layout.
     * @param studentCount the value used by this method.
     * @param issueCount the value used by this method.
     * @param fatal the value used by this method.
     * @param scores the value used by this method.
     */
    public GradeResult(int studentCount, int issueCount, boolean fatal, Map<String, Map<String, Integer>> scores) {
        // Collect every question that appears in any student's scores and make each worth 1.
        this(studentCount, issueCount, fatal, scores, oneMarkEach(scores));
    }

    /** Marks the question is worth; a question this result does not know is treated as worth one mark.
     * @param question the value used by this method.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    public int marksFor(String question) {
        // getOrDefault covers results built without an entry for the question.
        return availableMarks.getOrDefault(question, 1);
    }

    // Builds question name -> 1 for every question named in the scores, in first-seen order.
    /**
     * Creates a score map assigning one available mark to each question.
     * @param scores the value used by this method.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    private static Map<String, Integer> oneMarkEach(Map<String, Map<String, Integer>> scores) {
        // LinkedHashMap keeps the order the questions were first seen.
        Map<String, Integer> marks = new LinkedHashMap<>();
        // Every student's question names, merged; putIfAbsent keeps the first entry.
        scores.values().forEach(studentScores -> studentScores.keySet()
                .forEach(question -> marks.putIfAbsent(question, 1)));
        // Hand back the question -> 1 table that the four-argument constructor passes on as availableMarks.
        return marks;
    }
}
