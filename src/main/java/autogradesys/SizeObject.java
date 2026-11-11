package autogradesys;

import java.lang.instrument.Instrumentation;

public class SizeObject {
    private static volatile Instrumentation globalInstrumentation;

    // premain method to initialize the Instrumentation instance
    /**
     * Runs the application entry point.
     * @param agentArgs the value used by this method.
     * @param inst the value used by this method.
     */
    public static void premain(String agentArgs, Instrumentation inst) 
    {
        globalInstrumentation = inst;
    }

    // Method to get the size of an object
    /**
     * Returns the shallow JVM-reported size of the supplied object.
     * @param object the value used by this method.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    public static long getObjectSize(Object object) {
        if (globalInstrumentation == null) {
            throw new IllegalStateException("Agent not initialized.");
        }
        return globalInstrumentation.getObjectSize(object);
    }
}
