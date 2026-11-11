package autogradesys.validation;

/**
 * One registered student, read from the scoresheet.
 *
 * @param username the value used by this method.
 * @param fullName the value used by this method.
 * @param email the value used by this method.
 */

public record Student(String username, String fullName, String email) {
}
