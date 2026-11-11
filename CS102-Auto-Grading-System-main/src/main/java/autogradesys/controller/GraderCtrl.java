package autogradesys.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import autogradesys.config.Config;
import autogradesys.io.ScoresheetReader;
import autogradesys.preflight.HeaderPreflightCheck;
import autogradesys.preflight.PreflightReport;
import autogradesys.preflight.SubmissionPreflight;
import autogradesys.validation.CacheReport;
import autogradesys.validation.Student;
import autogradesys.validation.SubmissionCache;
import autogradesys.validation.SubmissionValidation;
import autogradesys.validation.UsernameTypoCheck;
import autogradesys.validation.ValidationReport;

public class GraderCtrl {
    private final SubmissionCache cache = new SubmissionCache(SubmissionCache.DEFAULT_ROOT);
    public final Config config;

    public GraderCtrl(Config config) {
        this.config = config;
    }

    public CacheReport prepareWorkingCopy(File submissions) {
        return cache.prepare(submissions.toPath());
    }

    /**
     * Runs the preflight check on the working copy and adds a USERNAME_TYPO finding
     * for every folder whose
     * name looks like a mistyped username, so the instructor is alerted before any
     * fix is run.
     */
    public PreflightReport checkSubmissions(Path workingDir) {
        PreflightReport report = SubmissionPreflight.check(
                config.startingFolder().toPath(),
                workingDir);

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

    public ValidationReport validateSubmissions(Path workingDir, PreflightReport preflight,
            Predicate<String> confirm) throws IOException {
        List<Student> students = ScoresheetReader.read(config.scoresheetFile());
        return new SubmissionValidation(
                students,
                confirm,
                cache,
                config.startingFolder().toPath()).run(workingDir, preflight);
    }

    public ValidationReport validateSubmissions(File submissions, PreflightReport preflight,
            Predicate<String> confirm) throws IOException {
        return validateSubmissions(submissions.toPath(), preflight, confirm);
    }
}
