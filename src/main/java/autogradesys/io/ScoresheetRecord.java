package autogradesys.io;
 
import java.util.LinkedHashMap;
 
/**
 * One student's row in the scoresheet.
 *
 * @param username The student's username without the "#", e.g. chee.teo.2022.
 * @param marks    The student's marks per question, e.g. "Q1" -> 6, "Q2" -> 10. Empty if not graded.
 */
public record ScoresheetRecord(String username, LinkedHashMap<String, Integer> marks) {
 
    /**
     * Adds up the student's marks.
     *
     * @return the sum of all marks in {@code marks}; 0 when the student has no marks
     */
    public int finalScore() {
        // Running total of the marks.
        int total = 0;
        for (int mark : marks.values()) {
            total += mark;
        }
        return total;
    }
}