package autogradesys;

/**
 * Stores the marks awarded and the total marks available for a result.
 */
public record Score(int scored, int total) {
    @Override
    /**
     * Shows the score in the usual fraction form.
     *
     * @return the marks scored and the total, e.g. {@code 2/3}
     */
    public String toString() { return scored + "/" + total; }
}