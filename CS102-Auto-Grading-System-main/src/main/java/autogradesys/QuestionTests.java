package autogradesys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


public class QuestionTests {

    public static Map<String, MethodSpec> getSPECS_BY_CLASS() {
        return SPECS_BY_CLASS;
    }
    

    private static final Map<String, MethodSpec> SPECS_BY_CLASS = Map.of(

    /*
            "NAME OF .JAVA FILE", new MethodSpec(
                    "METHOD NAME TO TEST",
                    new Class<?>[]{TYPES OF PARAM FOR METHOD.class, ... ...},
                    List.of(
                        TestCase.expectValue (for non exception testing) (label, new Object[]{ARGUMENTS GO HERE}, EXPECTD REUSLT)
                        TestCase.expectException (for exception results) (label, new Object[]{ARGUMENTS GO HERE}, EXPECTD ExcepTION)
                    )
            )
    
    */
            "Q1a", new MethodSpec(
                    "getIsogramWords",
                    new Class<?>[]{ ArrayList.class},
                    List.of(
                            TestCase.expectValue("getIsogramWords(alice,ben,cathy)",   new Object[]{new ArrayList<>(Arrays.asList("alice","ben","cathy"))},  new ArrayList<>(Arrays.asList("alice","ben","cathy"))),
                            TestCase.expectValue("getIsogramWords(ellen, alan, zoe)", new Object[]{new ArrayList<>(Arrays.asList("ellen","alan","zoe"))}, new ArrayList<>(Arrays.asList("zoe"))),
                            TestCase.expectValue("getIsogramWords(Elle,alan,Daniel,Canny)", new Object[]{new ArrayList<>(Arrays.asList("Elle","alan","Daniel","Canny"))}, new ArrayList<>(Arrays.asList("Daniel")))
                    )
            ),

            "Q1b", new MethodSpec(
                    "getSumOfEvenIntegers",
                    new Class<?>[]{ ArrayList.class },
                    List.of(
                            TestCase.expectValue("func(2,4)", new Object[]{new ArrayList<>(Arrays.asList(2,4))}, 6),
                            TestCase.expectValue("func(2,false,4.0,'two')", new Object[]{new ArrayList<>(Arrays.asList(2,false,4.0,"two"))}, 2),
                            TestCase.expectValue("func(9,2,10,3,5,8)", new Object[]{new ArrayList<>(Arrays.asList(9,2,10,3,5,8))}, 20)
                    )
            ),

            "Q2a", new MethodSpec(
                    "getAverageAge",
                    new Class<?>[]{ String.class, String.class },
                    List.of(
                            TestCase.expectValue("(is442.txt,LEE)", new Object[]{"is442.txt", "LEE"}, -1.0),
                            TestCase.expectValue("(persontester.txt,TAY)", new Object[]{"personstester.txt", "TAY"}, 30.0),
                            TestCase.expectValue("(persontester.txt,LEE)", new Object[]{"personstester.txt", "LEE"}, 37.0),
                            TestCase.expectValue("(persontester.txt,Wong)", new Object[]{"personstester.txt", "Wong"}, 26.0),
                            TestCase.expectValue("(persontester.txt,Ong)", new Object[]{"personstester.txt", "Ong"}, 0.0)

                    )
            ),
            "Q2b", new MethodSpec(
                    "getTopStudent",
                    new Class<?>[]{ String.class, String.class },
                    List.of(
                            TestCase.expectException("IS442", new Object[]{"is442.txt", "IS101"}, "DataException"),
                            TestCase.expectException("SMU101", new Object[]{"studentstester.txt", "SMU101"}, "DataException"),
                            TestCase.expectValue("CS101", new Object[]{"studentstester.txt","CS101"}, "LEE Teck Yang-4.0"),
                            TestCase.expectValue("CS102", new Object[]{"studentstester.txt", "CS102"}, "Jason LEE-4.0"),
                            TestCase.expectValue("CS103", new Object[]{"studentstester.txt", "CS103"}, "TAY Evelyn-4.0")

                    )
            ),
            "Q3", new MethodSpec(
                "sortShapes",
                 new Class<?>[]{List.class},
                 List.of(
                        TestCase.expectValue("test1", new Object[]{new ArrayList<>(Arrays.asList(
                            new ObjSpec("Rectangle", "R1", 10, 2),
                            new ObjSpec("Rectangle", "R2", 5, 3),
                            new ObjSpec("Rectangle", "R3", 20, 3),
                            new ObjSpec("Rectangle", "R4", 500, 3)
                            ))}, List.of("R3=>60.0:46.0", "R1=>20.0:24.0", "R2=>15.0:16.0"))
                 )
                 )

            // ... one entry per sub-question
    );
}
