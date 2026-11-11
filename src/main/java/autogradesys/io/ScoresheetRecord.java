package autogradesys.io;
 
import java.util.LinkedHashMap;
 
/**
 * One student's row in the scoresheet.
 *
 * @param username The student's username, e.g. chee.teo.2022.
 * @param marks    The student's marks per sub-question, in column order, e.g. "Q1a" -> 3, "Q1b" -> 2.
 */
public record ScoresheetRecord(String username, LinkedHashMap<String, Integer> marks) {
 
    // Input: nothing (uses this record's marks).
    // Function: adds up the marks of every sub-question.
    // Output: the total, which is what goes in the final_score column.
    /**
     * Returns the sum of all marks recorded for the student.
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