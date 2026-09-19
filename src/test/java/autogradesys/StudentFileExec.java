package autogradesys;
// junit imports
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
public class StudentFileExec {

    // label is test case name (preferably function call in string) args is args, expected is expected output.
    record TestCase(String label, Object[] args, Object expected) {}
    // method name like (compareShape, IsIsogramWord, ...) paramTypes is what type of parameter it is expected. testCases is the testcases it will be put against
    record MethodSpec(String methodName, Class<?>[] paramTypes, List<TestCase> testCases) {}

    // PLEASE FOLLOW ACCORDINGLY
    private static final Map<String, MethodSpec> SPECS_BY_CLASS = Map.of(

    /*
            "NAME OF .JAVA FILE", new MethodSpec(
                    "METHOD NAME TO TEST",
                    new Class<?>[]{TYPES OF PARAM FOR METHOD.class, ... ...}
            )
    
    */
            "Q1a", new MethodSpec(
                    "getIsogramWords",
                    new Class<?>[]{ ArrayList.class},
                    List.of(
                            new TestCase("getIsogramWords(cat,dog,tiger)",   new Object[]{new ArrayList<>(Arrays.asList("cat","dog","tiger"))},   new ArrayList<>(Arrays.asList("cat","dog","tiger"))),
                            new TestCase("getIsogramWords(alan,ben,cindy,danny,evan)", new Object[]{new ArrayList<>(Arrays.asList("alan","ben","cindy","danny","evan"))}, new ArrayList<>(Arrays.asList("ben","cindy","evan")))
                    )
            ),

            "Q1b", new MethodSpec(
                    "getSumOfEvenIntegers",
                    new Class<?>[]{ ArrayList.class },
                    List.of(
                            new TestCase("func(10,20,30)", new Object[]{new ArrayList<>(Arrays.asList(10,20,30))}, 60),
                            new TestCase("func(2,true,abc,1.0)", new Object[]{new ArrayList<>(Arrays.asList(2,true,"abc",1.0))}, 2)
                    )
            ),

            "Q2a", new MethodSpec(
                    "getAverageAge",
                    new Class<?>[]{ String.class, String.class },
                    List.of(
                            new TestCase("(person.txt,lee)", new Object[]{"persons.txt", "LEE"}, 36.0),
                            new TestCase("(person.txt,WONG)", new Object[]{"persons.txt", "WONG"}, 25.0),
                            new TestCase("(person.txt,Chan)", new Object[]{"persons.txt", "Chan"}, 27.0),
                            new TestCase("(person.txt,Ong)", new Object[]{"persons.txt", "Ong"}, 0.0),
                            new TestCase("(nosuchfile.txt,lee)", new Object[]{"nosuchfile.txt", "Lee"}, -1.0)

                    )
            ),

            "Q2b", new MethodSpec(
                    "getAverageAge",
                    new Class<?>[]{ String.class, String.class },
                    List.of(
                            new TestCase("(person.txt,lee)", new Object[]{"persons.txt", "LEE"}, 36.0)
                    )
            )

            // ... one entry per sub-question
    );

    @TestFactory
    Stream<DynamicNode> testAllSubmissions() throws Exception {
        Path submissionsRoot = Paths.get("submissions");
        List<Path> studentDirs = Files.list(submissionsRoot)
                .filter(Files::isDirectory)
                .toList();

        return studentDirs.stream().map(studentDir ->
            DynamicContainer.dynamicContainer(
                    "Student: " + studentDir.getFileName(),
                    buildQuestionContainers(studentDir)
            )
        );
    }

    // this is the MAIN question container
    private Stream<DynamicNode> buildQuestionContainers(Path studentDir) {
        try {
            List<Path> questionDirs = Files.list(studentDir)
                    .filter(Files::isDirectory)
                    .toList();

            return questionDirs.stream().map(questionDir ->
                DynamicContainer.dynamicContainer(
                        questionDir.getFileName().toString(), // e.g. "Question 1"
                        buildSubQuestionContainers(questionDir)
                )
            );
        } catch (Exception e) {
            return Stream.of(DynamicTest.dynamicTest(
                    "Setup failed for " + studentDir.getFileName(),
                    () -> { throw new AssertionError("Could not list question folders: " + e.getMessage(), e); }
            ));
        }
    }

    // this is the SUB QUESTION (1a, 1b, 1c etc) container 
    private Stream<DynamicNode> buildSubQuestionContainers(Path questionDir) {
        try {
            compileAll(questionDir);

            URLClassLoader loader = new URLClassLoader(
                    new URL[]{ questionDir.toUri().toURL() },
                    StudentFileExec.class.getClassLoader()
            );

            List<Path> javaFiles = Files.list(questionDir)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();

            return javaFiles.stream().map(javaFile -> {
                String className = javaFile.getFileName().toString().replace(".java", "");
                return DynamicContainer.dynamicContainer(
                        className, // if student no  name properly ggez no run
                        buildTestCasesForSubQuestion(className, loader, questionDir)
                );
            });

        } catch (Exception e) {
            return Stream.of(DynamicTest.dynamicTest(
                    "Setup failed for " + questionDir.getFileName(),
                    () -> { throw new AssertionError("Could not compile/load: " + e.getMessage(), e); }
            ));
        }
    }

    // each 1a 1b or whatev
    private Stream<DynamicNode> buildTestCasesForSubQuestion(String className, URLClassLoader loader, Path questionDir) {
        MethodSpec spec = SPECS_BY_CLASS.get(className);

        // if like a extra .java is found and not part of question or prof forgot to list a question
        if (spec == null) {
            return Stream.of(DynamicTest.dynamicTest(
                    "No spec defined for " + className,
                    () -> { throw new AssertionError("No SPECS_BY_CLASS entry for: " + className); }
            ));
        }

        try {
            Class<?> studentClass = Class.forName(className, true, loader);
            Object instance = studentClass.getDeclaredConstructor().newInstance();

            // Resolved ONCE per class using the explicit signature — no guessing
            Method method = studentClass.getMethod(spec.methodName(), spec.paramTypes());

            return spec.testCases().stream().map(tc ->
                DynamicTest.dynamicTest(tc.label(), () -> {
                List<Path> copiedFiles = copyResourceFilesToWorkingDir(questionDir);
                try {
                    Object actual = method.invoke(instance, tc.args());
                    assertEquals(tc.expected(), actual,
                            () -> className + "." + spec.methodName() + " failed on " + tc.label());
                } finally {
                    cleanupCopiedFiles(copiedFiles); 
                }
            })
            );

        } catch (NoSuchMethodException e) {
            // method name or signature no match specification
            return Stream.of(DynamicTest.dynamicTest(
                    className + ": method not found",
                    () -> { throw new AssertionError(
                            "Expected method " + spec.methodName() + Stream.of(spec.paramTypes())
                                    .map(Class::getSimpleName).toList()
                                    + " not found on " + className + ". Check method name/signature.", e); }
            ));
        } catch (Exception e) {
            return Stream.of(DynamicTest.dynamicTest(
                    "Load failed: " + className,
                    () -> { throw new AssertionError("Could not instantiate " + className + ": " + e.getMessage(), e); }
            ));
        }
    }
    // compiling student .java to .class if they didnt do so
    private void compileAll(Path dir) throws Exception {
        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        List<String> sourceFiles = Files.list(dir)
                .filter(p -> p.toString().endsWith(".java"))
                .map(Path::toString)
                .toList();
        compiler.run(null, null, null, sourceFiles.toArray(String[]::new));
    }


    private List<Path> copyResourceFilesToWorkingDir(Path questionDir) throws Exception {
    Path workingDir = Paths.get("").toAbsolutePath(); // the JVM's actual cwd
    List<Path> resourceFiles = Files.list(questionDir)
            .filter(p -> !p.toString().endsWith(".java") && !p.toString().endsWith(".class"))
            .toList();

    List<Path> copiedPaths = new java.util.ArrayList<>();
    for (Path src : resourceFiles) {
        Path dest = workingDir.resolve(src.getFileName());
        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        copiedPaths.add(dest);
    }
    return copiedPaths;
}

    private void cleanupCopiedFiles(List<Path> copiedFiles) {
        for (Path p : copiedFiles) {
            try {
                Files.deleteIfExists(p);
            } catch (Exception ignored) {
                
            }
        }
    }
}