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
 * Read-only check of a folder of student submissions against the instructor's
 * starter template.
 *
 * The expected layout is derived only from the template: every direct
 * sub-folder is a question,
 * every {@code .java} file directly inside it is a required file, and every
 * other file is an
 * expected resource. The check never compiles or runs anything, never follows
 * symbolic links, and
 * never writes, renames, moves or deletes. It does not grade; "ready" only
 * means the layout
 * matches the template.
 */
// Compares the submitted directory layout with the expected questions before
// grading starts.
public final class SubmissionPreflight {

    // How many bytes of a file we read to decide whether it is only whitespace.
    private static final int BLANK_CHECK_BYTES = 8192;

    // The folder whose children are the student submissions.
    private final Path submissions;
    // The template's own folder name (e.g. RenameToYourStudentID), used to spot
    // copies of it.
    private final String templateName;
    // What the template expects, keyed by question folder name (sorted for stable
    // output).
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
    private record Question(
            String name,
            TreeSet<String> javaFiles,
            TreeSet<String> resources,
            TreeSet<String> folders) {
    }

    /**
     * Compares every submission folder with the template.
     *
     * @param template    The starter template folder (its direct sub-folders are
     *                    the questions).
     * @param submissions The folder whose direct children are the student
     *                    submissions.
     * @return The report. If the template or submissions folder is unusable, the
     *         report is fatal
     *         and no submission is scanned.
     */
    public static PreflightReport check(Path template, Path submissions) {
        // Name used in the report heading.
        String templateName = nameOf(template);
        // Problems that make the whole check impossible are collected here.
        List<PreflightFinding> fatal = new ArrayList<>();

        // Learn the expected layout from the template (adds to "fatal" if the template
        // is unusable).
        Map<String, Question> questions = readTemplate(template, templateName, fatal);
        // Only bother with the submissions folder if the template is fine.
        if (fatal.isEmpty() && !isReadableDirectory(submissions)) {
            // Report it clearly instead of printing an empty "all ready" result.
            // Run-level finding (empty student name), FATAL so PreflightReport.fatal()
            // stops the menu before grading.
            fatal.add(new PreflightFinding("", Category.SUBMISSIONS_INVALID, Severity.FATAL,
                    submissions.toString(), "the submissions folder is missing, unreadable or not a folder",
                    "Choose the folder that contains one folder per student"));
        }
        // A fatal problem means we stop here and scan nothing.
        if (!fatal.isEmpty()) {
            // No students were scanned, so the report carries an empty student list and
            // only the fatal findings.
            return new PreflightReport(templateName, List.of(), fatal);
        }

        // Create a one-off scanner holding this run's state.
        SubmissionPreflight run = new SubmissionPreflight(submissions, templateName, questions);
        // Scan every submission; returns the sorted list of discovered submission
        // names.
        List<String> students = run.scanAll();
        // Bundle the results.
        return new PreflightReport(templateName, students, run.findings);
    }

    // Template

    // Reads the template folder into a map of question name -> expected files;
    // reports fatal problems.
    private static Map<String, Question> readTemplate(Path template, String templateName,
            List<PreflightFinding> fatal) {
        // The same advice is attached to every template problem.
        String fix = "Set Starting Folder to the starter template (e.g. RenameToYourStudentID)";
        // TreeMap keeps the questions sorted by name.
        Map<String, Question> questions = new TreeMap<>();
        // Question folders that hold no .java directly but whose sub-folders do (a sign
        // the folder is too high).
        List<String> nestedTemplates = new ArrayList<>();
        // Total number of required .java files found across all questions.
        int javaCount = 0;

        // The template must exist, be a folder and be readable.
        if (!isReadableDirectory(template)) {
            // Record one FATAL finding and return the empty map, since there is nothing to
            // read.
            fatal.add(new PreflightFinding("", Category.TEMPLATE_INVALID, Severity.FATAL, template.toString(),
                    "the template is missing, unreadable or not a folder", fix));
            return questions;
        }

        // Listing folders can throw, so the whole template read sits in one try; the
        // catch below turns that into a fatal finding.
        try {
            // Visit each direct child of the template in name order.
            for (Path dir : sortedChildren(template)) {
                // Skip junk and anything that is not a real (non-link) folder.
                if (isJunk(nameOf(dir)) || !isRealDirectory(dir)) {
                    // Not a question folder, so move on to the next child.
                    continue;
                }
                // Start the expectation record for this question folder.
                Question q = new Question(
                        nameOf(dir),
                        new TreeSet<>(),
                        new TreeSet<>(),
                        new TreeSet<>());
                // Does one of this folder's sub-folders directly contain .java files?
                boolean hasNestedJava = false;
                // Look at everything inside the question folder.
                for (Path entry : sortedChildren(dir)) {
                    // The entry's file name, e.g. Q1a.java.
                    String name = nameOf(entry);
                    // Ignore junk such as .DS_Store.
                    if (isJunk(name)) {
                        // Junk is not part of the expected layout.
                        continue;
                    }
                    // A sub-folder: remember if it holds Java, then move on.
                    if (isRealDirectory(entry)) {
                        hasNestedJava |= containsJava(entry);

                        // Remember this folder as part of the expected template structure.
                        q.folders().add(name);

                        continue;
                    }
                    // Links and special files are not part of the expected layout.
                    if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                        // Skip it without recording anything.
                        continue;
                    }
                    // .java files are required files; everything else is an expected resource.
                    if (name.endsWith(".java")) {
                        // Remember it as a file every student must hand in, and count it for the "one
                        // level too high" test below.
                        q.javaFiles().add(name);
                        javaCount++;
                    } else {
                        // Data files such as persons.txt are only warned about, because students may
                        // legitimately omit them.
                        q.resources().add(name);
                    }
                }
                // Record the question and note whether it looks like a whole template one level
                // down.
                questions.put(q.name(), q);
                // Only used by the "one level too high" test after the loop.
                if (hasNestedJava) {
                    nestedTemplates.add(q.name());
                }
            }
        } catch (IOException | SecurityException e) {
            // Could not read part of the template: say so rather than guessing.
            fatal.add(new PreflightFinding("", Category.TEMPLATE_INVALID, Severity.FATAL, template.toString(),
                    "the template could not be read (" + e.getMessage() + ")", fix));
            // Return what was read so far; the caller sees the fatal finding first and
            // ignores it.
            return questions;
        }

        // No question folders at all: nothing to compare with.
        if (questions.isEmpty()) {
            // FATAL: with no questions every submission would look "ready".
            fatal.add(new PreflightFinding("", Category.TEMPLATE_INVALID, Severity.FATAL, template.toString(),
                    "the template has no question folders", fix));
            // No Java files directly in any question, but a folder below holds them: the
            // Starting Folder is one level too high.
        } else if (javaCount == 0 && !nestedTemplates.isEmpty()) {
            // The fix text names the first folder that holds the real question folders.
            fatal.add(new PreflightFinding("", Category.TEMPLATE_INVALID, Severity.FATAL, template.toString(),
                    "looks one level too high: its folder " + String.join(", ", nestedTemplates)
                            + " holds the question folders",
                    "Set Starting Folder to " + template.resolve(nestedTemplates.get(0))));
        }
        // Hand back whatever was learned (the caller checks "fatal" first).
        return questions;
    }

    // Submissions

    // Looks at every entry in the submissions folder and returns the sorted
    // submission names.
    private List<String> scanAll() {
        // TreeSet gives sorted, de-duplicated names.
        TreeSet<String> names = new TreeSet<>();
        // The folder's children, sorted by name for deterministic output.
        List<Path> entries;
        // Listing can fail on permissions, which is reported below instead of crashing.
        try {
            entries = sortedChildren(submissions);
        } catch (IOException | SecurityException e) {
            // Unreadable folder: report it as fatal rather than showing zero students.
            add("", Category.SUBMISSIONS_INVALID, Severity.FATAL, submissions.toString(),
                    "the submissions folder could not be read (" + e.getMessage() + ")",
                    "Check the folder permissions");
            // No students can be listed, so return an empty list.
            return List.of();
        }

        // Lower-case names of the real folders, to recognise a .zip that was already
        // extracted.
        Set<String> folderNames = new HashSet<>();
        for (Path entry : entries) {
            // Only count real, non-junk folders (links are not followed).
            if (!isJunk(nameOf(entry)) && Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                // Lower case, so Frank.zip matches a folder called frank.
                folderNames.add(nameOf(entry).toLowerCase(Locale.ROOT));
            }
        }

        // Handle each entry according to what it is.
        for (Path entry : entries) {
            // Entry's file name.
            String name = nameOf(entry);
            // Branches in order: junk, link, folder, already-extracted zip, unextracted
            // zip, any other file.
            if (isJunk(name)) {
                // Clutter is counted as a note, never as a student; AutoGradeMenu.runGrader
                // later records it quietly through ErrorReporter.ignored.
                add("", Category.IGNORED_JUNK, Severity.NOTE, name, "ignored", "");
            } else if (Files.isSymbolicLink(entry)) {
                // A link is still a student we must not drop, but we refuse to open it.
                // Adding the name keeps it in the student list so the report shows it as
                // needing attention.
                names.add(name);
                add(name, Category.SYMLINK_NOT_FOLLOWED, Severity.ERROR, name,
                        "a symbolic link, so it was not opened",
                        "Replace the link with the real submission folder");
            } else if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                // A normal student folder: scan its layout.
                names.add(name);
                // Compare this folder with the template and add any findings.
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
        // A folder named exactly like the template is probably the blank starter, not a
        // student.
        if (student.equals(templateName)) {
            add(student, Category.TEMPLATE_COPY, Severity.WARNING, student + "/",
                    "has the template's name, so it is treated as the blank starter template and not graded",
                    "If it is a student's work, rename it to their username so it is graded");
        }
        // Scan the layout, allowing one level of wrapper folder.
        scanLayout(student, dir, true);
    }

    /**
     * Scans one student folder; {@code allowWrapper} lets it look one level down
     * for the real work.
     */
    private void scanLayout(String student, Path dir, boolean allowWrapper) {
        // Children of the student folder, sorted.
        List<Path> entries;
        try {
            entries = sortedChildren(dir);
        } catch (IOException | SecurityException e) {
            // Unreadable folder: flag it so the student is not silently "ready".
            add(student, Category.UNREADABLE, Severity.ERROR, rel(dir), "could not be read (" + e.getMessage() + ")",
                    "Check the folder permissions");
            // Nothing more can be checked in a folder we cannot list.
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
        if (items.size() == 1 && isZip(nameOf(items.get(0)))
                && Files.isRegularFile(items.get(0), LinkOption.NOFOLLOW_LINKS)) {
            // The archive's file name for the fix text.
            String zip = nameOf(items.get(0));
            add(student, Category.UNEXTRACTED_ARCHIVE, Severity.ERROR, rel(items.get(0)),
                    "the only content is an archive", "Extract " + zip + " into this folder before grading");
            // There is no layout to compare, so stop here.
            return;
        }

        // Split the content into sub-folders (by name) and plain files.
        // Sub-folders keyed by name, sorted, and the loose files in the order listed.
        Map<String, Path> dirs = new TreeMap<>();
        List<Path> files = new ArrayList<>();
        for (Path item : items) {
            // Real directories only; symlinked directories are not followed and fall into
            // the else branch.
            if (Files.isDirectory(item, LinkOption.NOFOLLOW_LINKS)) {
                // Keyed by name so question folders can be looked up directly.
                dirs.put(nameOf(item), item);
            } else {
                // Loose files at this level are inspected later for stray .java files.
                files.add(item);
            }
        }

        // Pair each template question with the sub-folder of that name (exact or
        // case-only difference).
        Map<String, Path> matched = matchNames(questions.keySet(), dirs);
        // No question folder found directly here: maybe the work is inside one extra
        // folder.
        if (matched.isEmpty() && allowWrapper) {
            // The single folder below that holds the questions, if there is exactly one.
            Path wrapper = findWrapper(dirs);
            // null means none or several candidates, in which case the normal comparison
            // below reports the missing questions.
            if (wrapper != null) {
                // Report the wrapper once instead of one MISSING_QUESTION per question.
                add(student, Category.WRAPPED_FOLDER, Severity.ERROR, rel(wrapper) + "/",
                        "the work is inside an extra folder",
                        "Move the contents of " + rel(wrapper) + "/ up into " + rel(dir) + "/");
                // Siblings of the wrapper are extras.
                for (Path other : dirs.values()) {
                    // Every sub-folder except the wrapper itself.
                    if (!other.equals(wrapper)) {
                        addExtra(student, other, true);
                    }
                }
                // Loose files next to the wrapper are extras too.
                for (Path file : files) {
                    // false = a file rather than a folder, so no trailing slash in the report.
                    addExtra(student, file, false);
                }
                // Continue checking recursively in case the work is wrapped inside more than
                // one extra folder.
                scanLayout(student, wrapper, true);

                // The wrapper's contents were just checked, so skip the comparison below, which
                // would flag every question as missing.
                return;
            }
        }

        // Compare each expected question with what the student has.
        for (Question q : questions.values()) {
            // The student's folder for this question, if any.
            Path questionDir = matched.get(q.name());
            // null means neither the exact name nor a case-only match exists.
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
            // Folders already paired with a template question are not extras.
            if (!matched.containsValue(d)) {
                // true = a folder, so the report shows a trailing slash.
                addExtra(student, d, true);
            }
        }
        // So are loose files next to the question folders.
        for (Path file : files) {
            // Loose files at student level are not in the template.
            addExtra(student, file, false);
        }
    }

    // Checks the files inside one question folder against the template's
    // expectations.
    private void scanQuestion(String student, Question q, Path questionDir) {
        // Children of the question folder, sorted.
        List<Path> entries;
        try {
            entries = sortedChildren(questionDir);
        } catch (IOException | SecurityException e) {
            // Unreadable question folder: flag it.
            add(student, Category.UNREADABLE, Severity.ERROR, rel(questionDir) + "/",
                    "could not be read (" + e.getMessage() + ")", "Check the folder permissions");
            // Without a listing no file checks are possible.
            return;
        }

        // Plain files by name, and sub-folders, after removing junk and links.
        // Files keyed by name for exact lookup, and sub-folders in listing order.
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

        // Pair each required .java name with the student's file (exact or case-only
        // difference).
        Map<String, Path> matchedJava = matchNames(q.javaFiles(), regular);
        // Check every required file in name order.
        for (String required : q.javaFiles()) {
            // The student's file for this required name, if any.
            Path file = matchedJava.get(required);
            // Where the file should have been, for messages.
            String where = rel(questionDir) + "/" + required;
            if (file == null) {
                // Not there at all.
                add(student, Category.MISSING_FILE, Severity.ERROR, where, "the file is missing",
                        "Add " + required + " to " + q.name() + "/");
                // A missing file cannot also be misnamed or empty.
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
            // Exact name only: resources are not matched by letter case.
            if (!regular.containsKey(resource)) {
                add(student, Category.MISSING_RESOURCE, Severity.ERROR, rel(questionDir) + "/" + resource,
                        "a resource file from the template is missing", "Add " + resource + " to " + q.name() + "/");
            }
        }

        // Any .java file not matched to a required name is an extra.
        for (Map.Entry<String, Path> e : regular.entrySet()) {
            // Only .java files count here; other extra files are not reported.
            if (e.getKey().endsWith(".java") && !matchedJava.containsValue(e.getValue())) {
                // false = a file, not a folder.
                addExtra(student, e.getValue(), false);
            }
        }
        // Match the student's subfolders against those in the template.
        Map<String, Path> actualFolders = new TreeMap<>();

        for (Path sub : subfolders) {
            actualFolders.put(nameOf(sub), sub);
        }

        Map<String, Path> matchedFolders = matchNames(q.folders(), actualFolders);

        // Report folders that exist in both places but have different casing.
        for (String expected : q.folders()) {
            Path actual = matchedFolders.get(expected);

            if (actual != null && !nameOf(actual).equals(expected)) {
                add(student, Category.MISNAMED, Severity.ERROR, rel(actual) + "/",
                        "expected the name " + expected,
                        "Rename " + nameOf(actual) + " to " + expected);
            }
        }

        // Only folders that are not in the template are extras.
        for (Path sub : subfolders) {
            if (!matchedFolders.containsValue(sub)) {
                addExtra(student, sub, true);
            }
        }
    }

    // Helpers

    /**
     * Pairs each expected name with the actual entry of that exact name, or else
     * the single entry
     * that differs only by letter case.
     */
    private static Map<String, Path> matchNames(Iterable<String> expected, Map<String, Path> actual) {
        // Expected name -> the actual path that satisfies it.
        Map<String, Path> matched = new TreeMap<>();
        // Handle each expected name on its own.
        for (String name : expected) {
            // An exact spelling always wins.
            Path exact = actual.get(name);
            if (exact != null) {
                // Pair the name with its exact match and skip the case-insensitive search.
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
                // Pair the expected name with the one differently-cased entry.
                matched.put(name, caseOnly.get(0));
            }
        }
        // Expected name -> student's entry, containing only the names that were found.
        return matched;
    }

    /**
     * Finds the single folder containing the student's work, even if
     * the work is wrapped inside multiple levels of extra folders.
     */
    private Path findWrapper(Map<String, Path> dirs) {

        // Folders that eventually contain at least one expected question folder.
        List<Path> candidates = new ArrayList<>();

        for (Path candidate : dirs.values()) {

            // Check recursively instead of checking only the immediate children.
            if (containsQuestionFolder(candidate)) {
                candidates.add(candidate);
            }
        }

        // Only identify a wrapper when there is exactly one candidate.
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    /**
     * Checks whether a folder contains an expected question folder,
     * either directly or somewhere deeper in its hierarchy.
     */
    private boolean containsQuestionFolder(Path folder) {

        try {

            // Collect the real subfolders.
            Map<String, Path> inside = new TreeMap<>();

            for (Path child : sortedChildren(folder)) {

                // Ignore junk and symbolic links.
                if (isJunk(nameOf(child)) || !isRealDirectory(child)) {
                    continue;
                }

                inside.put(nameOf(child), child);
            }

            // If an expected question folder exists directly inside,
            // we have found the student's actual work.
            if (!matchNames(questions.keySet(), inside).isEmpty()) {
                return true;
            }

            // Otherwise, continue searching deeper.
            for (Path child : inside.values()) {

                if (containsQuestionFolder(child)) {
                    return true;
                }
            }

        } catch (IOException | SecurityException e) {
            // An unreadable folder cannot be confirmed as a wrapper.
            return false;
        }

        return false;
    }

    // Records "this is not in the template" for a file or folder.
    private void addExtra(String student, Path path, boolean folder) {
        // WARNING (not ERROR): an extra does not stop the student being "ready";
        // folders get a trailing slash in the path.
        add(student, Category.EXTRA_FILE, Severity.WARNING, rel(path) + (folder ? "/" : ""),
                "not in the template", "Graders may ignore it; check it is not part of the answer");
    }

    // Appends one finding to the list.
    private void add(String student, Category category, Severity severity, String path, String message, String fix) {
        // Build the immutable finding and keep it in discovery order for the report.
        findings.add(new PreflightFinding(student, category, severity, path, message, fix));
    }

    // Path relative to the submissions folder, always with forward slashes.
    private String rel(Path path) {
        // Relative to the submissions folder; backslashes become slashes so Windows
        // output matches Unix.
        return submissions.relativize(path).toString().replace('\\', '/');
    }

    // True when the file is empty or only whitespace (reads at most
    // BLANK_CHECK_BYTES, read-only).
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
                // Look at each byte of the start of the file.
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
            // Any regular (non-link) .java file directly inside the folder is enough.
            return sortedChildren(dir).stream()
                    .anyMatch(p -> nameOf(p).endsWith(".java") && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS));
        } catch (IOException | SecurityException e) {
            // Unreadable folders are treated as having no Java.
            return false;
        }
    }

    // Lists a folder's children sorted by name (so output never depends on the file
    // system's order).
    private static List<Path> sortedChildren(Path dir) throws IOException {
        // Files.list holds a directory handle, so close it with try-with-resources.
        try (Stream<Path> stream = Files.list(dir)) {
            // Sort by file name so the report order is the same on every machine.
            return stream.sorted(Comparator.comparing(p -> nameOf(p))).toList();
        }
    }

    // A folder we are allowed to look inside.
    private static boolean isReadableDirectory(Path dir) {
        // Must be a directory and readable, otherwise listing it would fail.
        return Files.isDirectory(dir) && Files.isReadable(dir);
    }

    // A genuine folder, not a symbolic link to one.
    private static boolean isRealDirectory(Path dir) {
        // NOFOLLOW_LINKS makes a symbolic link to a folder answer false.
        return Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS);
    }

    // Names we ignore: hidden files and macOS resource folders.
    // .class files are deliberately NOT ignored because they may be required
    // by the grading algorithm and are part of the Starting Folder template.
    private static boolean isJunk(String name) {
        return name.startsWith(".") || name.equals("__MACOSX");
    }

    // True for .zip names in any letter case.
    private static boolean isZip(String name) {
        // Lower-case first so .ZIP and .Zip count too.
        return name.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    // "frank.zip" -> "frank".
    private static String stem(String zipName) {
        // Drop the last four characters, ".zip".
        return zipName.substring(0, zipName.length() - ".zip".length());
    }

    // Last part of a path; falls back to the whole path for roots like "/".
    private static String nameOf(Path path) {
        // getFileName() is null for a root path such as "/".
        Path name = path.getFileName();
        // Use the whole path text in that case.
        return name == null ? path.toString() : name.toString();
    }
}
