package autogradesys.cli;

/**
 * Thrown by the {@link Input} prompts when the input ends (end of a piped script, or Ctrl+D / Ctrl+Z)
 * before an answer was typed, so the program can stop with a clear message instead of a stack trace.
 */
public class InputEndedException extends IllegalStateException {

    /**
     * @param waitingFor the argument supplied to this operation.
     */
    public InputEndedException(String waitingFor) {
        super("Input ended while waiting for " + waitingFor);
    }
}
