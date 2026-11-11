package autogradesys.submission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Looks through the folder of submissions and goes through the zip files one by one.
// Files that are not valid submission zips are kept in a separate list so we can tell the grader.

public class SubmissionIterator implements Iterable<Submission> {
    private final Map<String, Submission> submissions = new LinkedHashMap<>(); // Linked Hashmap needed to maintain order
    private final List<String> skipped = new ArrayList<>();
    private final List<String> duplicates = new ArrayList<>();

    public SubmissionIterator(Path folder, Pattern zipNamePattern) throws IOException {
        if (!Files.isDirectory(folder)) {
            throw new IllegalArgumentException("Not a folder: " + folder);
        }

        List<Path> files;
        try (Stream<Path> stream = Files.list(folder)) {
            files = stream.sorted().collect(Collectors.toList()); //naming order
        }

        for (Path file : files) {
            String name = file.getFileName().toString();
            if (name.startsWith(".")) {
                continue;
            } // for ignoring system generated junk files. MACOS generated files for example

            Matcher matcher = zipNamePattern.matcher(name);
            if (Files.isRegularFile(file) && matcher.matches()) {
                addSubmission(new Submission(matcher.group(1), file));
            } else {
                skipped.add(name);
            } // name matching against pattern, deviations go into skipped
        }
    }

    private void addSubmission(Submission submission) {
        String key = submission.getUsername().toLowerCase();
        Submission earlier = submissions.put(key, submission); // put to replace

        if (earlier != null) {
            duplicates.add("Two zips for " + submission.getUsername() + ": "
                    + earlier.getZipPath().getFileName() + " and " + submission.getZipPath().getFileName()
                    + ". Only " + submission.getZipPath().getFileName() + " is used."); // Later one wins and used
        }
    }

    @Override
    public Iterator<Submission> iterator() {
        return submissions.values().iterator();
    }

    public int size() {
        return submissions.size();
    }

    public List<String> getSkipped() {
        return skipped;
    }

    public List<String> getDuplicates() {
        return duplicates;
    }
}
