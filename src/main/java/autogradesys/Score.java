package autogradesys;

/**
 * Stores the marks awarded and the total marks available for a result.
 * @param scored the value used by this method.
 * @param total the value used by this method.
 */
public record Score(int scored, int total) {
    @Override
    /**
     * Returns this score as text.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    public String toString() { return scored + "/" + total; }
}