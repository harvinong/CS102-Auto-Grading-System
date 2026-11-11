package autogradesys;

/**
 * Stores the label, arguments, expected value, and optional expected exception for one test.
 * @param label the value used by this method.
 * @param args the value used by this method.
 * @param expected the value used by this method.
 * @param expectedException the value used by this method.
 */
public record TestCase(String label, Object[] args, Object expected, String expectedException) {

    // use for object or value return
    /**
     * Creates a test case that expects the supplied return value.
     * @param label the value used by this method.
     * @param args the value used by this method.
     * @param expected the value used by this method.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    public static TestCase expectValue(String label, Object[] args, Object expected) {
        return new TestCase(label, args, expected, null);
    }

    // use for testing exceptions
    /**
     * Creates a test case that expects the named exception.
     * @param label the value used by this method.
     * @param args the value used by this method.
     * @param expectedExceptionName the value used by this method.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    public static TestCase expectException(String label, Object[] args, String expectedExceptionName) {
        return new TestCase(label, args, null, expectedExceptionName);
    }
}
