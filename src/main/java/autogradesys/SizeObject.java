package autogradesys;

import java.lang.instrument.Instrumentation;

public class SizeObject {
    private static volatile Instrumentation globalInstrumentation;

    // premain method to initialize the Instrumentation instance
    /**
     * Runs the application entry point.
     * @param agentArgs the argument supplied to this operation.
     * @param inst the argument supplied to this operation.
     */
    public static void premain(String agentArgs, Instrumentation inst) 
    {
        globalInstrumentation = inst;
    }

    // Method to get the size of an object
    /**
     * Returns the shallow JVM-reported size of the supplied object.
     * @param object the argument supplied to this operation.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public static long getObjectSize(Object object) {
        if (globalInstrumentation == null) {
            throw new IllegalStateException("Agent not initialized.");
        }
        return globalInstrumentation.getObjectSize(object);
    }
}
