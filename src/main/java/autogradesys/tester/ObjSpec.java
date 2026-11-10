package autogradesys.tester;

/**
 * Specify the object's class and constructor arguments
 * 
 * @param className the name of the class
 * @param constructorArgs the arguments used for instancing
 */
public record ObjSpec(String className, Object... constructorArgs) {}