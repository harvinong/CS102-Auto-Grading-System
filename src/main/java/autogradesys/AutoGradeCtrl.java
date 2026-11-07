package autogradesys;

import java.io.File;

import autogradesys.config.ConfigDAO;

public class AutoGradeCtrl {

    private ConfigDAO configDAO;

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

    public long getTimeoutSeconds() {
        return configDAO.getTimeoutSeconds();
    }

    public long getOutputLimitBytes() {
        return configDAO.getOutputLimitBytes();
    }

    // Upper bounds for the two limits, so the menu can validate input without reaching past the controller into the config layer.
    public long getMaxTimeoutSeconds() {
        return ConfigDAO.MAX_TIMEOUT_SECONDS;
    }

    public long getMaxOutputLimitBytes() {
        return ConfigDAO.MAX_OUTPUT_LIMIT_BYTES;
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

    // Saves the paths and the grading limits together; the setters reject non-positive limits before anything is written.
    public boolean saveConfig(File startingFolder, File testers, File scoresheet,
            long timeoutSeconds, long outputLimitBytes) {
        configDAO.setTimeoutSeconds(timeoutSeconds);
        configDAO.setOutputLimitBytes(outputLimitBytes);

        return saveConfig(startingFolder, testers, scoresheet);
    }

}
