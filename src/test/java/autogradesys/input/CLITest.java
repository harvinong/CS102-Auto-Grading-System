package autogradesys.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

/** Checks that the positive-number prompt re-asks on bad input like the path prompts do. */
class CLITest {

    // Feeds the typed lines to CLI.promptPositiveLong with default 2 and maximum 3600, and returns the value plus what was printed.
    private record Run(long value, String printed) {
    }

    private static Run prompt(String typed) {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        // Capture the prompt text, since the method prints directly to System.out.
        System.setOut(new PrintStream(captured));
        try {
            long value = CLI.promptPositiveLong("Grading timeout in seconds", 2, 3600, new Scanner(typed));
            return new Run(value, captured.toString());
        } finally {
            System.setOut(original);
        }
    }

    @Test
    void validNumberIsReturnedWithoutReprompting() {
        Run run = prompt("5\n");
        assertEquals(5, run.value());
        assertEquals(false, run.printed().contains("Invalid input"));
    }

    @Test
    void blankLineKeepsTheCurrentValue() {
        assertEquals(2, prompt("\n").value());
    }

    @Test
    void badInputIsRejectedAndAskedAgain() {
        // Text, decimal, zero and negative are each refused before the final valid 8.
        Run run = prompt("abc\n1.5\n0\n-3\n8\n");
        assertEquals(8, run.value());
        assertEquals(4, run.printed().split("Invalid input", -1).length - 1);
    }

    @Test
    void valueAboveTheMaximumIsRejectedAndAskedAgain() {
        // 9999999999999999 seconds used to be saved and then crash grading with "long overflow".
        Run run = prompt("9999999999999999\n3601\n3600\n");
        // The maximum itself is accepted; the two larger values are each refused with the range named.
        assertEquals(3600, run.value());
        assertEquals(2, run.printed().split("must be a whole number from 1 to 3600", -1).length - 1);
    }

    @Test
    void endedInputFailsInsteadOfLooping() {
        assertThrows(IllegalStateException.class, () -> prompt("abc\n"));
    }
}
