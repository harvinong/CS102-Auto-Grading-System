package autogradesys;

import java.lang.instrument.Instrumentation;

/**
 * Measures the memory size of a returned object, so a huge return value can be rejected.
 * The size is only known when the JVM was started with this class as a Java agent (-javaagent);
 * otherwise {@link #getObjectSize} returns -1 and the size check is skipped.
 */
public class SizeObject {
    private static volatile Instrumentation globalInstrumentation;

    // premain method to initialize the Instrumentation instance
    public static void premain(String agentArgs, Instrumentation inst) 
    {
        globalInstrumentation = inst;
    }

    // Size of the object in bytes, or -1 when no agent is attached (or the object is null).
    public static long getObjectSize(Object object) {
        if (globalInstrumentation == null || object == null) {
            return -1;
        }
        return globalInstrumentation.getObjectSize(object);
    }
}
