package autogradesys.grading;

import java.io.File;
import java.nio.file.Path;

/**
 * Tester file record.
 * 
 * @param forQuestion Q1, Q2, Q3a, Q3b
 * @param origin the origin of the file
 */
public record TesterFile(String forQuestion, File origin) {}