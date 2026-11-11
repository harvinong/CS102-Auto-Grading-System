package autogradesys;

/**
 * Stores the label, arguments, expected value, and optional expected exception for one test.
 */
public record TestCase(String label, Object[] args, Object expected, String expectedException) {

    // use for object or value return
    /**
     * Creates a test case that expects the supplied return value.
     */
    public static TestCase expectValue(String label, Object[] args, Object expected) {
        return new TestCase(label, args, expected, null);
    }

    // use for testing exceptions
    /**
     * Creates a test case that expects the named exception.
     */
    public static TestCase expectException(String label, Object[] args, String expectedExceptionName) {
        return new TestCase(label, args, null, expectedExceptionName);
    }
}
