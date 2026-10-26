package autogradesys.preflight;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import autogradesys.preflight.PreflightFinding.Category;
import autogradesys.preflight.PreflightFinding.Severity;

/**
 * Read-only check of a folder of student submissions against the instructor's starter template.
 *
 * The expected layout is derived only from the template: every direct sub-folder is a question,
 * every {@code .java} file directly inside it is a required file, and every other file is an
 * expected resource. The check never compiles or runs anything, never follows symbolic links, and
 * never writes, renames, moves or deletes. It does not grade; "ready" only means the layout
 * matches the template.
 */
public final class SubmissionPreflight {

    private static final int BLANK_CHECK_BYTES = 8192;

    private final Path submissions;
    private final String templateName;
    private final Map<String, Question> questions;
    private final List<PreflightFinding> findings = new ArrayList<>();

    private SubmissionPreflight(Path submissions, String templateName, Map<String, Question> questions) {
        this.submissions = submissions;
        this.templateName = templateName;
        this.questions = questions;
    }

    /** What the template says one question folder contains. */
    private record Question(String name, TreeSet<String> javaFiles, TreeSet<String> resources) {
    }

    /**
     * Compares every submission folder with the template.
     *
     * @param template    The starter template folder (its direct sub-folders are the questions).
     * @param submissions The folder whose direct children are the student submissions.
     * @return The report. If the template or submissions folder is unusable, the report is fatal
     *         and no submission is scanned.
     */
    public static PreflightReport check(Path template, Path submissions) {
        String templateName = nameOf(template);
        List<PreflightFinding> fatal = new ArrayList<>();

        Map<String, Question> questions = readTemplate(template, templateName, fatal);
        if (fatal.isEmpty() && !isReadableDirectory(submissions)) {
            fatal.add(new PreflightFinding("", Category.SUBMISSIONS_INVALID, Severity.FATAL,
                    submissions.toString(), "the submissions folder is missing, unreadable or not a folder",
                    "Choose the folder that contains one folder per student"));
        }
        if (!fatal.isEmpty()) {
            return new PreflightReport(templateName, List.of(), fatal);
        }

        SubmissionPreflight run = new SubmissionPreflight(submissions, templateName, questions);
        List<String> students = run.scanAll();
        return new PreflightReport(templateName, students, run.findings);
    }

    // Template

    private static Map<String, Question> readTemplate(Path template, String templateName,
            List<PreflightFinding> fatal) {
        String fix = "Set Starting Folder to the starter template (e.g. RenameToYourStudentID)";
        Map<String, Question> questions = new TreeMap<>();

        if (!isReadableDirectory(template)) {
            fatal.add(new PreflightFinding("", Category.TEMPLATE_INVALID, Severity.FATAL, template.toString(),
                    "the template is missing, unreadable or not a folder", fix));
            return questions;
        }

        try {
            for (Path dir : sortedChildren(template)) {
                if (isJunk(nameOf(dir)) || !isRealDirectory(dir)) {
                    continue;
                }
                Question q = new Question(nameOf(dir), new TreeSet<>(), new TreeSet<>());
                for (Path file : sortedChildren(dir)) {
                    String name = nameOf(file);
                    if (isJunk(name) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                        continue;
                    }
                    (name.endsWith(".java") ? q.javaFiles() : q.resources()).add(name);
                }
                questions.put(q.name(), q);
            }
        } catch (IOException | SecurityException e) {
            fatal.add(new PreflightFinding("", Category.TEMPLATE_INVALID, Severity.FATAL, template.toString(),
                    "the template could not be read (" + e.getMessage() + ")", fix));
            return questions;
        }

        if (questions.isEmpty()) {
            fatal.add(new PreflightFinding("", Category.TEMPLATE_INVALID, Severity.FATAL, template.toString(),
                    "the template has no question folders", fix));
        }
        return questions;
    }

    // Submissions

    private List<String> scanAll() {
        TreeSet<String> names = new TreeSet<>();
        List<Path> entries;
        try {
            entries = sortedChildren(submissions);
        } catch (IOException | SecurityException e) {
            add("", Category.SUBMISSIONS_INVALID, Severity.FATAL, submissions.toString(),
                    "the submissions folder could not be read (" + e.getMessage() + ")",
                    "Check the folder permissions");
            return List.of();
        }

        for (Path entry : entries) {
            String name = nameOf(entry);
            if (isJunk(name)) {
                add("", Category.IGNORED_JUNK, Severity.NOTE, name, "ignored", "");
            } else if (Files.isSymbolicLink(entry)) {
                names.add(name);
                add(name, Category.SYMLINK_NOT_FOLLOWED, Severity.ERROR, name,
                        "a symbolic link, so it was not opened",
                        "Replace the link with the real submission folder");
            } else if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                names.add(name);
                scanStudent(name, entry);
            } else if (isZip(name)) {
                names.add(name);
                add(name, Category.UNEXTRACTED_ARCHIVE, Severity.ERROR, name, "an archive, not a folder",
                        "Extract " + name + " into a folder before grading");
            } else {
                add("", Category.EXTRA_FILE, Severity.WARNING, name,
                        "a loose file in the submissions folder, not a student folder",
                        "Move it elsewhere if it is not a submission");
            }
        }
        return List.copyOf(names);
    }

    private void scanStudent(String student, Path dir) {
        if (student.equals(templateName)) {
            add(student, Category.TEMPLATE_COPY, Severity.WARNING, student + "/",
                    "has the template's name, so it looks like the starter template",
                    "Remove it from the submissions folder, or it would be graded as a student");
        }
        scanLayout(student, dir, true);
    }

    /** Scans one student folder; {@code allowWrapper} lets it look one level down for the real work. */
    private void scanLayout(String student, Path dir, boolean allowWrapper) {
        List<Path> entries;
        try {
            entries = sortedChildren(dir);
        } catch (IOException | SecurityException e) {
            add(student, Category.UNREADABLE, Severity.ERROR, rel(dir), "could not be read (" + e.getMessage() + ")",
                    "Check the folder permissions");
            return;
        }

        List<Path> items = new ArrayList<>();
        for (Path entry : entries) {
            String name = nameOf(entry);
            if (isJunk(name)) {
                add(student, Category.IGNORED_JUNK, Severity.NOTE, rel(entry), "ignored", "");
            } else if (Files.isSymbolicLink(entry)) {
                add(student, Category.SYMLINK_NOT_FOLLOWED, Severity.ERROR, rel(entry),
                        "a symbolic link, so it was not opened", "Replace the link with the real file or folder");
            } else {
                items.add(entry);
            }
        }

        if (items.size() == 1 && isZip(nameOf(items.get(0))) && Files.isRegularFile(items.get(0), LinkOption.NOFOLLOW_LINKS)) {
            String zip = nameOf(items.get(0));
            add(student, Category.UNEXTRACTED_ARCHIVE, Severity.ERROR, rel(items.get(0)),
                    "the only content is an archive", "Extract " + zip + " into this folder before grading");
            return;
        }

        Map<String, Path> dirs = new TreeMap<>();
        List<Path> files = new ArrayList<>();
        for (Path item : items) {
            if (Files.isDirectory(item, LinkOption.NOFOLLOW_LINKS)) {
                dirs.put(nameOf(item), item);
            } else {
                files.add(item);
            }
        }

        Map<String, Path> matched = matchNames(questions.keySet(), dirs);
        if (matched.isEmpty() && allowWrapper) {
            Path wrapper = findWrapper(dirs);
            if (wrapper != null) {
                add(student, Category.WRAPPED_FOLDER, Severity.ERROR, rel(wrapper) + "/",
                        "the work is inside an extra folder",
                        "Move the contents of " + rel(wrapper) + "/ up into " + rel(dir) + "/");
                for (Path other : dirs.values()) {
                    if (!other.equals(wrapper)) {
                        addExtra(student, other, true);
                    }
                }
                for (Path file : files) {
                    addExtra(student, file, false);
                }
                scanLayout(student, wrapper, false);
                return;
            }
        }

        for (Question q : questions.values()) {
            Path questionDir = matched.get(q.name());
            if (questionDir == null) {
                List<String> expected = new ArrayList<>(q.javaFiles());
                add(student, Category.MISSING_QUESTION, Severity.ERROR, rel(dir) + "/" + q.name() + "/",
                        "the question folder is missing",
                        "Add " + q.name() + "/" + (expected.isEmpty() ? "" : " (" + String.join(", ", expected) + ")"));
            } else {
                if (!nameOf(questionDir).equals(q.name())) {
                    add(student, Category.MISNAMED, Severity.ERROR, rel(questionDir) + "/",
                            "expected the name " + q.name(), "Rename " + nameOf(questionDir) + " to " + q.name());
                }
                scanQuestion(student, q, questionDir);
            }
        }

        for (Path d : dirs.values()) {
            if (!matched.containsValue(d)) {
                addExtra(student, d, true);
            }
        }
        for (Path file : files) {
            addExtra(student, file, false);
        }
    }

    private void scanQuestion(String student, Question q, Path questionDir) {
        List<Path> entries;
        try {
            entries = sortedChildren(questionDir);
        } catch (IOException | SecurityException e) {
            add(student, Category.UNREADABLE, Severity.ERROR, rel(questionDir) + "/",
                    "could not be read (" + e.getMessage() + ")", "Check the folder permissions");
            return;
        }

        Map<String, Path> regular = new TreeMap<>();
        List<Path> subfolders = new ArrayList<>();
        for (Path entry : entries) {
            String name = nameOf(entry);
            if (isJunk(name)) {
                add(student, Category.IGNORED_JUNK, Severity.NOTE, rel(entry), "ignored", "");
            } else if (Files.isSymbolicLink(entry)) {
                add(student, Category.SYMLINK_NOT_FOLLOWED, Severity.ERROR, rel(entry),
                        "a symbolic link, so it was not opened", "Replace the link with the real file");
            } else if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                subfolders.add(entry);
            } else {
                regular.put(name, entry);
            }
        }

        Map<String, Path> matchedJava = matchNames(q.javaFiles(), regular);
        for (String required : q.javaFiles()) {
            Path file = matchedJava.get(required);
            String where = rel(questionDir) + "/" + required;
            if (file == null) {
                add(student, Category.MISSING_FILE, Severity.ERROR, where, "the file is missing",
                        "Add " + required + " to " + q.name() + "/");
                continue;
            }
            if (!nameOf(file).equals(required)) {
                add(student, Category.MISNAMED, Severity.ERROR, rel(file), "expected the name " + required,
                        "Rename " + nameOf(file) + " to " + required);
            }
            if (isBlank(file)) {
                add(student, Category.EMPTY_FILE, Severity.ERROR, rel(file), "the file is empty",
                        "Ask for the real " + required);
            }
        }

        for (String resource : q.resources()) {
            if (!regular.containsKey(resource)) {
                add(student, Category.MISSING_RESOURCE, Severity.WARNING, rel(questionDir) + "/" + resource,
                        "a resource file from the template is missing", "Add " + resource + " to " + q.name() + "/");
            }
        }

        for (Map.Entry<String, Path> e : regular.entrySet()) {
            if (e.getKey().endsWith(".java") && !matchedJava.containsValue(e.getValue())) {
                addExtra(student, e.getValue(), false);
            }
        }
        for (Path sub : subfolders) {
            addExtra(student, sub, true);
        }
    }

    // Helpers

    /**
     * Pairs each expected name with the actual entry of that exact name, or else the single entry
     * that differs only by letter case.
     */
    private static Map<String, Path> matchNames(Iterable<String> expected, Map<String, Path> actual) {
        Map<String, Path> matched = new TreeMap<>();
        for (String name : expected) {
            Path exact = actual.get(name);
            if (exact != null) {
                matched.put(name, exact);
                continue;
            }
            List<Path> caseOnly = actual.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(name))
                    .map(Map.Entry::getValue)
                    .toList();
            if (caseOnly.size() == 1) {
                matched.put(name, caseOnly.get(0));
            }
        }
        return matched;
    }

    /** The single non-junk sub-folder that contains at least one template question, if exactly one does. */
    private Path findWrapper(Map<String, Path> dirs) {
        List<Path> candidates = new ArrayList<>();
        for (Path candidate : dirs.values()) {
            try {
                Map<String, Path> inside = new TreeMap<>();
                for (Path child : sortedChildren(candidate)) {
                    if (!isJunk(nameOf(child)) && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                        inside.put(nameOf(child), child);
                    }
                }
                if (!matchNames(questions.keySet(), inside).isEmpty()) {
                    candidates.add(candidate);
                }
            } catch (IOException | SecurityException e) {
                // Unreadable candidates cannot be a wrapper; they are reported as extras.
            }
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private void addExtra(String student, Path path, boolean folder) {
        add(student, Category.EXTRA_FILE, Severity.WARNING, rel(path) + (folder ? "/" : ""),
                "not in the template", "Graders may ignore it; check it is not part of the answer");
    }

    private void add(String student, Category category, Severity severity, String path, String message, String fix) {
        findings.add(new PreflightFinding(student, category, severity, path, message, fix));
    }

    private String rel(Path path) {
        return submissions.relativize(path).toString().replace('\\', '/');
    }

    private static boolean isBlank(Path file) {
        try {
            if (Files.size(file) == 0) {
                return true;
            }
            try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                byte[] head = in.readNBytes(BLANK_CHECK_BYTES);
                for (byte b : head) {
                    if (!Character.isWhitespace(b)) {
                        return false;
                    }
                }
                return head.length < BLANK_CHECK_BYTES;
            }
        } catch (IOException | SecurityException e) {
            return false;
        }
    }

    private static List<Path> sortedChildren(Path dir) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.sorted(Comparator.comparing(p -> nameOf(p))).toList();
        }
    }

    private static boolean isReadableDirectory(Path dir) {
        return Files.isDirectory(dir) && Files.isReadable(dir);
    }

    private static boolean isRealDirectory(Path dir) {
        return Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean isJunk(String name) {
        return name.startsWith(".") || name.equals("__MACOSX") || name.endsWith(".class");
    }

    private static boolean isZip(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    private static String nameOf(Path path) {
        Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
    }
}
