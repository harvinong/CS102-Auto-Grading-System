package autogradesys.controller;

import java.io.File;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import autogradesys.cli.TextColor;
import autogradesys.config.Config;
import autogradesys.config.ConfigDAO;
import autogradesys.io.CompilerNotFoundException;

public class MainCtrl {
    
    private ConfigDAO configDAO;
    private JavaCompiler compiler;

    public MainCtrl() {
        this.configDAO = new ConfigDAO();
        boolean compilerFound = setCompiler();
        if (!compilerFound) {
            throw new CompilerNotFoundException();
        }
    }

    // Getters
    public JavaCompiler getCompiler() {
        return compiler;
    }

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

    /** Saves the paths and grading limits together. */
    public boolean saveConfig(File startingFolder, File testers, File scoresheet,
            long timeoutSeconds, long outputLimitBytes) {
        configDAO.setTimeoutSeconds(timeoutSeconds);
        configDAO.setOutputLimitBytes(outputLimitBytes);
        return saveConfig(startingFolder, testers, scoresheet);
    }

    // helper function
    private boolean setCompiler() {
        compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return false;
        }
        return true;
    }

}
