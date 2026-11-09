public class Q3 {
    // Returns the biggest value in a non-empty array.
    public static int largest(int[] values) {
        int best = values[0];
        for (int value : values) {
            if (value > best) {
                best = value;
            }
        }
        return best;
    }
}
