package autogradesys;

/**
 * Creates a test case instance.
 */
public record TestCase(String label, Object[] args, Object expected, String expectedException) {

    // use for object or value return
    /**
     * Expect value.
     */
    public static TestCase expectValue(String label, Object[] args, Object expected) {
        return new TestCase(label, args, expected, null);
    }

    // use for testing exceptions
    /**
     * Expect exception.
     */
    public static TestCase expectException(String label, Object[] args, String expectedExceptionName) {
        return new TestCase(label, args, null, expectedExceptionName);
    }
}
