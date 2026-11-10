package autogradesys.tester;

import java.lang.instrument.Instrumentation;

public class SizeObject {
    private static volatile Instrumentation globalInstrumentation;

    // premain method to initialize the Instrumentation instance
    public static void premain(String agentArgs, Instrumentation inst) 
    {
        globalInstrumentation = inst;
    }

    // Method to get the size of an object
    public static long getObjectSize(Object object) {
        if (globalInstrumentation == null) {
            throw new IllegalStateException("Agent not initialized.");
        }
        return globalInstrumentation.getObjectSize(object);
    }
}
