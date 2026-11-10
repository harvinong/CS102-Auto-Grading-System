package autogradesys;

public record TestCase(String label, Object[] args, Object expected, String expectedException) {

    // use for object or value return
    public static TestCase expectValue(String label, Object[] args, Object expected) {
        return new TestCase(label, args, expected, null);
    }

    // use for testing exceptions
    public static TestCase expectException(String label, Object[] args, String expectedExceptionName) {
        return new TestCase(label, args, null, expectedExceptionName);
    }
}
