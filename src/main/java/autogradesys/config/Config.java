package autogradesys.config;

import java.io.File;

/**
 * Convenient Configuration object (record)
 * 
 * @param startingFolder the value used by this method.
 * @param testersFolder the value used by this method.
 * @param scoresheetFile the value used by this method.
 * @param timeoutSeconds the value used by this method.
 * @param outputLimitBytes the value used by this method.
 */
public record Config(File startingFolder, File testersFolder, File scoresheetFile, long timeoutSeconds, long outputLimitBytes) {}
