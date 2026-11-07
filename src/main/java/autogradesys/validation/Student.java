package autogradesys.validation;

/**
 * One registered student, read from the scoresheet.
 *
 * @param username Username without the scoresheet's leading '#' (e.g. ping.lee.2023).
 * @param fullName Name as written in the scoresheet (e.g. PING LEE).
 * @param email    The student's email address ("" if the scoresheet has none).
 */

public record Student(String username, String fullName, String email) {
}
