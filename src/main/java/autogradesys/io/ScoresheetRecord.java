package autogradesys.io;
 
import java.util.LinkedHashMap;
 
/**
 * One student's row in the scoresheet.
 *
 * @param username The student's username without the "#", e.g. chee.teo.2022.
 * @param marks    The student's marks per question, e.g. "Q1" -> 6, "Q2" -> 10. Empty if not graded.
 * @param username the value used by this method.
 * @param marks the value used by this method.
 */
public record ScoresheetRecord(String username, LinkedHashMap<String, Integer> marks) {
 
    /**
     * Returns the sum of all marks recorded for the student.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
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