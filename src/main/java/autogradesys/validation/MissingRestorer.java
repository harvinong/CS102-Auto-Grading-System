
package autogradesys.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import autogradesys.preflight.PreflightFinding;
import autogradesys.preflight.PreflightFinding.Category;
import autogradesys.preflight.PreflightReport;
import autogradesys.validation.ValidationEvent.Type;

/**
 * Puts back question folders and files that are missing from the working copy.
 *
 * Restoration priority:
 * 1. Try the student's original submission first.
 * 2. If the item is absent from the original submission, try the Starting Folder.
 * 3. If neither source contains the item, report that it could not be restored.
 *
 * The original submissions and Starting Folder template are never modified.
 */
public class MissingRestorer {

    private final SubmissionCache cache;
    private final Path templateDir;

    /**
     * Creates a missing restorer instance.
     */
    public MissingRestorer(SubmissionCache cache, Path templateDir) {
        this.cache = cache;
        this.templateDir = templateDir == null
                ? null
                : templateDir.toAbsolutePath().normalize();
    }

    /**
     * Backwards-compatible constructor.
     */
    public MissingRestorer(SubmissionCache cache) {
        this(cache, null);
    }

    /**
     * Restore.
     */
    public List<ValidationEvent> restore(
            Path workingDir,
            PreflightReport preflight) {

        List<ValidationEvent> events = new ArrayList<>();

        for (PreflightFinding finding : preflight.findings()) {

            boolean question =
                    finding.category() == Category.MISSING_QUESTION;

            boolean javaFile =
                    finding.category() == Category.MISSING_FILE;

            boolean resource =
                    finding.category() == Category.MISSING_RESOURCE;

            // Do not restore the blank template as if it were a student.
            if (finding.student().equals(preflight.templateName())) {
                continue;
            }

            if (question || javaFile || resource) {
                int parts = question ? 1 : 2;
                events.add(restoreOne(workingDir, finding, parts));
            }
        }

        return events;
    }

    /**
     * Restores one missing question folder or file.
     */
    private ValidationEvent restoreOne(
            Path workingDir,
            PreflightFinding finding,
            int parts) {

        Path workingRoot = workingDir.toAbsolutePath().normalize();
        Path target = workingRoot.resolve(finding.path()).normalize();

        // Never allow a finding to restore outside the working copy.
        if (!target.startsWith(workingRoot)) {
            return missing(finding, "the restoration path is invalid");
        }

        int count = target.getNameCount();

        if (count < parts) {
            return missing(finding,
                    "the restoration path could not be determined");
        }

        // Examples:
        // Q1/Q1a.java -> Q1/Q1a.java
        // Q2/students.txt -> Q2/students.txt
        // Q1 -> Q1
        Path relativeItem = target.subpath(count - parts, count);

        String item = relativeItem.toString().replace('\\', '/');

        // Determine the student's project directory.
        Path projectDir = target;

        for (int i = 0; i < parts; i++) {
            projectDir = projectDir.getParent();

            if (projectDir == null) {
                return missing(finding,
                        "the destination folder could not be determined");
            }
        }

        // ---------------------------------------------------------
        // STEP 1: Try restoring from the student's original submission.
        // ---------------------------------------------------------

        Optional<Path> original = cache.sourceOf(finding.student());

        String originalProblem =
                "the original submission could not be found";

        if (original.isPresent()) {
            try {

                int restored = SubmissionFiles.isZipFile(original.get())
                        ? ArchiveExtractor.restore(
                                original.get(), projectDir, item)
                        : restoreFromFolder(
                                original.get(), projectDir, item);

                if (restored > 0) {
                    return restoredEvent(
                            finding,
                            item,
                            restored,
                            "the original submission");
                }

                originalProblem =
                        "the item was not found in the original submission";

            } catch (IOException e) {

                originalProblem =
                        "the original submission could not be read ("
                                + e.getMessage() + ")";
            }
        }

        // ---------------------------------------------------------
        // STEP 2: Try restoring from the Starting Folder template.
        // ---------------------------------------------------------

        if (templateDir != null) {
            try {

                Path templateSource =
                        templateDir.resolve(relativeItem).normalize();

                // Ensure the source remains inside the configured template.
                if (!templateSource.startsWith(templateDir)) {
                    return missing(finding,
                            "the template restoration path is invalid");
                }

                // Only copy genuine files or directories, never symbolic links.
                boolean validSource =
                        Files.isRegularFile(
                                templateSource, LinkOption.NOFOLLOW_LINKS)
                        || Files.isDirectory(
                                templateSource, LinkOption.NOFOLLOW_LINKS);

                if (validSource) {

                    // IMPORTANT:
                    // Preserve the entire relative path, including Q1 or Q2.
                    Path destination =
                            projectDir.resolve(relativeItem).normalize();

                    if (!destination.startsWith(projectDir)) {
                        return missing(finding,
                                "the template destination path is invalid");
                    }

                    // Ensure the question folder exists before copying a file.
                    Path parent = destination.getParent();

                    if (parent != null) {
                        Files.createDirectories(parent);
                    }

                    int restored = SubmissionFiles.copyMissing(
                            templateSource,
                            destination
                    );

                    if (restored > 0) {
                        return restoredEvent(
                                finding,
                                item,
                                restored,
                                "the Starting Folder template");
                    }
                }

            } catch (IOException e) {

                return missing(
                        finding,
                        originalProblem
                                + "; the Starting Folder fallback also failed ("
                                + e.getMessage() + ")");
            }
        }

        // ---------------------------------------------------------
        // STEP 3: Neither source contained the item.
        // ---------------------------------------------------------

        String message =
                item + " could not be restored: " + originalProblem;

        if (templateDir == null) {
            message += "; no Starting Folder path was provided";
        } else {
            message +=
                    "; the item was also not found in the Starting Folder template";
        }

        return missing(finding, message);
    }

    /**
     * Creates a successful restoration event.
     */
    private static ValidationEvent restoredEvent(
            PreflightFinding finding,
            String item,
            int restored,
            String source) {

        return new ValidationEvent(
                finding.student(),
                Type.ITEM_RESTORED,
                finding.path(),
                "was missing, restored " + item + " from " + source
                        + " (" + restored
                        + (restored == 1 ? " file)" : " files)")
        );
    }

    /**
     * Creates a failed restoration event.
     */
    private static ValidationEvent missing(
            PreflightFinding finding,
            String message) {

        return new ValidationEvent(
                finding.student(),
                Type.ITEM_MISSING,
                finding.path(),
                message
        );
    }

    /**
     * Copies the missing item from the original submission,
     * even if the original submission contains multiple wrappers.
     */
    private static int restoreFromFolder(
            Path originalFolder,
            Path projectDir,
            String item) throws IOException {

        Path source = originalFolder.resolve(item);

        if (Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
            return SubmissionFiles.copyMissing(
                    source,
                    projectDir.resolve(Path.of(item))
            );
        }

        source = findOriginalItem(originalFolder, item);

        if (source == null) {
            return 0;
        }

        return SubmissionFiles.copyMissing(
                source,
                projectDir.resolve(Path.of(item))
        );
    }

    /**
     * Searches recursively for the requested item inside the original submission.
     */
    private static Path findOriginalItem(
            Path folder,
            String item) throws IOException {

        Path candidate = folder.resolve(item);

        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            return candidate;
        }

        try (Stream<Path> children = Files.list(folder)) {

            for (Path child : children.sorted().toList()) {

                if (SubmissionFiles.isJunk(child.getFileName())
                        || !Files.isDirectory(
                                child, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }

                Path found = findOriginalItem(child, item);

                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }
}
