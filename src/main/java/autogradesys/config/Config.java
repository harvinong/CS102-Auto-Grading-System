package autogradesys.config;

import java.io.File;

/**
 * Convenient Configuration object (record)
 * 
 * @param startingFolder the argument supplied to this operation.
 * @param testersFolder the argument supplied to this operation.
 * @param scoresheetFile the argument supplied to this operation.
 * @param timeoutSeconds the argument supplied to this operation.
 * @param outputLimitBytes the argument supplied to this operation.
 */
public record Config(File startingFolder, File testersFolder, File scoresheetFile, long timeoutSeconds, long outputLimitBytes) {}
