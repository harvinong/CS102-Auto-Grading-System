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

import autogradesys.controller.MainCtrl;

public class StudentFileExec {

    // label is test case name (preferably function call in string) args is args, expected is expected output.
    
    MainCtrl mc = new MainCtrl();

    record CompileAttempt(boolean success, Map<String, List<String>> errorsByFile) {}

    record CompileResult(Map<String, List<String>> errorsByFile) {
    /**
     * Gives the compiler messages recorded for one source file.
     *
     * @param javaFile the source file; only its file name is used to look up the messages
     * @return the file's compiler messages joined by line breaks, or a note that no specific diagnostic was captured when none were recorded for it (its error may come from a file it depends on)
     */
    String errorsForFile(Path javaFile) {
        String fileName = javaFile.getFileName().toString();
        List<String> errors = errorsByFile.getOrDefault(fileName, List.of());
        return errors.isEmpty()
                ? "(no specific diagnostic captured -- check dependent files for errors)"
                : String.join("\n", errors);
    }
}
    // PLEASE FOLLOW ACCORDINGLY
    private static final Map<String, MethodSpec> SPECS_BY_CLASS = QuestionTests.getSPECS_BY_CLASS();
    //entry point from GradingEntry
    @TestFactory
    /**
     * Entry point for JUnit: builds one test container per student folder found directly inside the configured starting folder, each named with the {@code 2023-2024-} prefix.
     *
     * @return the dynamic student containers, which hold the question and sub-question tests
     * @throws Exception if the starting folder cannot be listed or the configuration has none set
     */
    Stream<DynamicNode> testAllSubmissions() throws Exception {
        Path submissionsRoot = Paths.get(mc.getStartingFolder().getPath());
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
    /**
     * Turns a test argument into a real object using the student's class loader: an {@link ObjSpec} is built by calling the matching constructor of the student's class, a list is resolved element by element, and any other value passes through unchanged.
     *
     * @param arg the argument from the test case: an {@code ObjSpec}, a list that may contain them, or a plain value such as a String or Integer
     * @param loader the class loader that can load the student's classes
     * @return the object to pass to the student's method
     * @throws Exception if a class cannot be loaded or no constructor accepts the arguments
     */
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

    /**
     * Resolves every argument of one test case with {@link #resolveArg}.
     *
     * @param args the arguments listed in the test case
     * @param loader the class loader that can load the student's classes
     * @return a new array of the same length holding the resolved arguments
     * @throws Exception if any argument cannot be resolved
     */
    private Object[] resolveArgs(Object[] args, ClassLoader loader) throws Exception {
        Object[] out = new Object[args.length];
        for (int i = 0; i < args.length; i++) out[i] = resolveArg(args[i], loader);
        return out;
    }

    // Finds a constructor whose parameters can accept the args (allows int -> double widening)
    /**
     * Creates an instance by finding a public constructor whose parameters can accept the arguments (an int may be widened to a double or other number type).
     *
     * @param cls the student's class to instantiate
     * @param args the already resolved constructor arguments
     * @return the new instance made by the first constructor that fits
     * @throws NoSuchMethodException if no public constructor accepts these arguments
     * @throws Exception if the chosen constructor itself fails
     */
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

    /**
     * Converts a number or other value to the type a parameter needs when that is possible without losing meaning.
     *
     * @param arg the value from the test case; null is accepted
     * @param target the parameter type to fit
     * @return the value converted to the target type, the same value if it already fits, null for a null argument on a non-primitive parameter, or {@code NO_MATCH} if it cannot fit (a null for a primitive, a non-integer number for an int, or an unrelated type)
     */
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
    /**
     * Builds one test container for each question folder (such as Q1) inside a student's folder.
     *
     * @param studentDir the student's folder
     * @return the question containers; a single failing test describing the problem if the folder cannot be listed
     */
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
    /**
     * Compiles the Java files of one question folder and builds a container for each sub-question (such as Q1a) that has a test definition. Java files with no definition are ignored.
     *
     * @param questionDir the question folder inside a student's folder
     * @return one container per defined sub-question; a single failing test if compiling or loading the folder fails
     */
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
/**
 * Builds the container for one sub-question source file. If the compiler produced no class file for it, the container holds one failing test with the compiler messages.
 *
 * @param javaFile the sub-question's source file; its name without {@code .java} is the class name
 * @param questionDir the folder holding the source and compiled classes
 * @param compileResult the compiler messages gathered for the folder
 * @param loader the class loader that can load the student's classes
 * @return the container, holding the test cases, or a failing test if it could not be compiled or set up
 */
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
/**
 * Creates one dynamic test for each test case of a sub-question, after loading the student's class and finding the method to call.
 *
 * @param className the sub-question's class name, e.g. Q1a
 * @param loader the class loader that can load the student's classes
 * @param questionDir the folder holding the class and its resource files
 * @return one test per case; a single failing test if there is no definition for the class, the method is missing, or the class cannot be loaded
 */
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
    /**
     * Compiles all Java files in a folder. If the whole set fails, the files that had no errors are compiled again so they still produce class files.
     *
     * @param dir the question folder
     * @return the compiler messages by file name; empty when there are no Java files or everything compiled
     * @throws Exception if the compiler cannot be run
     */
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
/**
 * Runs one test case on a fresh instance of the student's class, with the resource files copied in and removed again afterwards. The call runs on its own thread with the configured timeout. A value case must equal the expected value (lists are compared as text) and fit the configured size limit; an exception case must throw an exception of the expected simple name.
 *
 * @param className the sub-question's class name, used in failure messages
 * @param spec the method definition being tested
 * @param tc the test case: label, arguments and expected value or exception
 * @param studentClass the student's loaded class
 * @param method the method to invoke
 * @param loader the class loader that can load the student's classes
 * @param questionDir the folder whose resource files are copied in
 * @throws Throwable an {@link AssertionError} when the test fails, times out, returns too much, throws unexpectedly, or does not throw an expected exception; or any error raised while preparing the call
 */
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
                future.get(mc.getTimeoutSeconds(),TimeUnit.SECONDS); //after 5s
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
            actual = future.get(mc.getTimeoutSeconds(),TimeUnit.SECONDS);
            if (SizeObject.getObjectSize(actual) > mc.getOutputLimitBytes()) {
                throw new AssertionError(className + "." + spec.methodName() + " on " + tc.label() + " returned too big.");
            }
        } catch (TimeoutException t) {
            throw new AssertionError(className + "." + spec.methodName() + " on " + tc.label() + " timed out (5s)");
        
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
/**
 * Compiles the given files in one compiler run and sorts the messages by file.
 *
 * @param compiler the system Java compiler
 * @param sourceFiles the files to compile together
 * @param questionDir the folder put on the class path so the files can see each other's classes
 * @return whether compilation succeeded and the messages by file name
 * @throws Exception if the compiler cannot be run
 */
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



    /**
     * Copies the question's non-Java, non-class files, such as {@code persons.txt}, into the program's working directory so the student's code can open them by name. Nothing is overwritten.
     *
     * @param questionDir the folder holding the resource files
     * @return the paths of the copies, so they can be removed later
     * @throws IllegalStateException if a file of the same name already exists in the working directory; any copies already made are removed first
     * @throws Exception if a file cannot be listed or copied
     */
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
    /**
     * Deletes the resource copies made for a test case, to prevent one test affecting the next. Each file is tried three times, and a file that still cannot be deleted gives a warning.
     *
     * @param copiedFiles the paths returned by {@link #copyResourceFilesToWorkingDir}
     */
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