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
    private ErrorReporter reporter;
    private Map<String, TesterFile> testerFileMap = new LinkedHashMap<>();

    public TesterRunner(JavaCompiler compiler, Config config, Path gradingPath, ErrorReporter reporter) {
        this.compiler = compiler;
        this.config = config;
        this.gradingPath = gradingPath;
        this.reporter = reporter;
    }

    public void run() throws IOException {
        // Get a map of tester files.
        reporter.info(TextColor.INFO.getActionTag() + " Searching tester files.");
        for (TesterFile t: getTesters()) {
            testerFileMap.put(t.forQuestion(), t);
        }
        if (testerFileMap.size() == 0) {
            reporter.error("test", "filesys", "ERROR", "can't identify any test files", "check if you configured the correct directory", "", "", "", false);
        } else {
            System.out.printf(
                "%s Identified %d question%s: %s%n",
                TextColor.SUCCESS.getActionTag(),
                testerFileMap.size(),
                testerFileMap.size() == 1? "": "s",
                String.join(", ", testerFileMap.keySet())
            );
        }
        
        // Copy tester files to submissions.
        int copyCount = copyToSubs();
        // Log
        System.out.printf(
            "%s Copied %d tester file%s%n",
            TextColor.SUCCESS.getActionTag(),
            copyCount,
            copyCount == 1? "": "s"
        );


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

    private int copyToSubs() throws IOException {
        int copyCount = 0;
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
                    TesterFile testerFile = testerFileMap.get(question);

                    Path dest = p.getParent().resolve(testerFile.origin().toPath().getFileName());
                    Files.copy(testerFile.origin().toPath(), dest);
                    copyCount++;
                }
            }
        }
        return copyCount;
    }

}
