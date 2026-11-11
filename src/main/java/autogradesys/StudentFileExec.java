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
     * Compiles the supplied source file and returns any compiler errors.
     * @param javaFile the argument supplied to this operation.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
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
     * Builds dynamic tests for every discovered student submission.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     * @throws Exception when the operation cannot complete.
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
     * Resolves a serialized argument into the runtime type expected by the student method.
     * @param arg the argument to resolve or convert.
     * @param loader the class loader used to resolve student classes.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     * @throws Exception when the operation cannot complete.
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
     * Resolves every serialized test argument using the supplied class loader.
     * @param args the arguments supplied to the invoked method.
     * @param loader the class loader used to resolve student classes.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     * @throws Exception when the operation cannot complete.
     */
    private Object[] resolveArgs(Object[] args, ClassLoader loader) throws Exception {
        Object[] out = new Object[args.length];
        for (int i = 0; i < args.length; i++) out[i] = resolveArg(args[i], loader);
        return out;
    }

    // Finds a constructor whose parameters can accept the args (allows int -> double widening)
    /**
     * Constructs an object using the matching student constructor and resolved arguments.
     * @param cls the argument supplied to this operation.
     * @param args the arguments supplied to the invoked method.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     * @throws Exception when the operation cannot complete.
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
     * Converts an argument to the target primitive or reference type when possible.
     * @param arg the argument to resolve or convert.
     * @param target the argument supplied to this operation.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
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
     * Builds dynamic test containers for each question in a student directory.
     * @param studentDir the directory containing one student submission.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
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
     * Builds dynamic test containers for each Java sub-question in a question directory.
     * @param questionDir the directory containing one question.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
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
 * Builds the dynamic container and tests associated with one sub-question source file.
 * @param javaFile the argument supplied to this operation.
 * @param questionDir the directory containing one question.
 * @param compileResult the argument supplied to this operation.
 * @param loader the class loader used to resolve student classes.
 * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
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
 * Creates dynamic tests from the configured cases for one sub-question class.
 * @param className the argument supplied to this operation.
 * @param loader the class loader used to resolve student classes.
 * @param questionDir the directory containing one question.
 * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
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
     * Compiles all Java sources in the supplied directory and reports the compilation result.
     * @param dir the directory to scan or compile.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     * @throws Exception when the operation cannot complete.
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
 * Invokes one student method and records whether its result matches the test case.
 * @param className the argument supplied to this operation.
 * @param spec the argument supplied to this operation.
 * @param tc the argument supplied to this operation.
 * @param studentClass the argument supplied to this operation.
 * @param method the argument supplied to this operation.
 * @param loader the class loader used to resolve student classes.
 * @param questionDir the directory containing one question.
 * @throws Throwable when the operation cannot complete.
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
 * Attempts compilation with the available compiler settings and captures diagnostics.
 * @param compiler the argument supplied to this operation.
 * @param sourceFiles the Java source files to compile.
 * @param questionDir the directory containing one question.
 * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
 * @throws Exception when the operation cannot complete.
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
     * Copies non-Java resources needed by the submission into its working directory.
     * @param questionDir the directory containing one question.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     * @throws Exception when the operation cannot complete.
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
     * Removes the temporary resource copies created for compilation.
     * @param copiedFiles the argument supplied to this operation.
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