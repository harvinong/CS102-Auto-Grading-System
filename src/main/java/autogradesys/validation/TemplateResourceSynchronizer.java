package autogradesys.validation;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import autogradesys.validation.ValidationEvent.Type;

/**
 * Makes the non-Java files in a student's working submission match
 * the Starting Folder template.
 *
 * - Template non-Java files replace the student's versions.
 * - Missing template non-Java files are restored.
 * - Non-Java files absent from the template are removed.
 * - Java files are never modified or deleted.
 *
 * Only the working submission is modified.
 */
public class TemplateResourceSynchronizer {

    private final Path templateDir;

    /**
     * Creates a synchroniser for one starter template.
     *
     * @param templateDir the Starting Folder template (made absolute and normalised); null disables synchronisation, so {@link #synchronize} does nothing
     */
    public TemplateResourceSynchronizer(Path templateDir) {
        this.templateDir = templateDir == null
                ? null
                : templateDir.toAbsolutePath().normalize();
    }

    /**
     * Makes one student's non-Java files match the template: template files replace or restore the
     * student's versions, extra non-Java files and empty folders are removed, and Java files are never changed or deleted.
     * Only the working submission is modified. Successful changes are summarised in one event, while each failure keeps its own event.
     *
     * @param student the student's folder name, used in the events
     * @param studentDir the student's folder in the working copy; it must contain the question folders directly
     * @return a RESOURCES_SYNCHRONISED summary if anything changed, plus one RESOURCE_SYNC_FAILED event per failure; empty if the template is null or nothing needed changing
     */
    public List<ValidationEvent> synchronize(
            String student,
            Path studentDir) {

        List<ValidationEvent> events = new ArrayList<>();

        if (templateDir == null) {
            return events;
        }

        Path workingRoot = studentDir.toAbsolutePath().normalize();

        if (!Files.isDirectory(templateDir, LinkOption.NOFOLLOW_LINKS)
                || !Files.isDirectory(workingRoot, LinkOption.NOFOLLOW_LINKS)) {

            events.add(failed(student, student + "/",
                    "the template or working submission folder is unavailable"));

            return summarise(student, events);
        }

        try {
            // ---------------------------------------------------------
            // STEP 1: Read the expected template structure.
            // ---------------------------------------------------------

            Set<Path> expectedDirectories = new HashSet<>();
            Map<Path, Path> expectedResources = new TreeMap<>();

            Files.walkFileTree(templateDir, new SimpleFileVisitor<Path>() {

                /**
                 * Records each template folder, except junk folders, which are skipped together with everything inside them.
                 *
                 * @param dir the template folder being entered
                 * @param attrs the folder's attributes, unused
                 * @return SKIP_SUBTREE for a junk folder, otherwise CONTINUE
                 */
                @Override
                public FileVisitResult preVisitDirectory(
                        Path dir,
                        BasicFileAttributes attrs) {

                    Path relative = templateDir.relativize(dir);

                    if (SubmissionFiles.isJunk(relative)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    if (!relative.toString().isEmpty()) {
                        expectedDirectories.add(relative);
                    }

                    return FileVisitResult.CONTINUE;
                }

                /**
                 * Records each template file that is a resource: a regular, non-junk file that is not Java source.
                 *
                 * @param file the template file being visited
                 * @param attrs the file's attributes, used to ignore anything that is not a regular file
                 * @return CONTINUE, so the scan of the template goes on whether or not this file counted as a resource
                 */
                @Override
                public FileVisitResult visitFile(
                        Path file,
                        BasicFileAttributes attrs) {

                    Path relative = templateDir.relativize(file);

                    if (attrs.isRegularFile()
                            && !SubmissionFiles.isJunk(relative)
                            && !isJavaFile(relative)) {

                        expectedResources.put(relative, file);
                    }

                    return FileVisitResult.CONTINUE;
                }
            });

            // ---------------------------------------------------------
            // STEP 2: Ensure all template directories exist.
            // ---------------------------------------------------------

            List<Path> directories = new ArrayList<>(expectedDirectories);

            // Create parent folders before their children.
            directories.sort(
                    Comparator.comparingInt(Path::getNameCount)
                            .thenComparing(Path::toString));

            for (Path relative : directories) {

                Path target = workingRoot.resolve(relative).normalize();

                if (!target.startsWith(workingRoot)) {
                    events.add(failed(student, relative.toString(),
                            "the template directory path is invalid"));
                    continue;
                }

                // A non-directory file may be occupying a required folder's path.
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {

                    if (isJavaFile(relative)) {
                        events.add(failed(student, relative.toString(),
                                "a Java file conflicts with a required template directory"));
                        continue;
                    }

                    Files.delete(target);

                    events.add(removed(student, relative,
                            "removed a file that was occupying a template directory"));
                }

                Files.createDirectories(target);
            }

            // ---------------------------------------------------------
            // STEP 3: Replace or restore every template resource.
            // ---------------------------------------------------------

            for (Map.Entry<Path, Path> entry : expectedResources.entrySet()) {

                Path relative = entry.getKey();
                Path source = entry.getValue();
                Path target = workingRoot.resolve(relative).normalize();

                if (!target.startsWith(workingRoot)) {
                    events.add(failed(student, relative.toString(),
                            "the resource destination path is invalid"));
                    continue;
                }

                try {
                    Files.createDirectories(target.getParent());

                    boolean existed = Files.exists(
                            target, LinkOption.NOFOLLOW_LINKS);

                    // A directory may be occupying a path where the template
                    // expects a resource file.
                    if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {

                        if (containsJavaFile(target)) {
                            events.add(failed(student, relative.toString(),
                                    "a folder containing Java files conflicts with a template resource"));
                            continue;
                        }

                        deleteTreeStrict(target);

                        events.add(removed(student, relative,
                                "removed a folder that was occupying a template resource path"));

                        existed = false;

                    } else if (existed) {

                        // Delete first so symbolic links are replaced rather
                        // than followed.
                        Files.delete(target);
                    }

                    Files.copy(
                            source,
                            target,
                            StandardCopyOption.REPLACE_EXISTING);

                    events.add(new ValidationEvent(
                            student,
                            Type.RESOURCE_REPLACED,
                            student + "/" + relative.toString().replace('\\', '/'),
                            existed
                                    ? "replaced with the Starting Folder template version"
                                    : "restored from the Starting Folder template"));

                } catch (IOException | SecurityException e) {

                    events.add(failed(student, relative.toString(),
                            "could not synchronise the resource ("
                                    + e.getMessage() + ")"));
                }
            }

            // ---------------------------------------------------------
            // STEP 4: Remove non-Java files that are not in the template.
            // ---------------------------------------------------------

            Files.walkFileTree(workingRoot, new SimpleFileVisitor<Path>() {

                /**
                 * Deletes a non-Java file from the student's folder unless the template supplies it. Java files are always kept.
                 *
                 * @param file the student's file being visited
                 * @param attrs the file's attributes, unused
                 * @return CONTINUE, so the walk goes on to the next student file whether or not this one was deleted
                 * @throws IOException if the file cannot be deleted, which stops the walk
                 */
                @Override
                public FileVisitResult visitFile(
                        Path file,
                        BasicFileAttributes attrs) throws IOException {

                    Path relative = workingRoot.relativize(file);

                    // Never delete Java files.
                    if (isJavaFile(relative)) {
                        return FileVisitResult.CONTINUE;
                    }

                    // Files copied from the template must remain.
                    if (expectedResources.containsKey(relative)) {
                        return FileVisitResult.CONTINUE;
                    }

                    Files.delete(file);

                    events.add(removed(student, relative,
                            "removed because it is not in the Starting Folder template"));

                    return FileVisitResult.CONTINUE;
                }

                /**
                 * Deletes a student's folder that is empty and not in the template. The submission root and expected template folders always stay.
                 *
                 * @param dir the student's folder whose contents were just visited
                 * @param exc the failure that occurred while visiting its contents, or null if there was none
                 * @return CONTINUE, so the walk goes on to the next folder, which is deleted only if it was empty and unexpected
                 * @throws IOException if {@code exc} is not null (it is rethrown), or the folder cannot be listed or deleted
                 */
                @Override
                public FileVisitResult postVisitDirectory(
                        Path dir,
                        IOException exc) throws IOException {

                    if (exc != null) {
                        throw exc;
                    }

                    Path relative = workingRoot.relativize(dir);

                    // Keep the submission root and all expected template folders.
                    if (relative.toString().isEmpty()
                            || expectedDirectories.contains(relative)) {
                        return FileVisitResult.CONTINUE;
                    }

                    // Remove extra folders only when they are empty.
                    try (var children = Files.list(dir)) {

                        if (children.findAny().isEmpty()) {

                            Files.delete(dir);

                            events.add(removed(student, relative,
                                    "removed an empty folder not in the template"));
                        }
                    }

                    return FileVisitResult.CONTINUE;
                }
            });

        } catch (IOException | SecurityException e) {

            events.add(failed(student, student + "/",
                    "resource synchronisation could not be completed ("
                            + e.getMessage() + ")"));
        }

        return summarise(student, events);
    }

    /**
     * Checks whether a path is a Java source file. The check is case-insensitive so files such as Example.JAVA are also preserved.
     *
     * @param path the file path or relative path to test
     * @return true if the file name ends with {@code .java} in any letter case
     */
    private static boolean isJavaFile(Path path) {

        String name = SubmissionFiles.nameOf(path);

        return name.toLowerCase(java.util.Locale.ROOT).endsWith(".java");
    }

    /**
     * Checks recursively for Java files without following symbolic links.
     *
     * @param folder the folder to search
     * @return true if a regular Java file exists anywhere below the folder
     * @throws IOException if the folder tree cannot be walked
     */
    private static boolean containsJavaFile(Path folder) throws IOException {

        final boolean[] found = { false };

        Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {

            /**
             * Stops the search as soon as a regular Java file is found.
             *
             * @param file the file being visited
             * @param attrs the file's attributes, used to ignore anything that is not a regular file
             * @return TERMINATE when a Java file is found, otherwise CONTINUE
             */
            @Override
            public FileVisitResult visitFile(
                    Path file,
                    BasicFileAttributes attrs) {

                if (attrs.isRegularFile() && isJavaFile(file)) {
                    found[0] = true;
                    return FileVisitResult.TERMINATE;
                }

                return FileVisitResult.CONTINUE;
            }
        });

        return found[0];
    }

    /**
     * Deletes a folder recursively without following symbolic links. Unlike {@code SubmissionFiles.deleteTree}, failures are reported.
     *
     * @param root the folder to delete
     * @throws IOException if any file or folder cannot be deleted
     */
    private static void deleteTreeStrict(Path root) throws IOException {

        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

            /**
             * Deletes one file during the walk.
             *
             * @param file the file being visited
             * @param attrs the file's attributes, unused
             * @return CONTINUE, so every file below the folder is deleted in turn
             * @throws IOException if the file cannot be deleted
             */
            @Override
            public FileVisitResult visitFile(
                    Path file,
                    BasicFileAttributes attrs) throws IOException {

                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            /**
             * Deletes a folder once everything inside it has been deleted.
             *
             * @param dir the folder whose contents were just visited
             * @param exc the failure that occurred while visiting its contents, or null if there was none
             * @return CONTINUE, so the walk climbs on to the parent folder after this one is deleted
             * @throws IOException if {@code exc} is not null (it is rethrown), or the folder cannot be deleted
             */
            @Override
            public FileVisitResult postVisitDirectory(
                    Path dir,
                    IOException exc) throws IOException {

                if (exc != null) {
                    throw exc;
                }

                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Creates the event for something deleted from the student's folder.
     *
     * @param student the student's folder name
     * @param relative the removed item's path relative to the student's folder
     * @param message why it was removed
     * @return a RESOURCE_REMOVED event whose path is {@code student/relative}
     */
    private static ValidationEvent removed(
            String student,
            Path relative,
            String message) {

        return new ValidationEvent(
                student,
                Type.RESOURCE_REMOVED,
                student + "/" + relative.toString().replace('\\', '/'),
                message);
    }

    /**
     * Creates the event for a step that could not be completed.
     *
     * @param student the student's folder name
     * @param path the path the problem concerns, shown as given
     * @param message what went wrong
     * @return a RESOURCE_SYNC_FAILED event
     */
    private static ValidationEvent failed(
            String student,
            String path,
            String message) {

        return new ValidationEvent(
                student,
                Type.RESOURCE_SYNC_FAILED,
                path,
                message);
    }

    /**
     * Combines all successful resource changes into one summary event, while keeping individual failures visible.
     *
     * @param student the student's folder name
     * @param events every event collected while synchronising
     * @return the failures and any other events, preceded by one RESOURCES_SYNCHRONISED event with the replaced, restored and removed counts if at least one change succeeded
     */
    private static List<ValidationEvent> summarise(
            String student,
            List<ValidationEvent> events) {

        int replaced = 0;
        int restored = 0;
        int removed = 0;

        List<ValidationEvent> result = new ArrayList<>();

        for (ValidationEvent event : events) {

            switch (event.type()) {

                case RESOURCE_REPLACED -> {
                    if (event.message().startsWith("replaced")) {
                        replaced++;
                    } else {
                        restored++;
                    }
                }

                case RESOURCE_REMOVED -> removed++;

                case RESOURCE_SYNC_FAILED -> result.add(event);

                default -> result.add(event);
            }
        }

        // Only report successful synchronisation if something actually changed.
        if (replaced + restored + removed > 0) {

            String message = "synchronised non-Java resources with the Starting Folder template ("
                    + replaced + " replaced, "
                    + restored + " restored, "
                    + removed + " removed)";

            result.add(0, new ValidationEvent(
                    student,
                    Type.RESOURCES_SYNCHRONISED,
                    student + "/",
                    message));
        }

        return result;
    }
}
