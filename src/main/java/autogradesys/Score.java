package autogradesys;

/**
 * Creates a score instance.
 */
public record Score(int scored, int total) {
    @Override
    /**
     * Returns this score as text.
     */
    public String toString() { return scored + "/" + total; }
}