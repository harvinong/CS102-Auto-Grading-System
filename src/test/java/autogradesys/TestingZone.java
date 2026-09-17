package autogradesys;

// java lib imports
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;


public class TestingZone {
    
    @Test
    void question1a(ArrayList<String> expected, ArrayList<String> studentAnswer){
        assertEquals(expected, studentAnswer);
    }
}
