package autogradesys.validation;

import java.util.List;
import java.util.Optional;


/**
 * Matches a submission's folder name to a registered student.
 * Moved from the team's original Validation.java.
 */

public final class UsernameMatcher {

    // A name only counts as a likely typo if its weighted distance is below this value.
    private static final double MAX_DISTANCE = 3.0;
    // 't' and 'r' are next to each other on a keyboard, so substituting them costs less.
    private static final double NEIGHBOUR_COST = 0.5;
    // For most characters, substituting one for another costs 1.0.
    private static final double SUBSTITUTION_COST = 1.0;

    /**
     * Computes the normalized edit distance used to detect username typos.
     * @param a the argument supplied to this operation.
     * @param b the argument supplied to this operation.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    private static double distance(String a, String b) {
        double[] d = new double[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) d[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            double previous = d[0]; d[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                double old = d[j];
                double substitution = a.charAt(i - 1) == b.charAt(j - 1) ? 0
                        : ((a.charAt(i - 1) == 't' && b.charAt(j - 1) == 'r')
                        || (a.charAt(i - 1) == 'r' && b.charAt(j - 1) == 't') ? NEIGHBOUR_COST : SUBSTITUTION_COST);
                d[j] = Math.min(Math.min(d[j] + 1, d[j - 1] + 1), previous + substitution);
                previous = old;
            }
        }
        return d[b.length()];
    }

    /**
     * Initializes the stateless username matching utility.
     */
    private UsernameMatcher() {
    }

    /** The student whose username equals the name (ignoring case), if any.
     * @param name the name used for lookup or display.
     * @param students the registered student identities used for matching.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public static Optional<Student> findExact(String name, List<Student> students) {
        return students.stream().filter(s -> s.username().equalsIgnoreCase(name)).findFirst();
    }

    /** The student whose username is closest to the name, if any is close enough to be a typo.
     * @param name the name used for lookup or display.
     * @param students the registered student identities used for matching.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public static Optional<Student> findClosest(String name, List<Student> students) {
        Student closest = null;
        double closestDistance = MAX_DISTANCE;
        for (Student student : students) {
            double distance = distance(name.toLowerCase(), student.username().toLowerCase());
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = student;
            }
        }
        return Optional.ofNullable(closest);
    }
}
