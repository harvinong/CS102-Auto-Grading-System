package autogradesys.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import autogradesys.preflight.PreflightFinding;
import autogradesys.preflight.PreflightFinding.Category;
import autogradesys.preflight.PreflightReport;
import autogradesys.validation.ValidationEvent.Type;

/**
 * Fixes the folder hierarchy problems that the preflight reports, so detection
 * is not repeated here.
 * Currently: the work sits inside one extra folder (e.g. RenameToYourStudentID/
 * or a student number).
 */

public final class HierarchyFixer {

    private HierarchyFixer() {
    }

    /**
     * Moves the contents of every wrapper folder up into its student folder.
     *
     * @param submissions The submissions folder the preflight scanned.
     * @param preflight   The preflight report whose WRAPPED_FOLDER findings are
     *                    fixed.
     * @return One event per wrapper folder.
     */

    /**
     * Moves the contents of every wrapper folder up into its parent.
     * Nested wrappers are processed from the innermost folder outward.
     *
     * @param submissions The submissions folder the preflight scanned.
     * @param preflight   The preflight report whose WRAPPED_FOLDER findings are
     *                    fixed.
     * @return One event per wrapper folder.
     */
    public static List<ValidationEvent> flattenWrappers(Path submissions, PreflightReport preflight) {
        List<ValidationEvent> events = new ArrayList<>();

        // Collect all wrapper findings belonging to actual student submissions.
        List<PreflightFinding> wrappers = preflight.findings().stream()
                .filter(finding -> finding.category() == Category.WRAPPED_FOLDER
                        && !finding.student().equals(preflight.templateName()))
                .toList();

        // Process in reverse order so nested wrappers are flattened first.
        for (int i = wrappers.size() - 1; i >= 0; i--) {

            PreflightFinding finding = wrappers.get(i);

            events.add(flatten(
                    submissions.resolve(finding.path()),
                    finding));
        }

        return events;
    }

    // Moves everything inside the wrapper up one level, then removes the empty
    // wrapper.
    private static ValidationEvent flatten(Path wrapper, PreflightFinding finding) {
        Path studentDir = wrapper.getParent();
        List<String> kept = new ArrayList<>();
        try {
            List<Path> children;
            try (Stream<Path> stream = Files.list(wrapper)) {
                children = stream.toList();
            }
            for (Path child : children) {
                Path target = studentDir.resolve(child.getFileName());
                // Never overwrite something the student already has at the top level.
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    kept.add(child.getFileName().toString());
                } else {
                    Files.move(child, target);
                }
            }
            if (kept.isEmpty()) {
                Files.delete(wrapper);
            }
        } catch (IOException e) {
            return new ValidationEvent(finding.student(), Type.FIX_FAILED, finding.path(),
                    "could not move the contents up (" + e.getMessage() + ")");
        }
        if (!kept.isEmpty()) {
            return new ValidationEvent(finding.student(), Type.FIX_FAILED, finding.path(),
                    "left in place because " + String.join(", ", kept) + " already exists in " + finding.student()
                            + "/");
        }
        return new ValidationEvent(finding.student(), Type.FOLDER_FLATTENED, finding.path(),
                "moved its contents up into " + finding.student() + "/");
    }
}
