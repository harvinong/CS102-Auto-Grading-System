package autogradesys;


import java.util.List;

/**
 * Describes a student method together with its parameter types and test cases.
 * @param methodName the value used by this method.
 * @param paramTypes the value used by this method.
 * @param testCases the value used by this method.
 */
public record MethodSpec(String methodName, Class<?>[] paramTypes, List<TestCase> testCases) {}