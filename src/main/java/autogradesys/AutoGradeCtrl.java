package autogradesys;

import java.io.File;
import java.io.IOException;

import autogradesys.config.AppSettings;
import autogradesys.config.ConfigDAO;
import autogradesys.grading.ErrorReporter;
import autogradesys.submission.StagedSubmissions;
import autogradesys.submission.SubmissionStager;

public class AutoGradeCtrl {

    private ConfigDAO configDAO;
    private final SubmissionStager stager;

    public AutoGradeCtrl() {
        this.configDAO = new ConfigDAO();
        this.stager = new SubmissionStager(AppSettings.load());
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

    public StagedSubmissions stageSubmissions(File submissionsFolder, ErrorReporter reporter) throws IOException {
        return stager.stage(submissionsFolder.toPath(), reporter);
    }

}
