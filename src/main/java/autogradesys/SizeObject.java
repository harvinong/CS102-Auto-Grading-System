package autogradesys;

import java.lang.instrument.Instrumentation;

public class SizeObject {
    private static volatile Instrumentation globalInstrumentation;

    // premain method to initialize the Instrumentation instance
    /**
     * Java agent hook, called by the JVM before {@code main} when the program is started with the agent. It keeps the instrumentation handle used by {@link #getObjectSize}.
     *
     * @param agentArgs the option string given to the agent on the command line; unused
     * @param inst the instrumentation handle supplied by the JVM
     */
    public static void premain(String agentArgs, Instrumentation inst) 
    {
        globalInstrumentation = inst;
    }

    // Method to get the size of an object
    /**
     * Measures an object with the JVM's instrumentation. The size is shallow: objects it refers to are not counted.
     *
     * @param object the object to measure
     * @return the object's size in bytes as reported by the JVM
     * @throws IllegalStateException if the agent was not started, so no instrumentation handle exists
     */
    public static long getObjectSize(Object object) {
        if (globalInstrumentation == null) {
            throw new IllegalStateException("Agent not initialized.");
        }
        return globalInstrumentation.getObjectSize(object);
    }
}
