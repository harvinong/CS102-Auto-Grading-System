public class ShapeComparator {
    // Bug on purpose: waits for the two areas to differ, which never happens for equal areas.
    public static int compare(double a, double b) {
        while (a == b) {
        }
        return Double.compare(a, b);
    }
}
