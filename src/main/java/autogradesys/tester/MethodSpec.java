package autogradesys.tester;


import java.util.List;

/**
 * Specifies what the method is expected to be.
 * 
 * @param methodName The name of the method
 * @param paramTypes The class type of each parameters.
 * @param testCases List of test cases provided.
 */
public record MethodSpec(String methodName, Class<?>[] paramTypes, List<TestCase> testCases) {}