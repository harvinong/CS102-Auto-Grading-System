package autogradesys.validation;

import java.util.List;
import java.util.Optional;

import info.debatty.java.stringsimilarity.WeightedLevenshtein;

/**
 * Matches a submission's folder name to a registered student, tolerating small typos.
 * Moved from the team's original Validation.java.
 */

public final class UsernameMatcher {

    // A name only counts as a likely typo if its weighted distance is below this value.
    private static final double MAX_DISTANCE = 3.0;
    // 't' and 'r' are next to each other on a keyboard, so substituting them costs less.
    private static final double NEIGHBOUR_COST = 0.5;
    // For most characters, substituting one for another costs 1.0.
    private static final double SUBSTITUTION_COST = 1.0;

    private static final WeightedLevenshtein WL = new WeightedLevenshtein((c1, c2) -> {
        if ((c1 == 't' && c2 == 'r') || (c1 == 'r' && c2 == 't')) {
            return NEIGHBOUR_COST;
        }
        return SUBSTITUTION_COST;
    });

    private UsernameMatcher() {
    }

    /** The student whose username equals the name (ignoring case), if any. */
    public static Optional<Student> findExact(String name, List<Student> students) {
        return students.stream().filter(s -> s.username().equalsIgnoreCase(name)).findFirst();
    }

    /** The student whose username is closest to the name, if any is close enough to be a typo. */
    public static Optional<Student> findClosest(String name, List<Student> students) {
        Student closest = null;
        double closestDistance = MAX_DISTANCE;
        for (Student student : students) {
            double distance = WL.distance(name.toLowerCase(), student.username().toLowerCase());
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = student;
            }
        }
        return Optional.ofNullable(closest);
    }
}
