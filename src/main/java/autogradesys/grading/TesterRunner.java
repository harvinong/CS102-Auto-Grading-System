package autogradesys.grading;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;

import autogradesys.cli.TextColor;
import autogradesys.config.Config;

public class TesterRunner {

    private static final String TESTER_SUFFIX = "Tester.java";
    private static final String QUESTION_SUFFIX = ".java";
    
    private JavaCompiler compiler;
    private Config config;
    private Path gradingPath;
    private Map<String, TesterFile> testerFileMap = new LinkedHashMap<>();

    public TesterRunner(JavaCompiler compiler, Config config, Path gradingPath) {
        this.compiler = compiler;
        this.config = config;
        this.gradingPath = gradingPath;
    }

    public void run() {
        // Get a map of tester files.
        for (TesterFile t: getTesters()) {
            System.out.println(t);
            testerFileMap.put(t.forQuestion(), t);
        }

        copyToSubs();

    }

    private List<TesterFile> getTesters() {
        List<TesterFile> testerFileList = null;
        try (Stream<Path> pathStream = Files.walk(config.testersFolder().toPath())) {
            testerFileList = pathStream
            .filter(f -> f.toString().endsWith(TESTER_SUFFIX))
            .map(f -> {
                String filename = f.getFileName().toString();
                return new TesterFile(filename.toLowerCase().substring(0, filename.length() - TESTER_SUFFIX.length()), f.toFile());
            }).toList();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (testerFileList == null) {
                testerFileList = new ArrayList<>();
            }
        }

        return testerFileList;
    }

    private void copyToSubs() {
        try (Stream<Path> pathStream = Files.walk(gradingPath)) {
            pathStream.forEach(f -> {
                String question = null;
                if (f.endsWith(QUESTION_SUFFIX)) {
                    String filename = f.getFileName().toString().toLowerCase();
                    question = filename.substring(0, filename.length() - QUESTION_SUFFIX.length());
                }
                if (question != null && testerFileMap.containsKey(question)) {
                    TesterFile testerFile = testerFileMap.get(question);
                    try {
                        System.out.print(testerFile.origin().toPath() + " -> " + f);
                        Files.copy(testerFile.origin().toPath(), f);
                    } catch (IOException e) {
                        System.out.println(TextColor.ERROR.getActionTag());
                        e.printStackTrace();
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
