package autogradesys;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import autogradesys.config.Config;
import autogradesys.config.ConfigDAO;
import autogradesys.preflight.PreflightReport;
import autogradesys.validation.CacheReport;
import autogradesys.validation.ScoresheetReader;
import autogradesys.validation.Student;
import autogradesys.validation.SubmissionCache;
import autogradesys.validation.SubmissionValidation;
import autogradesys.validation.ValidationReport;

public class MainCtrl {
    
    private ConfigDAO configDAO;

    public MainCtrl() {
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

    public long getTimeoutSeconds() {
        return configDAO.getTimeoutSeconds();
    }

    public long getOutputLimitBytes() {
        return configDAO.getOutputLimitBytes();
    }

    public long getMaxTimeoutSeconds() {
        return ConfigDAO.MAX_TIMEOUT_SECONDS;
    }

    public long getMaxOutputLimitBytes() {
        return ConfigDAO.MAX_OUTPUT_LIMIT_BYTES;
    }

    public Config getConfig() {
        return new Config(getStartingFolder(), getTestersFolder(), getScoresheetFile(), getTimeoutSeconds(), getOutputLimitBytes());
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

    // Saves the paths and grading limits together.
    public boolean saveConfig(File startingFolder, File testers, File scoresheet,
            long timeoutSeconds, long outputLimitBytes) {
        configDAO.setTimeoutSeconds(timeoutSeconds);
        configDAO.setOutputLimitBytes(outputLimitBytes);
        return saveConfig(startingFolder, testers, scoresheet);
    }

}
