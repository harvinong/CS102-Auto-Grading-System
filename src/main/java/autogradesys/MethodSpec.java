package autogradesys;


import java.util.List;

/**
 * Describes a student method together with its parameter types and test cases.
 * @param methodName the argument supplied to this operation.
 * @param paramTypes the argument supplied to this operation.
 * @param testCases the argument supplied to this operation.
 */
public record MethodSpec(String methodName, Class<?>[] paramTypes, List<TestCase> testCases) {}