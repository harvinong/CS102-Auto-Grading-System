package autogradesys.validation;

/**
 * One registered student, read from the scoresheet.
 *
 * @param username the student username used for lookup.
 * @param fullName the argument supplied to this operation.
 * @param email the argument supplied to this operation.
 */

public record Student(String username, String fullName, String email) {
}
