package autogradesys;


import java.util.List;

/**
 * Describes a student method together with its parameter types and test cases.
 */
public record MethodSpec(String methodName, Class<?>[] paramTypes, List<TestCase> testCases) {}