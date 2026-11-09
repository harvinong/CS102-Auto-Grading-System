package autogradesys.config;

import java.io.File;

/**
 * Convenient Configuration object (record)
 * 
 * @param startingFolder The template where students modify this folder during lab test/exam.
 * @param testersFolder A folder containing tester files to be used for grading.
 * @param scoresheetFile A csv file containing a list of students along with their grades.
 * @param timeoutSeconds The maximum duration for each compile or execution process for each question.
 * @param outputLimitBytes Cap on captured stdout plus stderr per process/question.
 */
public record Config(File startingFolder, File testersFolder, File scoresheetFile, long timeoutSeconds, long outputLimitBytes) {}
