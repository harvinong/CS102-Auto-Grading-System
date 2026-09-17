package autogradesys;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
/**
 * Unit test for simple App.
 */
public class AppTest {
    
    
    
    /**
     * Rigorous Test :-)
     * 
     */
    @Test
    public void shouldAnswerWithTrue() {
        assertTrue(true);
    }
    public static void main(String[] args) {
        //unpacker("C:\\Users\\mista\\Desktop\\serverproj\\Project\\student-submission\\2023-2024-chee.teo.2022.zip",new File("").getAbsolutePath().concat("cheeteo"));   
        ArrayList<String> expected = new ArrayList<>(Arrays.asList("alice", "ben", "cathy"));
        ArrayList<String> input = expected;
        TestingZone tz = new TestingZone();
        Q1a.getIsogramWords(input);
    }
}
