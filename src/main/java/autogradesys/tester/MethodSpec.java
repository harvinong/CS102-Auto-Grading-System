package autogradesys.tester;


import java.util.List;

public record MethodSpec(String methodName, Class<?>[] paramTypes, List<TestCase> testCases) {}