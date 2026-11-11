package autogradesys.validation;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

import autogradesys.preflight.PreflightReport;
import autogradesys.validation.ValidationEvent.Type;

public class SubmissionValidation {

    private final List<Student> students;
    private final UsernameResolver resolver;
    private final MissingRestorer restorer;
    private final SubmissionCache cache;

    /**
     * Creates a submission validation instance.
     */
    public SubmissionValidation(
            List<Student> students,
            Predicate<String> confirm,
            SubmissionCache cache) {
        this(students, confirm, cache, null);
    }

    /**
     * Creates a submission validation instance.
     */
    public SubmissionValidation(
            List<Student> students,
            Predicate<String> confirm,
            SubmissionCache cache,
            Path templateDir) {

        this.students = students;
        this.resolver = new UsernameResolver(students, confirm);
        this.restorer = new MissingRestorer(cache, templateDir);
        this.cache = cache;
    }

    /**
     * Run.
     */
    public ValidationReport run(Path submissions, PreflightReport preflight) {

        List<ValidationEvent> events = new ArrayList<>();
        Map<String, Path> identified = new TreeMap<>();

        // Which folder already has each registered username.
        Map<String, String> owners =
                UsernameResolver.owners(preflight.students(), students);

        Set<String> replacedFolders = new HashSet<>();

        // Tracks original folder names and their final renamed versions.
        Map<String, String> finalFolderNames = new TreeMap<>();

        // Restore missing files before flattening wrapper folders.
        events.addAll(restorer.restore(submissions, preflight));
        events.addAll(HierarchyFixer.flattenWrappers(submissions, preflight));

        for (String folder : preflight.students()) {

            Path dir = submissions.resolve(folder);

            if (folder.equals(preflight.templateName())
                    || replacedFolders.contains(folder)
                    || !Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }

            resolver.resolve(folder, dir, events, owners).ifPresent(identity -> {

                String username = identity.student().username();

                if (identity.replaced() != null) {
                    replacedFolders.add(identity.replaced());
                    identified.remove(username);

                    events.removeIf(event ->
                            event.student().equals(identity.replaced()));
                }

                String workingName = SubmissionFiles.nameOf(identity.dir());

                if (!workingName.equals(folder)) {

                    // Preserve the link to the original submission after a rename.
                    cache.renamed(folder, workingName);

                    // Remember the final name for report grouping.
                    finalFolderNames.put(folder, workingName);
                }

                if (identified.containsKey(username)) {
                    events.add(new ValidationEvent(
                            workingName,
                            Type.USERNAME_DUPLICATE,
                            workingName + "/",
                            "another folder is already matched to " + username));
                    return;
                }

                identified.put(username, identity.dir());

                // Use the final folder name for all subsequent header events.
                events.addAll(HeaderFixer.fixAll(
                        workingName,
                        identity.dir(),
                        identity.student(),
                        students));
            });
        }

        // Update the student field of earlier events so all events for a
        // renamed folder appear under its final name in ValidationReport.
        List<ValidationEvent> groupedEvents = events.stream()
                .map(event -> {
                    String finalName = finalFolderNames.getOrDefault(
                            event.student(), event.student());

                    return new ValidationEvent(
                            finalName,
                            event.type(),
                            event.path(),
                            event.message());
                })
                .toList();

        return new ValidationReport(groupedEvents, identified);
    }
}
