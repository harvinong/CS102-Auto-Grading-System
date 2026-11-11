package autogradesys;

/**
 * Stores the marks awarded and the total marks available for a result.
 * @param scored the argument supplied to this operation.
 * @param total the argument supplied to this operation.
 */
public record Score(int scored, int total) {
    @Override
    /**
     * Returns this score as text.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public String toString() { return scored + "/" + total; }
}