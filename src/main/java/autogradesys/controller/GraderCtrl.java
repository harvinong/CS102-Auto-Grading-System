package autogradesys.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import autogradesys.config.Config;
import autogradesys.io.ScoresheetReader;
import autogradesys.preflight.HeaderPreflightCheck;
import autogradesys.preflight.PreflightFinding;
import autogradesys.preflight.PreflightFinding.Category;
import autogradesys.preflight.PreflightFinding.Severity;
import autogradesys.preflight.PreflightReport;
import autogradesys.preflight.SubmissionPreflight;
import autogradesys.validation.CacheReport;
import autogradesys.validation.Student;
import autogradesys.validation.SubmissionCache;
import autogradesys.validation.SubmissionValidation;
import autogradesys.validation.TemplateResourceSynchronizer;
import autogradesys.validation.ValidationEvent;
import autogradesys.validation.UsernameTypoCheck;
import autogradesys.validation.ValidationReport;

public class GraderCtrl {

    private final SubmissionCache cache = new SubmissionCache(SubmissionCache.DEFAULT_ROOT);
    public final Config config;

    /**
     * Creates a controller for one grading session.
     *
     * @param config the folders and limits chosen by the user; the starting folder and scoresheet are read from it when checking and validating
     */
    public GraderCtrl(Config config) {
        this.config = config;
    }

    /**
     * Gets a cached working copy of the submissions, so repairs never touch the originals.
     *
     * @param submissions the folder holding the students' original submissions
     * @return a report naming the working copy folder and whether it was newly built or reused
     */
    public CacheReport prepareWorkingCopy(File submissions) {
        return cache.prepare(submissions.toPath());
    }

    /**
     * Synchronises non-Java resources in the working copy with the Starting Folder template, then runs the
     * preflight check on the updated copy, adding username-typo and Java-header findings from the scoresheet.
     * Original submissions and the Starting Folder are never modified.
     *
     * @param workingDir the cached working copy that contains one folder per student
     * @return the preflight report with the synchronisation findings added; if the scoresheet cannot be read,
     *         the report is returned without the username and header findings
     */
    public PreflightReport checkSubmissions(Path workingDir) {

        Path templateDir = config.startingFolder().toPath();

        // First check the existing layout. This also verifies that the
        // template and submissions directory are usable before synchronising.
        PreflightReport initial = SubmissionPreflight.check(
                templateDir,
                workingDir);

        List<PreflightFinding> syncFindings = new ArrayList<>();

        // Do not attempt synchronisation if the template or submissions
        // directory is invalid.
        if (!initial.fatal()) {

            TemplateResourceSynchronizer synchronizer =
                    new TemplateResourceSynchronizer(templateDir);

            for (String student : initial.students()) {

                // Never synchronise the template copy itself.
                if (initial.templateCopy(student)) {
                    continue;
                }

                Path studentDir = workingDir.resolve(student);

                // Archives, symbolic links and other non-directory entries
                // cannot be synchronised as working submission folders.
                if (!Files.isDirectory(studentDir, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }

                // The synchroniser expects question folders directly inside
                // the student's folder. Do not create duplicate folders when
                // the actual work is inside an extra wrapper.
                PreflightFinding wrapper = initial.findings().stream()
                        .filter(f -> f.student().equals(student)
                                && f.category() == Category.WRAPPED_FOLDER)
                        .findFirst()
                        .orElse(null);

                if (wrapper != null) {
                    syncFindings.add(new PreflightFinding(
                            student,
                            Category.RESOURCE_SYNC_SKIPPED,
                            Severity.NOTE,
                            wrapper.path(),
                            "resource synchronisation was skipped because the work is inside an extra wrapper folder",
                            "Flatten the wrapper folder and run Submission Check again"));
                    continue;
                }

                // Synchronise this student's non-Java resources.
                List<ValidationEvent> events =
                        synchronizer.synchronize(student, studentDir);

                // Convert synchronisation events into preflight findings
                // so they appear in the normal Submission Check report.
                for (ValidationEvent event : events) {

                    boolean failed =
                            event.type() == ValidationEvent.Type.RESOURCE_SYNC_FAILED;

                    syncFindings.add(new PreflightFinding(
                            student,
                            failed
                                    ? Category.RESOURCE_SYNC_FAILED
                                    : Category.RESOURCES_SYNCHRONISED,
                            failed ? Severity.ERROR : Severity.NOTE,
                            event.path(),
                            event.message(),
                            failed
                                    ? "Check the working copy permissions and the corresponding template resource"
                                    : ""));
                }
            }
        }

        // Run preflight again against the synchronised working copy.
        // Missing resources that were successfully restored should no
        // longer appear as missing.
        PreflightReport report = SubmissionPreflight.check(
                templateDir,
                workingDir);

        // Add the synchronisation results to the normal preflight findings.
        List<PreflightFinding> findings =
                new ArrayList<>(report.findings());

        findings.addAll(syncFindings);

        report = new PreflightReport(
                report.templateName(),
                report.students(),
                findings);

        // Add username typo and Java header findings as before.
        try {
            List<Student> students = ScoresheetReader.read(config.scoresheetFile());

            report = UsernameTypoCheck.addFindings(report, students);

            return HeaderPreflightCheck.addFindings(
                    report,
                    workingDir,
                    students);

        } catch (IOException e) {
            return report;
        }
    }

    /**
     * Validates the working copy against the scoresheet and repairs what it can, asking for confirmation where needed.
     *
     * @param workingDir the cached working copy that contains one folder per student
     * @param preflight the report from {@link #checkSubmissions}; it decides which students need repair
     * @param confirm answers a yes/no question about a proposed repair; return true to go ahead
     * @return events describing repairs made and issues that remain
     * @throws IOException if the scoresheet cannot be read or a file operation during validation fails
     */
    public ValidationReport validateSubmissions(Path workingDir, PreflightReport preflight,
            Predicate<String> confirm) throws IOException {

        List<Student> students = ScoresheetReader.read(config.scoresheetFile());

        return new SubmissionValidation(
                students,
                confirm,
                cache,
                config.startingFolder().toPath()).run(workingDir, preflight);
    }

    /**
     * Same as the {@link Path} version, for callers that hold a {@link File}.
     *
     * @param submissions the working copy folder with one folder per student
     * @param preflight the report from {@link #checkSubmissions}
     * @param confirm answers a yes/no question about a proposed repair; return true to go ahead
     * @return events describing repairs made and issues that remain
     * @throws IOException if the scoresheet cannot be read or a file operation during validation fails
     */
    public ValidationReport validateSubmissions(File submissions, PreflightReport preflight,
            Predicate<String> confirm) throws IOException {

        return validateSubmissions(submissions.toPath(), preflight, confirm);
    }
}
