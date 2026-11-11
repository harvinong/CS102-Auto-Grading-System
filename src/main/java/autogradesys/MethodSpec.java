package autogradesys;


import java.util.List;

/**
 * Creates a method spec instance.
 */
public record MethodSpec(String methodName, Class<?>[] paramTypes, List<TestCase> testCases) {}