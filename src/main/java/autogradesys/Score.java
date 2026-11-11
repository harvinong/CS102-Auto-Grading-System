package autogradesys;

/**
 * Stores the marks awarded and the total marks available for a result.
 */
public record Score(int scored, int total) {
    @Override
    /**
     * Returns this score as text.
     */
    public String toString() { return scored + "/" + total; }
}