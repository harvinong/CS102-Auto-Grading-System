package autogradesys;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import autogradesys.config.ConfigDAO;
import autogradesys.preflight.PreflightReport;
import autogradesys.validation.CacheReport;
import autogradesys.validation.ScoresheetReader;
import autogradesys.validation.Student;
import autogradesys.validation.SubmissionCache;
import autogradesys.validation.SubmissionValidation;
import autogradesys.validation.ValidationReport;

public class AutoGradeCtrl {
    
    private ConfigDAO configDAO;
    private final SubmissionCache cache = new SubmissionCache(SubmissionCache.DEFAULT_ROOT);

    public AutoGradeCtrl() {
        this.configDAO = new ConfigDAO();
    }

    // Getters
    public File getStartingFolder() {
        return configDAO.getStartingFolder();
    }

    public File getTestersFolder() {
        return configDAO.getTestersFolder();
    }

    public File getScoresheetFile() {
        return configDAO.getScoresheetFile();
    }

    
    public boolean loadConfig() {
        return configDAO.load();
    }

    public boolean saveConfig(File startingFolder, File testers, File scoresheet) {
        configDAO.setStartingFolder(startingFolder);
        configDAO.setTestersFolder(testers);
        configDAO.setScoresheetFile(scoresheet);

        return configDAO.save();
    }

    public CacheReport prepareWorkingCopy(File submissions) {
        return cache.prepare(submissions.toPath());
    }

    public ValidationReport validateSubmissions(Path workingDir, PreflightReport preflight,
            Predicate<String> confirm) throws IOException {
        List<Student> students = ScoresheetReader.read(configDAO.getScoresheetFile());
        return new SubmissionValidation(students, confirm, cache).run(workingDir, preflight);
    }

    /**
     * Fixes common mistakes in the submissions in place (see {@link SubmissionValidation}).
     * 
     * @param submissions The folder of student submissions.
     * @param preflight   The preflight report of that folder.
     * @param confirm     Asks the instructor a yes/no question; true means yes.
     * @return What was fixed and what still needs attention.
     * @throws IOException If the scoresheet cannot be read.
     */
    public ValidationReport validateSubmissions(File submissions, PreflightReport preflight,
            Predicate<String> confirm) throws IOException {
        List<Student> students = ScoresheetReader.read(configDAO.getScoresheetFile());
        return new SubmissionValidation(students, confirm, cache).run(submissions.toPath(), preflight);
    }

}
