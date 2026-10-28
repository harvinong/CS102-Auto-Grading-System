package autogradesys.preflight;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    // How many bytes of a file we read to decide whether it is only whitespace.
    private static final int BLANK_CHECK_BYTES = 8192;

    // The folder whose children are the student submissions.
    private final Path submissions;
    // The template's own folder name (e.g. RenameToYourStudentID), used to spot copies of it.
    private final String templateName;
    // What the template expects, keyed by question folder name (sorted for stable output).
    private final Map<String, Question> questions;
    // Everything we notice is collected here, in the order we notice it.
    private final List<PreflightFinding> findings = new ArrayList<>();

    // Private: callers go through the static check(...) method instead.
    private SubmissionPreflight(Path submissions, String templateName, Map<String, Question> questions) {
        // Remember the submissions folder for building relative paths.
        this.submissions = submissions;
        // Remember the template name for the "template copy" test.
        this.templateName = templateName;
        // Remember the expected structure.
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
        // Name used in the report heading.
        String templateName = nameOf(template);
        // Problems that make the whole check impossible are collected here.
        List<PreflightFinding> fatal = new ArrayList<>();

        // Learn the expected layout from the template (adds to "fatal" if the template is unusable).
        Map<String, Question> questions = readTemplate(template, templateName, fatal);
        // Only bother with the submissions folder if the template is fine.
        if (fatal.isEmpty() && !isReadableDirectory(submissions)) {
            // Report it clearly instead of printing an empty "all ready" result.
            fatal.add(new PreflightFinding("", Category.SUBMISSIONS_INVALID, Severity.FATAL,
                    submissions.toString(), "the submissions folder is missing, unreadable or not a folder",
                    "Choose the folder that contains one folder per student"));
        }
        // A fatal problem means we stop here and scan nothing.
        if (!fatal.isEmpty()) {
            return new PreflightReport(templateName, List.of(), fatal);
        }

        // Create a one-off scanner holding this run's state.
        SubmissionPreflight run = new SubmissionPreflight(submissions, templateName, questions);
        // Scan every submission; returns the sorted list of discovered submission names.
        List<String> students = run.scanAll();
        // Bundle the results.
        return new PreflightReport(templateName, students, run.findings);
    }

    // Template

    // Reads the template folder into a map of question name -> expected files; reports fatal problems.
    private static Map<String, Question> readTemplate(Path template, String templateName,
            List<PreflightFinding> fatal) {
        // The same advice is attached to every template problem.
        String fix = "Set Starting Folder to the starter template (e.g. RenameToYourStudentID)";
        // TreeMap keeps the questions sorted by name.
        Map<String, Question> questions = new TreeMap<>();
        // Question folders that hold no .java directly but whose sub-folders do (a sign the folder is too high).
        List<String> nestedTemplates = new ArrayList<>();
        // Total number of required .java files found across all questions.
        int javaCount = 0;

        // The template must exist, be a folder and be readable.
        if (!isReadableDirectory(template)) {
            fatal.add(new PreflightFinding("", Category.TEMPLATE_INVALID, Severity.FATAL, template.toString(),
                    "the template is missing, unreadable or not a folder", fix));
            return questions;
        }

        try {
            // Visit each direct child of the template in name order.
            for (Path dir : sortedChildren(template)) {
                // Skip junk and anything that is not a real (non-link) folder.
                if (isJunk(nameOf(dir)) || !isRealDirectory(dir)) {
                    continue;
                }
                // Start the expectation record for this question folder.
                Question q = new Question(nameOf(dir), new TreeSet<>(), new TreeSet<>());
                // Does one of this folder's sub-folders directly contain .java files?
                boolean hasNestedJava = false;
                // Look at everything inside the question folder.
                for (Path entry : sortedChildren(dir)) {
                    // The entry's file name, e.g. Q1a.java.
                    String name = nameOf(entry);
                    // Ignore junk such as .DS_Store.
                    if (isJunk(name)) {
                        continue;
                    }
                    // A sub-folder: remember if it holds Java, then move on.
                    if (isRealDirectory(entry)) {
                        hasNestedJava |= containsJava(entry);
                        continue;
                    }
                    // Links and special files are not part of the expected layout.
                    if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                        continue;
                    }
                    // .java files are required files; everything else is an expected resource.
                    if (name.endsWith(".java")) {
                        q.javaFiles().add(name);
                        javaCount++;
                    } else {
                        q.resources().add(name);
                    }
                }
                // Record the question and note whether it looks like a whole template one level down.
                questions.put(q.name(), q);
                if (hasNestedJava) {
                    nestedTemplates.add(q.name());
                }
            }
        } catch (IOException | SecurityException e) {
            // Could not read part of the template: say so rather than guessing.
            fatal.add(new PreflightFinding("", Category.TEMPLATE_INVALID, Severity.FATAL, template.toString(),
                    "the template could not be read (" + e.getMessage() + ")", fix));
            return questions;
        }

        // No question folders at all: nothing to compare with.
        if (questions.isEmpty()) {
            fatal.add(new PreflightFinding("", Category.TEMPLATE_INVALID, Severity.FATAL, template.toString(),
                    "the template has no question folders", fix));
        // No Java files directly in any question, but a folder below holds them: the Starting Folder is one level too high.
        } else if (javaCount == 0 && !nestedTemplates.isEmpty()) {
            fatal.add(new PreflightFinding("", Category.TEMPLATE_INVALID, Severity.FATAL, template.toString(),
                    "looks one level too high: its folder " + String.join(", ", nestedTemplates)
                            + " holds the question folders",
                    "Set Starting Folder to " + template.resolve(nestedTemplates.get(0))));
        }
        // Hand back whatever was learned (the caller checks "fatal" first).
        return questions;
    }

    // Submissions

    // Looks at every entry in the submissions folder and returns the sorted submission names.
    private List<String> scanAll() {
        // TreeSet gives sorted, de-duplicated names.
        TreeSet<String> names = new TreeSet<>();
        // The folder's children, sorted by name for deterministic output.
        List<Path> entries;
        try {
            entries = sortedChildren(submissions);
        } catch (IOException | SecurityException e) {
            // Unreadable folder: report it as fatal rather than showing zero students.
            add("", Category.SUBMISSIONS_INVALID, Severity.FATAL, submissions.toString(),
                    "the submissions folder could not be read (" + e.getMessage() + ")",
                    "Check the folder permissions");
            return List.of();
        }

        // Lower-case names of the real folders, to recognise a .zip that was already extracted.
        Set<String> folderNames = new HashSet<>();
        for (Path entry : entries) {
            // Only count real, non-junk folders (links are not followed).
            if (!isJunk(nameOf(entry)) && Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                folderNames.add(nameOf(entry).toLowerCase(Locale.ROOT));
            }
        }

        // Handle each entry according to what it is.
        for (Path entry : entries) {
            // Entry's file name.
            String name = nameOf(entry);
            if (isJunk(name)) {
                // Clutter is counted as a note, never as a student.
                add("", Category.IGNORED_JUNK, Severity.NOTE, name, "ignored", "");
            } else if (Files.isSymbolicLink(entry)) {
                // A link is still a student we must not drop, but we refuse to open it.
                names.add(name);
                add(name, Category.SYMLINK_NOT_FOLLOWED, Severity.ERROR, name,
                        "a symbolic link, so it was not opened",
                        "Replace the link with the real submission folder");
            } else if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                // A normal student folder: scan its layout.
                names.add(name);
                scanStudent(name, entry);
            } else if (isZip(name) && folderNames.contains(stem(name).toLowerCase(Locale.ROOT))) {
                // The zip sits beside the folder it was extracted into: not a separate student.
                add("", Category.ARCHIVE_ALREADY_EXTRACTED, Severity.NOTE, name,
                        "not counted: its extracted folder " + stem(name) + "/ is checked instead", "");
            } else if (isZip(name)) {
                // A zip with no extracted folder is a student we cannot look inside.
                names.add(name);
                add(name, Category.UNEXTRACTED_ARCHIVE, Severity.ERROR, name, "an archive, not a folder",
                        "Extract " + name + " into a folder before grading");
            } else {
                // Any other loose file is not a student; warn about it at run level.
                add("", Category.EXTRA_FILE, Severity.WARNING, name,
                        "a loose file in the submissions folder, not a student folder",
                        "Move it elsewhere if it is not a submission");
            }
        }
        // Immutable copy of the sorted names.
        return List.copyOf(names);
    }

    // Checks one student folder (after the template-copy test).
    private void scanStudent(String student, Path dir) {
        // A folder named exactly like the template is probably the blank starter, not a student.
        if (student.equals(templateName)) {
            add(student, Category.TEMPLATE_COPY, Severity.WARNING, student + "/",
                    "has the template's name, so it looks like the starter template",
                    "Remove it from the submissions folder, or it would be graded as a student");
        }
        // Scan the layout, allowing one level of wrapper folder.
        scanLayout(student, dir, true);
    }

    /** Scans one student folder; {@code allowWrapper} lets it look one level down for the real work. */
    private void scanLayout(String student, Path dir, boolean allowWrapper) {
        // Children of the student folder, sorted.
        List<Path> entries;
        try {
            entries = sortedChildren(dir);
        } catch (IOException | SecurityException e) {
            // Unreadable folder: flag it so the student is not silently "ready".
            add(student, Category.UNREADABLE, Severity.ERROR, rel(dir), "could not be read (" + e.getMessage() + ")",
                    "Check the folder permissions");
            return;
        }

        // Entries that are neither junk nor links; these are the real content.
        List<Path> items = new ArrayList<>();
        for (Path entry : entries) {
            // File name of this entry.
            String name = nameOf(entry);
            if (isJunk(name)) {
                // Count clutter as a note.
                add(student, Category.IGNORED_JUNK, Severity.NOTE, rel(entry), "ignored", "");
            } else if (Files.isSymbolicLink(entry)) {
                // Never follow links; just report them.
                add(student, Category.SYMLINK_NOT_FOLLOWED, Severity.ERROR, rel(entry),
                        "a symbolic link, so it was not opened", "Replace the link with the real file or folder");
            } else {
                // Genuine content to examine.
                items.add(entry);
            }
        }

        // A folder whose only content is one .zip has not been extracted.
        if (items.size() == 1 && isZip(nameOf(items.get(0))) && Files.isRegularFile(items.get(0), LinkOption.NOFOLLOW_LINKS)) {
            // The archive's file name for the fix text.
            String zip = nameOf(items.get(0));
            add(student, Category.UNEXTRACTED_ARCHIVE, Severity.ERROR, rel(items.get(0)),
                    "the only content is an archive", "Extract " + zip + " into this folder before grading");
            return;
        }

        // Split the content into sub-folders (by name) and plain files.
        Map<String, Path> dirs = new TreeMap<>();
        List<Path> files = new ArrayList<>();
        for (Path item : items) {
            if (Files.isDirectory(item, LinkOption.NOFOLLOW_LINKS)) {
                dirs.put(nameOf(item), item);
            } else {
                files.add(item);
            }
        }

        // Pair each template question with the sub-folder of that name (exact or case-only difference).
        Map<String, Path> matched = matchNames(questions.keySet(), dirs);
        // No question folder found directly here: maybe the work is inside one extra folder.
        if (matched.isEmpty() && allowWrapper) {
            // The single folder below that holds the questions, if there is exactly one.
            Path wrapper = findWrapper(dirs);
            if (wrapper != null) {
                // Report the wrapper once instead of one MISSING_QUESTION per question.
                add(student, Category.WRAPPED_FOLDER, Severity.ERROR, rel(wrapper) + "/",
                        "the work is inside an extra folder",
                        "Move the contents of " + rel(wrapper) + "/ up into " + rel(dir) + "/");
                // Siblings of the wrapper are extras.
                for (Path other : dirs.values()) {
                    if (!other.equals(wrapper)) {
                        addExtra(student, other, true);
                    }
                }
                // Loose files next to the wrapper are extras too.
                for (Path file : files) {
                    addExtra(student, file, false);
                }
                // Keep checking inside the wrapper so other problems still show (no second wrapper level).
                scanLayout(student, wrapper, false);
                return;
            }
        }

        // Compare each expected question with what the student has.
        for (Question q : questions.values()) {
            // The student's folder for this question, if any.
            Path questionDir = matched.get(q.name());
            if (questionDir == null) {
                // The whole question folder is absent: list the files that belong in it.
                List<String> expected = new ArrayList<>(q.javaFiles());
                add(student, Category.MISSING_QUESTION, Severity.ERROR, rel(dir) + "/" + q.name() + "/",
                        "the question folder is missing",
                        "Add " + q.name() + "/" + (expected.isEmpty() ? "" : " (" + String.join(", ", expected) + ")"));
            } else {
                // Found, but spelled differently (e.g. q1 instead of Q1)?
                if (!nameOf(questionDir).equals(q.name())) {
                    add(student, Category.MISNAMED, Severity.ERROR, rel(questionDir) + "/",
                            "expected the name " + q.name(), "Rename " + nameOf(questionDir) + " to " + q.name());
                }
                // Check the files inside this question folder.
                scanQuestion(student, q, questionDir);
            }
        }

        // Folders the template does not have are extras.
        for (Path d : dirs.values()) {
            if (!matched.containsValue(d)) {
                addExtra(student, d, true);
            }
        }
        // So are loose files next to the question folders.
        for (Path file : files) {
            addExtra(student, file, false);
        }
    }

    // Checks the files inside one question folder against the template's expectations.
    private void scanQuestion(String student, Question q, Path questionDir) {
        // Children of the question folder, sorted.
        List<Path> entries;
        try {
            entries = sortedChildren(questionDir);
        } catch (IOException | SecurityException e) {
            // Unreadable question folder: flag it.
            add(student, Category.UNREADABLE, Severity.ERROR, rel(questionDir) + "/",
                    "could not be read (" + e.getMessage() + ")", "Check the folder permissions");
            return;
        }

        // Plain files by name, and sub-folders, after removing junk and links.
        Map<String, Path> regular = new TreeMap<>();
        List<Path> subfolders = new ArrayList<>();
        for (Path entry : entries) {
            // File name of this entry.
            String name = nameOf(entry);
            if (isJunk(name)) {
                // Count clutter as a note.
                add(student, Category.IGNORED_JUNK, Severity.NOTE, rel(entry), "ignored", "");
            } else if (Files.isSymbolicLink(entry)) {
                // Never follow links; just report them.
                add(student, Category.SYMLINK_NOT_FOLLOWED, Severity.ERROR, rel(entry),
                        "a symbolic link, so it was not opened", "Replace the link with the real file");
            } else if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                // Folders inside a question are not expected.
                subfolders.add(entry);
            } else {
                // A normal file.
                regular.put(name, entry);
            }
        }

        // Pair each required .java name with the student's file (exact or case-only difference).
        Map<String, Path> matchedJava = matchNames(q.javaFiles(), regular);
        for (String required : q.javaFiles()) {
            // The student's file for this required name, if any.
            Path file = matchedJava.get(required);
            // Where the file should have been, for messages.
            String where = rel(questionDir) + "/" + required;
            if (file == null) {
                // Not there at all.
                add(student, Category.MISSING_FILE, Severity.ERROR, where, "the file is missing",
                        "Add " + required + " to " + q.name() + "/");
                continue;
            }
            // Present but with the wrong letter case.
            if (!nameOf(file).equals(required)) {
                add(student, Category.MISNAMED, Severity.ERROR, rel(file), "expected the name " + required,
                        "Rename " + nameOf(file) + " to " + required);
            }
            // Present but with nothing in it.
            if (isBlank(file)) {
                add(student, Category.EMPTY_FILE, Severity.ERROR, rel(file), "the file is empty",
                        "Ask for the real " + required);
            }
        }

        // Non-Java files the template ships (e.g. persons.txt) should also be present.
        for (String resource : q.resources()) {
            if (!regular.containsKey(resource)) {
                add(student, Category.MISSING_RESOURCE, Severity.WARNING, rel(questionDir) + "/" + resource,
                        "a resource file from the template is missing", "Add " + resource + " to " + q.name() + "/");
            }
        }

        // Any .java file not matched to a required name is an extra.
        for (Map.Entry<String, Path> e : regular.entrySet()) {
            if (e.getKey().endsWith(".java") && !matchedJava.containsValue(e.getValue())) {
                addExtra(student, e.getValue(), false);
            }
        }
        // Sub-folders inside a question are extras too.
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
        // Expected name -> the actual path that satisfies it.
        Map<String, Path> matched = new TreeMap<>();
        for (String name : expected) {
            // An exact spelling always wins.
            Path exact = actual.get(name);
            if (exact != null) {
                matched.put(name, exact);
                continue;
            }
            // Otherwise look for entries that differ only by upper/lower case.
            List<Path> caseOnly = actual.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(name))
                    .map(Map.Entry::getValue)
                    .toList();
            // Accept the case-only match only when it is unambiguous.
            if (caseOnly.size() == 1) {
                matched.put(name, caseOnly.get(0));
            }
        }
        return matched;
    }

    /** The single non-junk sub-folder that contains at least one template question, if exactly one does. */
    private Path findWrapper(Map<String, Path> dirs) {
        // Folders that look like they hold the real work.
        List<Path> candidates = new ArrayList<>();
        for (Path candidate : dirs.values()) {
            try {
                // The candidate's own sub-folders, by name.
                Map<String, Path> inside = new TreeMap<>();
                for (Path child : sortedChildren(candidate)) {
                    if (!isJunk(nameOf(child)) && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                        inside.put(nameOf(child), child);
                    }
                }
                // It qualifies if at least one template question appears inside it.
                if (!matchNames(questions.keySet(), inside).isEmpty()) {
                    candidates.add(candidate);
                }
            } catch (IOException | SecurityException e) {
                // Unreadable candidates cannot be a wrapper; they are reported as extras.
            }
        }
        // Only an unambiguous single candidate counts as the wrapper.
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    // Records "this is not in the template" for a file or folder.
    private void addExtra(String student, Path path, boolean folder) {
        add(student, Category.EXTRA_FILE, Severity.WARNING, rel(path) + (folder ? "/" : ""),
                "not in the template", "Graders may ignore it; check it is not part of the answer");
    }

    // Appends one finding to the list.
    private void add(String student, Category category, Severity severity, String path, String message, String fix) {
        findings.add(new PreflightFinding(student, category, severity, path, message, fix));
    }

    // Path relative to the submissions folder, always with forward slashes.
    private String rel(Path path) {
        return submissions.relativize(path).toString().replace('\\', '/');
    }

    // True when the file is empty or only whitespace (reads at most BLANK_CHECK_BYTES, read-only).
    private static boolean isBlank(Path file) {
        try {
            // Zero bytes is trivially empty.
            if (Files.size(file) == 0) {
                return true;
            }
            // Open without following links; the stream is closed automatically.
            try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                // Read only the start of the file.
                byte[] head = in.readNBytes(BLANK_CHECK_BYTES);
                for (byte b : head) {
                    // Any visible character means the file has content.
                    if (!Character.isWhitespace(b)) {
                        return false;
                    }
                }
                // All whitespace so far; only call it blank if we saw the whole file.
                return head.length < BLANK_CHECK_BYTES;
            }
        } catch (IOException | SecurityException e) {
            // If we cannot read it, do not claim it is empty.
            return false;
        }
    }

    // True when the folder directly contains at least one .java file.
    private static boolean containsJava(Path dir) {
        try {
            return sortedChildren(dir).stream()
                    .anyMatch(p -> nameOf(p).endsWith(".java") && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS));
        } catch (IOException | SecurityException e) {
            // Unreadable folders are treated as having no Java.
            return false;
        }
    }

    // Lists a folder's children sorted by name (so output never depends on the file system's order).
    private static List<Path> sortedChildren(Path dir) throws IOException {
        // Files.list holds a directory handle, so close it with try-with-resources.
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.sorted(Comparator.comparing(p -> nameOf(p))).toList();
        }
    }

    // A folder we are allowed to look inside.
    private static boolean isReadableDirectory(Path dir) {
        return Files.isDirectory(dir) && Files.isReadable(dir);
    }

    // A genuine folder, not a symbolic link to one.
    private static boolean isRealDirectory(Path dir) {
        return Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS);
    }

    // Names we ignore: hidden files, macOS resource folders and compiled classes.
    private static boolean isJunk(String name) {
        return name.startsWith(".") || name.equals("__MACOSX") || name.endsWith(".class");
    }

    // True for .zip names in any letter case.
    private static boolean isZip(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    // "frank.zip" -> "frank".
    private static String stem(String zipName) {
        return zipName.substring(0, zipName.length() - ".zip".length());
    }

    // Last part of a path; falls back to the whole path for roots like "/".
    private static String nameOf(Path path) {
        Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
    }
}
