package autogradesys.tester;

/**
 * Test case for expected results.
 * 
 * @param label identification of this test case, usually a literal string showing how the method is called.
 * @param args arguments passed in to the method.
 * @param expected expected return value
 * @param expectedException expected thrown exception
 */
public record TestCase(String label, Object[] args, Object expected, String expectedException) {

    /**
     * Create a test case to test object or value return
     * 
     * @param label identification of this test case, usually a literal string showing how the method is called.
     * @param args arguments passed in to the method.
     * @param expected expected return value
     */ 
    public static TestCase expectValue(String label, Object[] args, Object expected) {
        return new TestCase(label, args, expected, null);
    }

    /**
     * Create a test case to test exceptions throw
     * 
     * @param label identification of this test case, usually a literal string showing how the method is called.
     * @param args arguments passed in to the method.
     * @param expectedExceptionName expected thrown exception
     */
    public static TestCase expectException(String label, Object[] args, String expectedExceptionName) {
        return new TestCase(label, args, null, expectedExceptionName);
    }
}
