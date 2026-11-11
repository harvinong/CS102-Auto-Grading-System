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
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    public File getStartingFolder() {
        return configDAO.getStartingFolder();
    }

    /**
     * Returns the folder holding the instructor tester files.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    public File getTestersFolder() {
        return configDAO.getTestersFolder();
    }

    /**
     * Returns the CSV file used by this scoresheet data access object.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    public File getScoresheetFile() {
        return configDAO.getScoresheetFile();
    }

    /**
     * Returns the maximum number of seconds allowed for one grading process.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    public long getTimeoutSeconds() {
        return configDAO.getTimeoutSeconds();
    }

    /**
     * Returns the maximum number of process-output bytes retained during grading.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    public long getOutputLimitBytes() {
        return configDAO.getOutputLimitBytes();
    }

    /**
     * Returns the largest timeout value accepted by configuration input.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    public long getMaxTimeoutSeconds() {
        return ConfigDAO.MAX_TIMEOUT_SECONDS;
    }

    /**
     * Returns the largest output limit accepted by configuration input.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    public long getMaxOutputLimitBytes() {
        return ConfigDAO.MAX_OUTPUT_LIMIT_BYTES;
    }

    /**
     * Returns the current application configuration object.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    public Config getConfig() {
        return new Config(getStartingFolder(), getTestersFolder(), getScoresheetFile(), getTimeoutSeconds(), getOutputLimitBytes());
    }


    /**
     * Loads configuration values from disk and reports whether the file was usable.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    public boolean loadConfig() {
        return configDAO.load();
    }

    /**
     * Saves the supplied folders and grading limits through the configuration controller.
     * @param startingFolder the value used by this method.
     * @param testers the value used by this method.
     * @param scoresheet the value used by this method.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    public boolean saveConfig(File startingFolder, File testers, File scoresheet) {
        configDAO.setStartingFolder(startingFolder);
        configDAO.setTestersFolder(testers);
        configDAO.setScoresheetFile(scoresheet);

        return configDAO.save();
    }

    /** Saves the paths and grading limits together.
     * @param startingFolder the value used by this method.
     * @param testers the value used by this method.
     * @param scoresheet the value used by this method.
     * @param timeoutSeconds the value used by this method.
     * @param outputLimitBytes the value used by this method.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    public boolean saveConfig(File startingFolder, File testers, File scoresheet,
            long timeoutSeconds, long outputLimitBytes) {
        configDAO.setTimeoutSeconds(timeoutSeconds);
        configDAO.setOutputLimitBytes(outputLimitBytes);
        return saveConfig(startingFolder, testers, scoresheet);
    }

}
