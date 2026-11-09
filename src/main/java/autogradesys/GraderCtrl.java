package autogradesys;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import autogradesys.config.Config;
import autogradesys.preflight.PreflightReport;
import autogradesys.validation.CacheReport;
import autogradesys.validation.ScoresheetReader;
import autogradesys.validation.Student;
import autogradesys.validation.SubmissionCache;
import autogradesys.validation.SubmissionValidation;
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

    public ValidationReport validateSubmissions(Path workingDir, PreflightReport preflight,
            Predicate<String> confirm) throws IOException {
        List<Student> students = ScoresheetReader.read(config.scoresheetFile());
        return new SubmissionValidation(students, confirm, cache).run(workingDir, preflight);
    }

    public ValidationReport validateSubmissions(File submissions, PreflightReport preflight,
            Predicate<String> confirm) throws IOException {
        return validateSubmissions(submissions.toPath(), preflight, confirm);
    }
}
