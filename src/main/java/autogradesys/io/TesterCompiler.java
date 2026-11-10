package autogradesys.io;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.ToolProvider;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import autogradesys.tester.QuestionTests;
import autogradesys.cli.TextColor;

public class TesterCompiler {

    record CompileAttempt(boolean success, Map<String, List<String>> errorsByFile) {
    }

    // Relative paths
    private static final String DEST_PATH = "classes";
    private static final String[] CLASS_PATHS = recurseLib();
    private final JavaCompiler sysJavaCompiler;
    private final File testerFile;

    public TesterCompiler(File testerFile) throws CompilerNotFoundException {
        this.testerFile = testerFile;
        sysJavaCompiler = ToolProvider.getSystemJavaCompiler();
        if (sysJavaCompiler == null) {
            throw new CompilerNotFoundException();
        }
        System.out.println(TextColor.SUCCESS.getActionTag() + " compiler ready 👍");
    }

    public CompileAttempt compile() {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean success = false;
        try (StandardJavaFileManager fileManager = sysJavaCompiler.getStandardFileManager(diagnostics, null, null)) {
            fileManager.setLocation(
                    StandardLocation.CLASS_PATH,
                    List.of(CLASS_PATHS).stream().map(x -> Path.of(x).toFile()).toList());
            fileManager.setLocation(
                StandardLocation.CLASS_OUTPUT, 
                List.of(DEST_PATH).stream().map(x -> Path.of(x).toFile()).toList());
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromStrings(
                    List.of(new String[] { testerFile.toPath().toAbsolutePath().toString() }));
            JavaCompiler.CompilationTask task = sysJavaCompiler.getTask(
                    null, fileManager, diagnostics, null, null, compilationUnits);
            success = task.call();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Map<String, List<String>> errorsByFile = new HashMap<>();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getSource() == null)
                continue;
            String fileName = Paths.get(d.getSource().toUri()).getFileName().toString();
            errorsByFile.computeIfAbsent(fileName, k -> new ArrayList<>()).add(d.toString());
        }

        return new CompileAttempt(success, errorsByFile);
    }

    public QuestionTests getQuestionTests() {
        try {
            URLClassLoader classLoader = URLClassLoader.newInstance(new URL[] { (new File(DEST_PATH)).toURI().toURL() });
            Class<?> cls = Class.forName(testerFile.getName().substring(0, ".java".length()), true, classLoader);
            Object instance = cls.getDeclaredConstructor().newInstance();
            System.out.println(TextColor.INFO.getActionTag() + " instancing done");
            return (QuestionTests) instance;
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
            | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();            
        } catch (MalformedURLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String[] recurseLib() {
        List<String> paths = null;
        try (Stream<Path> walk = Files.walk(Path.of(DEST_PATH))) {
            paths = walk.map(x -> x.toAbsolutePath().toString()).collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (paths == null) return new String[0];
        paths.add("classes");
        return paths.toArray(new String[0]);
    }

    public static void main(String[] args) {
        System.out.println(TextColor.INFO.getActionTag() + " Working on " + System.getProperty("user.dir"));
        File tf = new File("demo/Is442Test.java");
        try {
            TesterCompiler tc = new TesterCompiler(tf);

            if (args.length != 1 || !args[0].equals("--run")) {
                TesterCompiler.CompileAttempt status = tc.compile();
    
                if (status.success()) {
                    System.out.print(TextColor.SUCCESS.getActionTag());
                } else {
                    System.out.print(TextColor.ERROR.getActionTag());
                }
                System.out.println(" Number of failed compile: " + status.errorsByFile().size());

            }
            
        } catch (CompilerNotFoundException | CompileException e) {
            System.out.print(TextColor.ERROR.getActionTag() + " ");
            e.printStackTrace();
        }
    }

}