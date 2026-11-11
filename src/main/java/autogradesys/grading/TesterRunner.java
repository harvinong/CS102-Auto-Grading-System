package autogradesys.grading;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
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

    public void run() throws IOException {
        // Get a map of tester files.
        for (TesterFile t: getTesters()) {
            System.out.println(t);
            testerFileMap.put(t.forQuestion(), t);
        }

        copyToSubs();

    }

    private List<TesterFile> getTesters() throws IOException {
        List<TesterFile> testerFileList = null;
        try (Stream<Path> pathStream = Files.walk(config.testersFolder().toPath())) {
            testerFileList = pathStream
            .filter(f -> f.toString().endsWith(TESTER_SUFFIX))
            .map(f -> {
                String filename = f.getFileName().toString();
                return new TesterFile(filename.substring(0, filename.length() - TESTER_SUFFIX.length()), f.toFile());
            }).toList();
        } finally {
            if (testerFileList == null) {
                testerFileList = new ArrayList<>();
            }
        }

        return testerFileList;
    }

    private void copyToSubs() throws IOException {
        System.out.println(TextColor.INFO.apply("COPY TO SUBS"));
        try (Stream<Path> pathStream = Files.walk(gradingPath)) {
            Iterator<Path> pathStreamIterator = pathStream.iterator();
            while (pathStreamIterator.hasNext()) {
                Path p = pathStreamIterator.next();

                // Find file that ends with .java
                String question = null;
                if (p.toString().endsWith(QUESTION_SUFFIX)) {
                    String filename = p.getFileName().toString();
                    question = filename.substring(0, filename.length() - QUESTION_SUFFIX.length());
                }

                // if the name is part of the tester file (therefore is a question)
                if (testerFileMap.containsKey(question)) {
                    System.out.println(TextColor.INFO.getActionTag() + " found " + question);
                    TesterFile testerFile = testerFileMap.get(question);

                    Path dest = p.getParent().resolve(testerFile.origin().toPath().getFileName());
                    Files.copy(testerFile.origin().toPath(), dest);
                }

            }
        }
    }

}
