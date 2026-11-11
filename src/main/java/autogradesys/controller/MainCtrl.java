package autogradesys.controller;

import java.io.File;

import autogradesys.config.Config;
import autogradesys.config.ConfigDAO;

public class MainCtrl {
    
    private ConfigDAO configDAO;

    /**
     * Initializes the controller that loads and saves application configuration.
     */
    public MainCtrl() {
        this.configDAO = new ConfigDAO();
    }

    // Getters
    /**
     * Returns the root folder selected for locating submissions and grading inputs.
     */
    public File getStartingFolder() {
        return configDAO.getStartingFolder();
    }

    /**
     * Returns the folder holding the instructor tester files.
     */
    public File getTestersFolder() {
        return configDAO.getTestersFolder();
    }

    /**
     * Returns the CSV file used by this scoresheet data access object.
     */
    public File getScoresheetFile() {
        return configDAO.getScoresheetFile();
    }

    /**
     * Returns the maximum number of seconds allowed for one grading process.
     */
    public long getTimeoutSeconds() {
        return configDAO.getTimeoutSeconds();
    }

    /**
     * Returns the maximum number of process-output bytes retained during grading.
     */
    public long getOutputLimitBytes() {
        return configDAO.getOutputLimitBytes();
    }

    /**
     * Returns the largest timeout value accepted by configuration input.
     */
    public long getMaxTimeoutSeconds() {
        return ConfigDAO.MAX_TIMEOUT_SECONDS;
    }

    /**
     * Returns the largest output limit accepted by configuration input.
     */
    public long getMaxOutputLimitBytes() {
        return ConfigDAO.MAX_OUTPUT_LIMIT_BYTES;
    }

    /**
     * Returns the current application configuration object.
     */
    public Config getConfig() {
        return new Config(getStartingFolder(), getTestersFolder(), getScoresheetFile(), getTimeoutSeconds(), getOutputLimitBytes());
    }


    /**
     * Loads configuration values from disk and reports whether the file was usable.
     */
    public boolean loadConfig() {
        return configDAO.load();
    }

    /**
     * Saves the supplied folders and grading limits through the configuration controller.
     */
    public boolean saveConfig(File startingFolder, File testers, File scoresheet) {
        configDAO.setStartingFolder(startingFolder);
        configDAO.setTestersFolder(testers);
        configDAO.setScoresheetFile(scoresheet);

        return configDAO.save();
    }

    /** Saves the paths and grading limits together. */
    public boolean saveConfig(File startingFolder, File testers, File scoresheet,
            long timeoutSeconds, long outputLimitBytes) {
        configDAO.setTimeoutSeconds(timeoutSeconds);
        configDAO.setOutputLimitBytes(outputLimitBytes);
        return saveConfig(startingFolder, testers, scoresheet);
    }

}
