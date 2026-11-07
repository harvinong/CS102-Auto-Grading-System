package autogradesys.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import autogradesys.validation.ValidationEvent.Type;

/**
 * Works out which registered student a submission folder belongs to. A mistyped name is renamed
 * to the registered username once the instructor confirms it is a typo.
 */

public class UsernameResolver {

    // Folder names may start with a term prefix such as "2023-2024-"; the username is the rest.
    private static final Pattern FOLDER_NAME = Pattern.compile("^(?:\\d{4}-\\d{4}-)?(.+)$");

    /** The student a folder belongs to, and where that folder is now (it may have been renamed). */
    public record Identity(Student student, Path dir) {
    }

    private final List<Student> students;
    private final Predicate<String> confirm;

    /**
     * @param students The registered students from the scoresheet.
     * @param confirm  Asks the instructor a yes/no question; true means yes.
     */
    public UsernameResolver(List<Student> students, Predicate<String> confirm) {
        this.students = students;
        this.confirm = confirm;
    }

    /**
     * @param folder Name of the student's folder.
     * @param dir    The student's folder.
     * @param events Collects what happened.
     * @return The identity, or empty if the folder could not be matched to a registered student.
     */
    public Optional<Identity> resolve(String folder, Path dir, List<ValidationEvent> events) {
        String claimed = claimedUsername(folder);

        Optional<Student> exact = UsernameMatcher.findExact(claimed, students);
        if (exact.isPresent()) {
            return Optional.of(new Identity(exact.get(), dir));
        }

        Optional<Student> closest = UsernameMatcher.findClosest(claimed, students);
        if (closest.isEmpty()) {
            events.add(new ValidationEvent(folder, Type.USERNAME_UNREGISTERED, folder + "/",
                    "no registered student is close to '" + claimed + "'"));
            return Optional.empty();
        }

        Student match = closest.get();
        boolean isTypo = confirm.test("'" + match.username() + "' is the closest registered name to '"
                + claimed + "'. Is this a typo?");
        if (!isTypo) {
            events.add(new ValidationEvent(folder, Type.TYPO_DECLINED, folder + "/",
                    "'" + claimed + "' was not changed and is not matched to a student"));
            return Optional.empty();
        }
        return rename(folder, dir, match, events);
    }

    // Renames the folder to the registered username.
    private Optional<Identity> rename(String folder, Path dir, Student match, List<ValidationEvent> events) {
        Path renamed = dir.resolveSibling(match.username());
        try {
            // Files.move fails instead of overwriting when the target exists.
            Files.move(dir, renamed);
        } catch (IOException e) {
            events.add(new ValidationEvent(folder, Type.FIX_FAILED, folder + "/",
                    "could not rename it to " + match.username() + " (" + e.getMessage() + ")"));
            return Optional.empty();
        }
        events.add(new ValidationEvent(folder, Type.FOLDER_RENAMED, folder + "/",
                "renamed to " + match.username()));
        return Optional.of(new Identity(match, renamed));
    }

    // The username a folder name claims to be, with any term prefix removed.
    private static String claimedUsername(String folder) {
        Matcher matcher = FOLDER_NAME.matcher(folder);
        return matcher.matches() ? matcher.group(1) : folder;
    }
}
