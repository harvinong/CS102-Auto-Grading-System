package autogradesys.cli;

/**
 * Thrown by the {@link Input} prompts when the input ends (end of a piped script, or Ctrl+D / Ctrl+Z)
 * before an answer was typed, so the program can stop with a clear message instead of a stack trace.
 */
public class InputEndedException extends IllegalStateException {

    /**
     * Creates the exception for a prompt that found no more input.
     *
     * @param waitingFor what the prompt was asking for, e.g. "Starting Folder"; it becomes part of the message,
     *         which reads "Input ended while waiting for ..."
     */
    public InputEndedException(String waitingFor) {
        super("Input ended while waiting for " + waitingFor);
    }
}
