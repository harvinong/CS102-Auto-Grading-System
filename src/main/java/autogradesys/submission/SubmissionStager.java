package autogradesys.submission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import autogradesys.config.AppSettings;
import autogradesys.grading.ErrorReporter;
import autogradesys.io.FolderDeleter;

/**
 * Unzips every submission into the cache folder, one folder per student, so the checker and
 * grader can work on normal folders. A bad zip is reported and skipped, not fatal.
 */
public class SubmissionStager {
    private static final String STAGING_FOLDER = "staged-submissions";

    private final AppSettings settings;

    public SubmissionStager(AppSettings settings) {
        this.settings = settings;
    }

    public StagedSubmissions stage(Path submissionsFolder, ErrorReporter reporter) throws IOException {
        SubmissionIterator submissions = new SubmissionIterator(submissionsFolder, settings.getZipNamePattern());

        if (submissions.size() == 0) {
            return new StagedSubmissions(submissionsFolder, false);
        }

        Path stagingRoot = settings.getCacheDir().resolve(STAGING_FOLDER).toAbsolutePath().normalize();
        FolderDeleter.delete(stagingRoot);
        Files.createDirectories(stagingRoot);

        reporter.info("Extracting " + submissions.size() + " zipped submission(s)...");
        if (!submissions.getSkipped().isEmpty()) {
            reporter.info("Skipping (not a submission zip): " + String.join(", ", submissions.getSkipped()));
        }

        for (String message : submissions.getDuplicates()) {
            reporter.recoverable("submission", "duplicate", message,
                    "Check that the right zip was used.", null, null, null);
        }

        int position = 0;
        for (Submission submission : submissions) {
            position++;
            reporter.info("[" + position + "/" + submissions.size() + "] " + submission.getUsername());
            extract(submission, stagingRoot, reporter);
        }
        return new StagedSubmissions(stagingRoot, true);
    }

    private void extract(Submission submission, Path stagingRoot, ErrorReporter reporter) {
        String zipName = submission.getZipPath().getFileName().toString();
        Path target = stagingRoot.resolve(submission.getUsername()).normalize();

        try {
            if (!stagingRoot.equals(target.getParent())) {
                throw new IOException("the username cannot be used as a folder name");
            }
            // ArchiveExtractor creates the destination atomically and enforces zip-slip/size limits.
            SubmissionUnzipper.unzip(submission.getZipPath(), target);
        } catch (IOException | RuntimeException e) {
            deleteQuietly(target, stagingRoot);
            reporter.recoverable("submission", "extraction",
                    "Could not extract " + zipName + ": " + e.getMessage(),
                    "Ask the student to resubmit a valid zip file.",
                    submission.getUsername(), zipName, null);
        }
    }

    private void deleteQuietly(Path folder, Path stagingRoot) {
        if (!stagingRoot.equals(folder.getParent())) {
            return;
        }
        try {
            FolderDeleter.delete(folder);
        } catch (IOException e) {
        }
    }
}
