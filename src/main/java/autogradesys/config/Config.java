package autogradesys.config;

import java.io.File;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Convenient Configuration object (record)
 * 
 * @param startingFolder
 * @param testersFolder
 * @param scoresheetFile
 * @param timeoutSeconds
 * @param outputLimitBytes
 */
public record Config(File startingFolder, File testersFolder, File scoresheetFile, long timeoutSeconds, long outputLimitBytes) {}
