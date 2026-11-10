package autogradesys;
// java
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
public class StudentFileExec {

    // label is test case name (preferably function call in string) args is args, expected is expected output.
    record TestCase(String label, Object[] args, Object expected, String expectedException) {

    // use for object or value return
    static TestCase expectValue(String label, Object[] args, Object expected) {
        return new TestCase(label, args, expected, null);
    }
  
    // use for testing exceptions
    static TestCase expectException(String label, Object[] args,String expectedExceptionName) {
        return new TestCase(label, args, null, expectedExceptionName);
    }
}
    // Describes "new <className>(<args>)"  im so sorry....
    record ObjSpec(String className, Object... ctorArgs) {}

    
    // method name like (compareShape, IsIsogramWord, ...) paramTypes is what type of parameter it is expected. testCases is the testcases it will be put against
    record MethodSpec(String methodName, Class<?>[] paramTypes, List<TestCase> testCases) {}

    record CompileAttempt(boolean success, Map<String, List<String>> errorsByFile) {}

    record CompileResult(Map<String, List<String>> errorsByFile) {
    String errorsForFile(Path javaFile) {
        String fileName = javaFile.getFileName().toString();
        List<String> errors = errorsByFile.getOrDefault(fileName, List.of());
        return errors.isEmpty()
                ? "(no specific diagnostic captured -- check dependent files for errors)"
                : String.join("\n", errors);
    }
}
    // PLEASE FOLLOW ACCORDINGLY
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
    //entry point from GradingEntry
    @TestFactory
    Stream<DynamicNode> testAllSubmissions() throws Exception {
        Path submissionsRoot = Paths.get("submissions");
        List<Path> studentDirs = Files.list(submissionsRoot)
                .filter(Files::isDirectory)
                .toList();

        return studentDirs.stream().map(studentDir ->
            DynamicContainer.dynamicContainer(
                    "2023-2024-" + studentDir.getFileName(),
                    buildQuestionContainers(studentDir) //cos yknow the files aint here
            )
        );
    }

        // Recursively turns ObjSpec / List into real objects using the student's classloader
    private Object resolveArg(Object arg, ClassLoader loader) throws Exception {
        if (arg instanceof ObjSpec spec) {
            Class<?> cls = Class.forName(spec.className(), true, loader);
            Object[] ctorArgs = new Object[spec.ctorArgs().length];
            for (int i = 0; i < ctorArgs.length; i++) {
                ctorArgs[i] = resolveArg(spec.ctorArgs()[i], loader);
            }
            return construct(cls, ctorArgs);
        }
        if (arg instanceof List<?> list) {
            ArrayList<Object> resolved = new ArrayList<>();
            for (Object o : list) resolved.add(resolveArg(o, loader));
            return resolved;
        }
        return arg; // plain values (String, Integer, ...) pass through untouched
    }

    private Object[] resolveArgs(Object[] args, ClassLoader loader) throws Exception {
        Object[] out = new Object[args.length];
        for (int i = 0; i < args.length; i++) out[i] = resolveArg(args[i], loader);
        return out;
    }

    // Finds a constructor whose parameters can accept the args (allows int -> double widening)
    private Object construct(Class<?> cls, Object[] args) throws Exception {
        for (java.lang.reflect.Constructor<?> c : cls.getConstructors()) {
            Class<?>[] pts = c.getParameterTypes();
            if (pts.length != args.length) continue;
            Object[] converted = new Object[args.length];
            boolean ok = true;
            for (int i = 0; i < pts.length && ok; i++) {
                Object converted_i = coerce(args[i], pts[i]);
                if (converted_i == NO_MATCH) ok = false; else converted[i] = converted_i;
            }
            if (ok) return c.newInstance(converted);
        }
        throw new NoSuchMethodException("No matching constructor on " + cls.getName()
                + " for args " + Arrays.toString(args));
    }

    private static final Object NO_MATCH = new Object();

    private Object coerce(Object arg, Class<?> target) {
        if (arg instanceof Number n) {
            if (target == double.class || target == Double.class) return n.doubleValue();
            if (target == int.class || target == Integer.class)
                return (n instanceof Integer) ? n : NO_MATCH;
            if (target == float.class || target == Float.class) return n.floatValue();
            if (target == long.class || target == Long.class) return n.longValue();
        }
        if (arg == null) return target.isPrimitive() ? NO_MATCH : null;
        return target.isInstance(arg) ? arg : NO_MATCH;
    }
    // this is the MAIN question container
    private Stream<DynamicNode> buildQuestionContainers(Path studentDir) {
        try {
            List<Path> questionDirs = Files.list(studentDir)
                    .filter(Files::isDirectory)
                    .toList();

            return questionDirs.stream().map(questionDir ->
                DynamicContainer.dynamicContainer(
                        questionDir.getFileName().toString(), // e.g. "Q1"
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
        CompileResult compileResult = compileAll(questionDir);

        URLClassLoader loader = new URLClassLoader(
                new URL[]{ questionDir.toUri().toURL() },
                StudentFileExec.class.getClassLoader()
        );

        List<Path> javaFiles = Files.list(questionDir)
                .filter(p -> p.toString().endsWith(".java"))
                .toList();

        return javaFiles.stream()
                .filter(javaFile -> SPECS_BY_CLASS.containsKey(
                        javaFile.getFileName().toString().replace(".java", "")))
                .map(javaFile -> buildOneSubQuestionContainer(javaFile, questionDir, compileResult, loader))
                
                ;

    } catch (Exception e) {
        return Stream.of(DynamicTest.dynamicTest(
                "Setup failed for " + questionDir.getFileName(),
                () -> { throw new AssertionError("Could not compile/load: " + e.getMessage(), e); }
        ));
    }
}

//actual like dir where question is. so can test
private DynamicNode buildOneSubQuestionContainer(Path javaFile, Path questionDir,
                                                   CompileResult compileResult, URLClassLoader loader) {
    String className = javaFile.getFileName().toString().replace(".java", "");
                                                    // compilation area
    try {
        Path expectedClassFile = questionDir.resolve(className + ".class");

        if (!Files.exists(expectedClassFile)) {
            String relevantErrors = compileResult.errorsForFile(javaFile);
            return DynamicContainer.dynamicContainer(
                    className,
                    Stream.of(DynamicTest.dynamicTest(
                            className + ": compilation failed",
                            () -> { throw new AssertionError(
                                    "Compilation failed for " + className + ":\n" + relevantErrors); }
                    ))
            );
        }

        return DynamicContainer.dynamicContainer(
                className,
                buildTestCasesForSubQuestion(className, loader, questionDir)
        );

    } catch (Throwable t) {
      
        return DynamicContainer.dynamicContainer(
                className,
                Stream.of(DynamicTest.dynamicTest(
                        className + ": unexpected error during setup",
                        () -> { throw new AssertionError(
                                "Unexpected failure loading " + className + ": " + t, t); }
                ))
        );
    }
}

    // each test case within the subquestion yes
    //dynamicnode cos its what junit thing is for test generated at runtime like instead of @test function {} or whatever
private Stream<DynamicNode> buildTestCasesForSubQuestion(String className, URLClassLoader loader, Path questionDir) {
    MethodSpec spec = SPECS_BY_CLASS.get(className);
    if (spec == null) {
        return Stream.of(DynamicTest.dynamicTest(
                "No spec defined for " + className,
                () -> { throw new AssertionError("No SPECS_BY_CLASS entry for: " + className); }
        ));
    }

    try {
        Class<?> studentClass = Class.forName(className, true, loader);
        
        Method method = studentClass.getMethod(spec.methodName(), spec.paramTypes());

        return spec.testCases().stream().map(tc ->
    (DynamicNode) DynamicTest.dynamicTest(tc.label(), () -> runTestCase(className, spec, tc, studentClass, method, loader, questionDir)));

    } catch (NoSuchMethodException e) {
        return Stream.of(DynamicTest.dynamicTest(
                className + ": method not found",
                () -> { throw new AssertionError("Expected method not found on " + className, e); }
        ));
    } catch (Throwable t) {  
        return Stream.of(DynamicTest.dynamicTest(
                "Load failed: " + className,
                () -> { throw new AssertionError("Could not instantiate " + className + ": " + t, t); }
        ));
    }
}
    // compiling student .java to .class if they didnt do so
    private CompileResult compileAll(Path dir) throws Exception {
    javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();

    List<Path> allJavaFiles = Files.list(dir)
            .filter(p -> p.toString().endsWith(".java"))
            .toList();

    if (allJavaFiles.isEmpty()) {
        return new CompileResult(Map.of());
    }

    // try all
   
    CompileAttempt firstAttempt = tryCompile(compiler, allJavaFiles,dir);

    if (firstAttempt.success()) {
        return new CompileResult(Map.of()); 
    }

    // something failed. Retry without file w prob
    // so files that are actually fine still get their .class produced.
    Set<String> filesWithErrors = firstAttempt.errorsByFile().keySet();
    List<Path> cleanFiles = allJavaFiles.stream()
            .filter(f -> !filesWithErrors.contains(f.getFileName().toString()))
            .toList();

    if (!cleanFiles.isEmpty()) {
        tryCompile(compiler, cleanFiles,dir);
    }

    return new CompileResult(firstAttempt.errorsByFile());
}
//the fun begins
private void runTestCase(String className, MethodSpec spec, TestCase tc,
                         Class<?> studentClass, Method method,
                         ClassLoader loader, Path questionDir) throws Throwable {
    //each test case recopies resource files to make sure it is clean
    List<Path> copiedFiles = copyResourceFilesToWorkingDir(questionDir);
    try {
        //get argument and method instance
        Object[] realArgs = resolveArgs(tc.args(), loader);
        Object freshInstance = studentClass.getDeclaredConstructor().newInstance();
        //when exception testing
        if (tc.expectedException() != null) {
            Throwable thrown = null;
            //timeout purposes. new thread
            ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread d = new Thread(r, "studentgrading");
            d.setDaemon(true); //there are daemon and user threads. daemon shutsdown after program shutsdown
            return d;
            });
            try {
                Future<Object> future = exec.submit(() -> method.invoke(freshInstance,realArgs));
                future.get(5,TimeUnit.SECONDS); //after 5s
            } catch (TimeoutException e) {
                throw new AssertionError(className + "." + spec.methodName() + "on" + tc.label() + "timed out");
            } catch (ExecutionException e) {
                thrown = e.getCause();
                if (thrown instanceof java.lang.reflect.InvocationTargetException ite) thrown = ite.getCause();
            } finally {
                exec.shutdownNow();
            }
            //testing exceptions
            if (thrown == null) {
                throw new AssertionError(className + "." + spec.methodName() + " on " + tc.label()
                        + " expected " + tc.expectedException() + " but no exception was thrown");
            }
            String actualName = thrown.getClass().getSimpleName();
            if (!actualName.equals(tc.expectedException())) {
                throw new AssertionError(className + "." + spec.methodName() + " on " + tc.label()
                        + " expected " + tc.expectedException() + " but got " + actualName, thrown);
            }
            return;
        }
        //value for value testing
        Object actual;
        ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread d = new Thread(r, "studentgrading");
            d.setDaemon(true);
            return d;
        });
        try {
            Future<Object> future = exec.submit(() -> method.invoke(freshInstance,realArgs));
            actual = future.get(5,TimeUnit.SECONDS);
        } catch (TimeoutException t) {
            throw new AssertionError(className + "." + spec.methodName() + " on " + tc.label() + "timed out (5s)");
        
        } catch (ExecutionException e) {
            throw new AssertionError(className + "." + spec.methodName() + " on " + tc.label()
                    + " threw " + e.getCause(), e.getCause());
        } finally {
            exec.shutdownNow();
        }

        Object expected = tc.expected();
        //because collections a bitch i just cast them all to list then compare
        if (expected instanceof List<?> el && actual instanceof List<?> al) {
            List<String> exp = new ArrayList<>();
            for (Object o : el) {exp.add(String.valueOf(o));}
            List<String> act = new ArrayList<>();
            for (Object o : al) {act.add(String.valueOf(o));}
            expected = exp;
            actual = act;
        }

        assertEquals(expected, actual,
                className + "." + spec.methodName() + " failed on " + tc.label());
    } finally {
        cleanupCopiedFiles(copiedFiles);
    }
}
//try compile, if cant compile all java, compile all again without said fail java 
private CompileAttempt tryCompile(JavaCompiler compiler, List<Path> sourceFiles, Path questionDir) throws Exception {
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    boolean success;
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            fileManager.setLocation(
                    javax.tools.StandardLocation.CLASS_PATH,
                    List.of(questionDir.toFile())
            );      Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjectsFromStrings(
                            sourceFiles.stream().map(Path::toString).toList());
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, fileManager, diagnostics, null, null, compilationUnits);
            success = task.call();
        }

    Map<String, List<String>> errorsByFile = new HashMap<>();
    for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
        if (d.getSource() == null) continue;
        String fileName = Paths.get(d.getSource().toUri()).getFileName().toString();
        errorsByFile.computeIfAbsent(fileName, k -> new ArrayList<>()).add(d.toString());
        }

    return new CompileAttempt(success, errorsByFile);
}



    private List<Path> copyResourceFilesToWorkingDir(Path questionDir) throws Exception {
        Path workingDir = Paths.get("").toAbsolutePath();
        List<Path> copiedPaths = new ArrayList<>();

        List<Path> resourceFiles;
        //basically copy all that is not java or class
        try (Stream<Path> stream = Files.list(questionDir)) { 
            resourceFiles = stream
                    .filter(Files::isRegularFile)                        
                    .filter(p -> !p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().endsWith(".class"))        
                    .toList();
        }

        try {
            for (Path src : resourceFiles) {
                Path dest = workingDir.resolve(src.getFileName());
                if (Files.exists(dest)) {                                 
                    throw new IllegalStateException("Refusing to overwrite existing file in working dir: " + dest);
                }
                Files.copy(src, dest);
                copiedPaths.add(dest);
                
            }
        } catch (IOException | IllegalStateException e) {                                          
            cleanupCopiedFiles(copiedPaths);
            throw e;
        }
    return copiedPaths;
    }
    //cleanup after each test case run to prevent contaminatioon
    private void cleanupCopiedFiles(List<Path> copiedFiles) {
        for (Path p : copiedFiles) {
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    Files.deleteIfExists(p);
                    break;
                } catch (Exception e) {
                    if (attempt == 2) System.err.println("WARNING: could not delete " + p + ": " + e);
                    else { System.gc(); try { Thread.sleep(50); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); } }
                }
            }
        }
    }
}