package autogradesys.controller;

import java.io.File;

import autogradesys.config.Config;
import autogradesys.config.ConfigDAO;

public class MainCtrl {
    
    private ConfigDAO configDAO;

    /**
     * Creates a main ctrl instance.
     */
    public MainCtrl() {
        this.configDAO = new ConfigDAO();
    }

    // Getters
    /**
     * Returns starting folder.
     */
    public File getStartingFolder() {
        return configDAO.getStartingFolder();
    }

    /**
     * Returns testers folder.
     */
    public File getTestersFolder() {
        return configDAO.getTestersFolder();
    }

    /**
     * Returns scoresheet file.
     */
    public File getScoresheetFile() {
        return configDAO.getScoresheetFile();
    }

    /**
     * Returns timeout seconds.
     */
    public long getTimeoutSeconds() {
        return configDAO.getTimeoutSeconds();
    }

    /**
     * Returns output limit bytes.
     */
    public long getOutputLimitBytes() {
        return configDAO.getOutputLimitBytes();
    }

    /**
     * Returns max timeout seconds.
     */
    public long getMaxTimeoutSeconds() {
        return ConfigDAO.MAX_TIMEOUT_SECONDS;
    }

    /**
     * Returns max output limit bytes.
     */
    public long getMaxOutputLimitBytes() {
        return ConfigDAO.MAX_OUTPUT_LIMIT_BYTES;
    }

    /**
     * Returns config.
     */
    public Config getConfig() {
        return new Config(getStartingFolder(), getTestersFolder(), getScoresheetFile(), getTimeoutSeconds(), getOutputLimitBytes());
    }


    /**
     * Load config.
     */
    public boolean loadConfig() {
        return configDAO.load();
    }

    /**
     * Save config.
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
