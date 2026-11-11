package autogradesys;

/**
 * Stores the label, arguments, expected value, and optional expected exception for one test.
 * @param label the prompt or display label.
 * @param args the arguments supplied to the invoked method.
 * @param expected the argument supplied to this operation.
 * @param expectedException the argument supplied to this operation.
 */
public record TestCase(String label, Object[] args, Object expected, String expectedException) {

    // use for object or value return
    /**
     * Creates a test case that expects the supplied return value.
     * @param label the prompt or display label.
     * @param args the arguments supplied to the invoked method.
     * @param expected the argument supplied to this operation.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public static TestCase expectValue(String label, Object[] args, Object expected) {
        return new TestCase(label, args, expected, null);
    }

    // use for testing exceptions
    /**
     * Creates a test case that expects the named exception.
     * @param label the prompt or display label.
     * @param args the arguments supplied to the invoked method.
     * @param expectedExceptionName the argument supplied to this operation.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public static TestCase expectException(String label, Object[] args, String expectedExceptionName) {
        return new TestCase(label, args, null, expectedExceptionName);
    }
}
