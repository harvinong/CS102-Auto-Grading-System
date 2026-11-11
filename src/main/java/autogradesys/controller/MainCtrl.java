package autogradesys.controller;

import java.io.File;

import autogradesys.config.Config;
import autogradesys.config.ConfigDAO;

public class MainCtrl {
    
    private ConfigDAO configDAO;

    /**
     * Creates the controller with a DAO for {@code config.json} in the current working directory. Nothing is loaded until {@link #loadConfig()}.
     */
    public MainCtrl() {
        this.configDAO = new ConfigDAO();
    }

    // Getters
    /**
     * Gives the blank starter template folder.
     *
     * @return the folder, or null if no configuration has been loaded or saved
     */
    public File getStartingFolder() {
        return configDAO.getStartingFolder();
    }

    /**
     * Gives the folder of instructor tester files.
     *
     * @return the folder, or null if no configuration has been loaded or saved
     */
    public File getTestersFolder() {
        return configDAO.getTestersFolder();
    }

    /**
     * Gives the course scoresheet CSV.
     *
     * @return the file, or null if no configuration has been loaded or saved
     */
    public File getScoresheetFile() {
        return configDAO.getScoresheetFile();
    }

    /**
     * Gives the time limit for one compile or run.
     *
     * @return seconds per process; the default of 2 until another value is loaded or saved
     */
    public long getTimeoutSeconds() {
        return configDAO.getTimeoutSeconds();
    }

    /**
     * Gives the cap on output captured from one process.
     *
     * @return bytes per process; the default of 1 MiB until another value is loaded or saved
     */
    public long getOutputLimitBytes() {
        return configDAO.getOutputLimitBytes();
    }

    /**
     * Gives the upper bound for the timeout, so the menu can state and enforce it.
     *
     * @return the largest timeout accepted, in seconds
     */
    public long getMaxTimeoutSeconds() {
        return ConfigDAO.MAX_TIMEOUT_SECONDS;
    }

    /**
     * Gives the upper bound for the output cap, so the menu can state and enforce it.
     *
     * @return the largest output cap accepted, in bytes
     */
    public long getMaxOutputLimitBytes() {
        return ConfigDAO.MAX_OUTPUT_LIMIT_BYTES;
    }

    /**
     * Bundles the current settings for the grading steps.
     *
     * @return a snapshot of the three paths and two limits; the paths are null if nothing has been loaded or saved
     */
    public Config getConfig() {
        return new Config(getStartingFolder(), getTestersFolder(), getScoresheetFile(), getTimeoutSeconds(), getOutputLimitBytes());
    }


    /**
     * Reads {@code config.json} into this controller.
     *
     * @return true if the file was read; false if it is missing or unreadable, in which case the settings stay as they were
     */
    public boolean loadConfig() {
        return configDAO.load();
    }

    /**
     * Stores the three paths and writes the configuration file, keeping the current grading limits.
     *
     * @param startingFolder the blank starter template folder
     * @param testers the folder of instructor tester files
     * @param scoresheet the course scoresheet CSV
     * @return true if the file was written; false if the write failed
     */
    public boolean saveConfig(File startingFolder, File testers, File scoresheet) {
        configDAO.setStartingFolder(startingFolder);
        configDAO.setTestersFolder(testers);
        configDAO.setScoresheetFile(scoresheet);

        return configDAO.save();
    }

    /**
     * Stores the three paths and the two grading limits together, then writes the configuration file.
     *
     * @param startingFolder the blank starter template folder
     * @param testers the folder of instructor tester files
     * @param scoresheet the course scoresheet CSV
     * @param timeoutSeconds time limit per process, from 1 up to {@link #getMaxTimeoutSeconds()}
     * @param outputLimitBytes output cap per process, from 1 up to {@link #getMaxOutputLimitBytes()}
     * @return true if the file was written; false if the write failed
     * @throws IllegalArgumentException if a limit is out of range; nothing is saved then
     */
    public boolean saveConfig(File startingFolder, File testers, File scoresheet,
            long timeoutSeconds, long outputLimitBytes) {
        configDAO.setTimeoutSeconds(timeoutSeconds);
        configDAO.setOutputLimitBytes(outputLimitBytes);
        return saveConfig(startingFolder, testers, scoresheet);
    }

}
