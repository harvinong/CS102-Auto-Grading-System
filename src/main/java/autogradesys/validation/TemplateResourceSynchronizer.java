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
     * Creates a template resource synchronizer instance.
     * @param templateDir the value used by this method.
     */
    public TemplateResourceSynchronizer(Path templateDir) {
        this.templateDir = templateDir == null
                ? null
                : templateDir.toAbsolutePath().normalize();
    }

    /**
     * Synchronises one student's working submission with the template.
     * @param student the value used by this method.
     * @param studentDir the value used by this method.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
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

                @Override
                /**
                 * Pre visit directory.
                 * @param dir the value used by this method.
                 * @param attrs the value used by this method.
                 * @return the value produced by this operation, including any empty or absent result defined by its contract.
                 */
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

                @Override
                /**
                 * Visit file.
                 * @param file the value used by this method.
                 * @param attrs the value used by this method.
                 * @return the value produced by this operation, including any empty or absent result defined by its contract.
                 */
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

                @Override
                /**
                 * Visit file.
                 * @param file the value used by this method.
                 * @param attrs the value used by this method.
                 * @return the value produced by this operation, including any empty or absent result defined by its contract.
                 * @throws IOException when the operation cannot complete.
                 */
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

                @Override
                /**
                 * Post visit directory.
                 * @param dir the value used by this method.
                 * @param exc the value used by this method.
                 * @return the value produced by this operation, including any empty or absent result defined by its contract.
                 * @throws IOException when the operation cannot complete.
                 */
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
     * Checks whether a path represents a Java source file.
     * Case-insensitive so files such as Example.JAVA are also preserved.
     * @param path the value used by this method.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    private static boolean isJavaFile(Path path) {

        String name = SubmissionFiles.nameOf(path);

        return name.toLowerCase(java.util.Locale.ROOT).endsWith(".java");
    }

    /**
     * Checks recursively for Java files without following symbolic links.
     * @param folder the value used by this method.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     * @throws IOException when the operation cannot complete.
     */
    private static boolean containsJavaFile(Path folder) throws IOException {

        final boolean[] found = { false };

        Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {

            @Override
            /**
             * Visit file.
             * @param file the value used by this method.
             * @param attrs the value used by this method.
             * @return the value produced by this operation, including any empty or absent result defined by its contract.
             */
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
     * Deletes a folder recursively without following symbolic links.
     * Unlike SubmissionFiles.deleteTree(), this method reports failures.
     * @param root the value used by this method.
     * @throws IOException when the operation cannot complete.
     */
    private static void deleteTreeStrict(Path root) throws IOException {

        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

            @Override
            /**
             * Visit file.
             * @param file the value used by this method.
             * @param attrs the value used by this method.
             * @return the value produced by this operation, including any empty or absent result defined by its contract.
             * @throws IOException when the operation cannot complete.
             */
            public FileVisitResult visitFile(
                    Path file,
                    BasicFileAttributes attrs) throws IOException {

                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            /**
             * Post visit directory.
             * @param dir the value used by this method.
             * @param exc the value used by this method.
             * @return the value produced by this operation, including any empty or absent result defined by its contract.
             * @throws IOException when the operation cannot complete.
             */
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
     * Removed.
     * @param student the value used by this method.
     * @param relative the value used by this method.
     * @param message the value used by this method.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
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
     * Failed.
     * @param student the value used by this method.
     * @param path the value used by this method.
     * @param message the value used by this method.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
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
     * Combines all successful resource changes into one summary event,
     * while keeping individual failures visible.
     * @param student the value used by this method.
     * @param events the value used by this method.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
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
