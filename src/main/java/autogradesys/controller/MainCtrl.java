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
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public File getStartingFolder() {
        return configDAO.getStartingFolder();
    }

    /**
     * Returns the folder holding the instructor tester files.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public File getTestersFolder() {
        return configDAO.getTestersFolder();
    }

    /**
     * Returns the CSV file used by this scoresheet data access object.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public File getScoresheetFile() {
        return configDAO.getScoresheetFile();
    }

    /**
     * Returns the maximum number of seconds allowed for one grading process.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public long getTimeoutSeconds() {
        return configDAO.getTimeoutSeconds();
    }

    /**
     * Returns the maximum number of process-output bytes retained during grading.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public long getOutputLimitBytes() {
        return configDAO.getOutputLimitBytes();
    }

    /**
     * Returns the largest timeout value accepted by configuration input.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public long getMaxTimeoutSeconds() {
        return ConfigDAO.MAX_TIMEOUT_SECONDS;
    }

    /**
     * Returns the largest output limit accepted by configuration input.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public long getMaxOutputLimitBytes() {
        return ConfigDAO.MAX_OUTPUT_LIMIT_BYTES;
    }

    /**
     * Returns the current application configuration object.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public Config getConfig() {
        return new Config(getStartingFolder(), getTestersFolder(), getScoresheetFile(), getTimeoutSeconds(), getOutputLimitBytes());
    }


    /**
     * Loads configuration values from disk and reports whether the file was usable.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public boolean loadConfig() {
        return configDAO.load();
    }

    /**
     * Saves the supplied folders and grading limits through the configuration controller.
     * @param startingFolder the argument supplied to this operation.
     * @param testers the argument supplied to this operation.
     * @param scoresheet the CSV scoresheet to read or update.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public boolean saveConfig(File startingFolder, File testers, File scoresheet) {
        configDAO.setStartingFolder(startingFolder);
        configDAO.setTestersFolder(testers);
        configDAO.setScoresheetFile(scoresheet);

        return configDAO.save();
    }

    /** Saves the paths and grading limits together.
     * @param startingFolder the argument supplied to this operation.
     * @param testers the argument supplied to this operation.
     * @param scoresheet the CSV scoresheet to read or update.
     * @param timeoutSeconds the argument supplied to this operation.
     * @param outputLimitBytes the argument supplied to this operation.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public boolean saveConfig(File startingFolder, File testers, File scoresheet,
            long timeoutSeconds, long outputLimitBytes) {
        configDAO.setTimeoutSeconds(timeoutSeconds);
        configDAO.setOutputLimitBytes(outputLimitBytes);
        return saveConfig(startingFolder, testers, scoresheet);
    }

}
