package autogradesys.io;
 
import java.util.LinkedHashMap;
 
/**
 * One student's row in the scoresheet.
 *
 * @param username the student username used for lookup.
 * @param marks the argument supplied to this operation.
 */
public record ScoresheetRecord(String username, LinkedHashMap<String, Integer> marks) {
 
    /**
     * Returns the sum of all marks recorded for the student.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
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