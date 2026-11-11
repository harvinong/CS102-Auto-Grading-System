package autogradesys;


import java.util.List;

public record MethodSpec(String methodName, Class<?>[] paramTypes, List<TestCase> testCases) {}