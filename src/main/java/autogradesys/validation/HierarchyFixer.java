package autogradesys.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import autogradesys.preflight.PreflightFinding;
import autogradesys.preflight.PreflightFinding.Category;
import autogradesys.preflight.PreflightReport;
import autogradesys.validation.ValidationEvent.Type;

public final class HierarchyFixer {

    /**
     * Initializes the utility that removes unnecessary wrapper directories.
     */
    private HierarchyFixer() {
    }

    /**
     * Moves the contents of every wrapper folder up into its student folder.
     * Nested wrappers are processed from the innermost folder outward.
     * @param submissions the value used by this method.
     * @param preflight the value used by this method.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    public static List<ValidationEvent> flattenWrappers(
            Path submissions,
            PreflightReport preflight) {

        List<ValidationEvent> events = new ArrayList<>();

        List<PreflightFinding> wrappers = preflight.findings().stream()
                .filter(finding ->
                        finding.category() == Category.WRAPPED_FOLDER
                                && !finding.student().equals(preflight.templateName()))
                // Explicitly process deepest wrappers first.
                .sorted(Comparator.comparingInt(
                        (PreflightFinding finding) -> pathDepth(finding.path()))
                        .reversed())
                .toList();

        for (PreflightFinding finding : wrappers) {

            Path wrapper = submissions.resolve(finding.path()).normalize();

            // A previous flattening operation may already have removed this
            // wrapper, so do not report a false failure.
            if (!Files.isDirectory(wrapper, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }

            events.add(flatten(wrapper, finding));
        }

        return events;
    }

    /**
     * Returns the number of path components in a wrapper path.
     * @param path the value used by this method.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    private static int pathDepth(String path) {
        return Path.of(path).getNameCount();
    }

    /**
     * Moves the wrapper's children into its parent, then attempts to remove
     * the now-empty wrapper.
     * @param wrapper the value used by this method.
     * @param finding the value used by this method.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    private static ValidationEvent flatten(
            Path wrapper,
            PreflightFinding finding) {

        Path studentDir = wrapper.getParent();

        if (studentDir == null) {
            return new ValidationEvent(
                    finding.student(),
                    Type.FIX_FAILED,
                    finding.path(),
                    "could not determine the parent folder");
        }

        List<String> kept = new ArrayList<>();

        try {

            List<Path> children;

            try (Stream<Path> stream = Files.list(wrapper)) {
                children = stream.toList();
            }

            // Move each child into the parent directory.
            for (Path child : children) {

                Path target = studentDir.resolve(child.getFileName());

                // Never overwrite something already in the student folder.
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    kept.add(child.getFileName().toString());
                    continue;
                }

                Files.move(child, target);
            }

        } catch (IOException e) {

            return new ValidationEvent(
                    finding.student(),
                    Type.FIX_FAILED,
                    finding.path(),
                    "could not move the contents up (" + e.getMessage() + ")");
        }

        // Some files or folders could not be moved because their names
        // already existed in the parent directory.
        if (!kept.isEmpty()) {

            return new ValidationEvent(
                    finding.student(),
                    Type.FIX_FAILED,
                    finding.path(),
                    "left in place because "
                            + String.join(", ", kept)
                            + " already exists in "
                            + finding.student() + "/");
        }

        // All children have been moved. Now attempt to delete the empty wrapper.
        try {
            Files.delete(wrapper);

        } catch (IOException e) {

            // If the directory is empty despite the deletion error, the
            // important operation (moving the contents) was successful.
            try (Stream<Path> remaining = Files.list(wrapper)) {

                if (remaining.findAny().isEmpty()) {
                    return new ValidationEvent(
                            finding.student(),
                            Type.FOLDER_FLATTENED,
                            finding.path(),
                            "moved its contents up into "
                                    + finding.student()
                                    + "/ (empty wrapper could not be removed)");
                }

            } catch (IOException checkError) {
                // We could not verify that the wrapper is empty.
                return new ValidationEvent(
                        finding.student(),
                        Type.FIX_FAILED,
                        finding.path(),
                        "contents were moved, but could not verify whether "
                                + "the wrapper was empty (" + checkError.getMessage() + ")");
            }

            return new ValidationEvent(
                    finding.student(),
                    Type.FIX_FAILED,
                    finding.path(),
                    "contents were moved, but the wrapper could not be removed ("
                            + e.getMessage() + ")");
        }

        return new ValidationEvent(
                finding.student(),
                Type.FOLDER_FLATTENED,
                finding.path(),
                "moved its contents up into " + finding.student() + "/");
    }
}
