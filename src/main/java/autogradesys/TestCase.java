package autogradesys;

/**
 * Stores the label, arguments, expected value, and optional expected exception for one test.
 */
public record TestCase(String label, Object[] args, Object expected, String expectedException) {

    // use for object or value return
    /**
     * Defines a test that passes when the method returns the expected value.
     *
     * @param label the name shown for this test, preferably the call as text, e.g. {@code func(2,4)}
     * @param args the arguments to pass to the method; an {@code ObjSpec} stands for an object built from the student's own class
     * @param expected the value the method must return
     * @return a test case with no expected exception
     */
    public static TestCase expectValue(String label, Object[] args, Object expected) {
        return new TestCase(label, args, expected, null);
    }

    // use for testing exceptions
    /**
     * Defines a test that passes when the method throws the named exception.
     *
     * @param label the name shown for this test
     * @param args the arguments to pass to the method
     * @param expectedExceptionName the simple class name of the exception, e.g. {@code DataException}; the package is not compared
     * @return a test case with no expected return value
     */
    public static TestCase expectException(String label, Object[] args, String expectedExceptionName) {
        return new TestCase(label, args, null, expectedExceptionName);
    }
}
