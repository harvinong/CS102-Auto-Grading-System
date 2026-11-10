package autogradesys.tester;

import java.util.Map;

/**
 * The superclass of tester files to test questions.
 * 
 * Extend your tester class to this class to make a tester file.
 * 
 */
public abstract class QuestionTests {

    /**<p>
     * Get specification for each questions. 
     * </p><p>
     * To create one entry, you should set the key as the name of the 
     * .java file. For example, to test {@code Q1a.java}, the key should be {@code "Q1a"}.
     * </p><p>
     * The value of the entry is a method specification ({@link MethodSpec}),
     * which specifies how the method is expected to run.
     * <p>
     * <b>Each entry should look like this:</b>
     * <pre>
     * { "Q2b": new MethodSpec(
     *          "getTopStudent", 
     *          new Class<?>[]{ String.class, String.class },
     *          List.of(
     *              TestCase.expectException(
     *                  "IS442", 
     *                  new Object[]{ "is442.txt", "IS101" }, 
     *                  "DataException"
     *              ),
     *              TestCase.expectException(
     *                  "SMU101", 
     *                  new Object[]{ "studentstester.txt", "SMU101" }, 
     *                  "DataException"
     *              ),
     *              TestCase.expectValue(
     *                  "CS101", 
     *                  new Object[]{ "studentstester.txt", "CS101" }, 
     *                  "LEE Teck Yang-4.0"
     *              ),
     *              TestCase.expectValue(
     *                  "CS102", 
     *                  new Object[]{ "studentstester.txt", "CS102" }, 
     *                  "Jason LEE-4.0"
     *              ),
     *              TestCase.expectValue(
     *                  "CS103", 
     *                  new Object[]{ "studentstester.txt", "CS103" }, 
     *                  "TAY Evelyn-4.0"
     *              )
     *          )
     *      )
     * }
     * </pre>
     * @see MethodSpec
     * @see TestCase
     * @return A map that maps one java file to a method specification.
     */
    public abstract Map<String, MethodSpec> getSpecs();

}
