package autogradesys.config;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ConfigDAO {

    public static final File DEF_CONFIG_FILE = Path.of(".", "config.json").toFile();

    // Per-process time limit used when config.json has no valid "grading_timeout_seconds"; matches the old hard-coded 2 seconds.
    public static final long DEFAULT_TIMEOUT_SECONDS = 2;
    // Cap on captured stdout plus stderr per process when config.json has no valid "output_limit_bytes"; 1 MiB as before.
    public static final long DEFAULT_OUTPUT_LIMIT_BYTES = 1024 * 1024L;
    // Largest accepted timeout: one hour per process. Bigger values would let a looping submission stall grading for days, and past about 9e15 seconds the conversion to milliseconds overflows and crashes grading.
    public static final long MAX_TIMEOUT_SECONDS = 3600;
    // Largest accepted output cap: 64 MiB per process. The grader keeps the captured output in memory, so a much larger cap could exhaust the heap on a flooding submission.
    public static final long MAX_OUTPUT_LIMIT_BYTES = 64 * 1024 * 1024L;

    // The file this instance reads and writes; the default constructor points it at ./config.json.
    private final File configFile;

    private File startingFolder;
    private File testersFolder;
    private File scoresheetFile;
    // Start on the defaults so a DAO that never loads (new user) or loads an older file still has usable limits.
    private long timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    private long outputLimitBytes = DEFAULT_OUTPUT_LIMIT_BYTES;

    public ConfigDAO() {
        this(DEF_CONFIG_FILE);
    }

    // Lets tests point the DAO at a temporary file instead of the developer's ./config.json.
    public ConfigDAO(File configFile) {
        this.configFile = configFile;
    }

    public boolean load() {
        if (!configFile.exists()) {
            return false;
        }

        try {
            // Ref: https://www.geeksforgeeks.org/java/how-to-read-and-write-json-files-in-java/
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(configFile);

            // Load values
            startingFolder = Path.of(jsonNode.get(ConfigKey.STARTING_FOLDER.toString()).asText()).toFile();
            testersFolder = Path.of(jsonNode.get(ConfigKey.TESTERS.toString()).asText()).toFile();
            scoresheetFile = Path.of(jsonNode.get(ConfigKey.SCORESHEET.toString()).asText()).toFile();
            // The limits are optional: a missing, non-integer or out-of-range value silently becomes the default instead of failing the whole load.
            timeoutSeconds = integerInRangeOrDefault(jsonNode.get(ConfigKey.TIMEOUT_SECONDS.toString()),
                    MAX_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS);
            outputLimitBytes = integerInRangeOrDefault(jsonNode.get(ConfigKey.OUTPUT_LIMIT_BYTES.toString()),
                    MAX_OUTPUT_LIMIT_BYTES, DEFAULT_OUTPUT_LIMIT_BYTES);

            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean save() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode jsonNode = objectMapper.createObjectNode();

        // Write into json object
        jsonNode.put(ConfigKey.STARTING_FOLDER.toString(), this.startingFolder.toPath().toAbsolutePath().toString());
        jsonNode.put(ConfigKey.TESTERS.toString(), this.testersFolder.toPath().toAbsolutePath().toString());
        jsonNode.put(ConfigKey.SCORESHEET.toString(), this.scoresheetFile.toPath().toAbsolutePath().toString());

        // Always write the limits, so the saved file shows the values in effect even when they are the defaults.
        jsonNode.put(ConfigKey.TIMEOUT_SECONDS.toString(), this.timeoutSeconds);
        jsonNode.put(ConfigKey.OUTPUT_LIMIT_BYTES.toString(), this.outputLimitBytes);

        // Save into memory
        try (PrintStream ps = new PrintStream(configFile)) {
            ps.append(jsonNode.toPrettyString());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public File getStartingFolder() {
        return startingFolder;
    }

    public File getTestersFolder() {
        return testersFolder;
    }

    public File getScoresheetFile() {
        return scoresheetFile;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public long getOutputLimitBytes() {
        return outputLimitBytes;
    }

    public void setStartingFolder(File startingFolder) {
        this.startingFolder = startingFolder;
    }

    public void setTestersFolder(File testersFolder) {
        this.testersFolder = testersFolder;
    }

    public void setScoresheetFile(File scoresheetFile) {
        this.scoresheetFile = scoresheetFile;
    }

    // Rejects out-of-range limits here too, so a caller that skips the menu prompt cannot save a value that would stop every process at once or crash grading.
    public void setTimeoutSeconds(long timeoutSeconds) {
        // A limit of 0 or less would kill every compile and run immediately; above the maximum it could hang or overflow.
        if (timeoutSeconds <= 0 || timeoutSeconds > MAX_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException("Timeout must be from 1 to " + MAX_TIMEOUT_SECONDS
                    + " seconds: " + timeoutSeconds);
        }
        this.timeoutSeconds = timeoutSeconds;
    }

    public void setOutputLimitBytes(long outputLimitBytes) {
        // A cap of 0 or less could not hold any output, and JavaGrader would reject it as a fatal setup error; above the maximum it risks running out of memory.
        if (outputLimitBytes <= 0 || outputLimitBytes > MAX_OUTPUT_LIMIT_BYTES) {
            throw new IllegalArgumentException("Output limit must be from 1 to " + MAX_OUTPUT_LIMIT_BYTES
                    + " bytes: " + outputLimitBytes);
        }
        this.outputLimitBytes = outputLimitBytes;
    }

    // Reads an optional JSON number: only a whole number from 1 to max counts, everything else (absent, text, 1.5, 0, -3, too big) gives the default.
    private static long integerInRangeOrDefault(JsonNode node, long max, long defaultValue) {
        // node is null when the key is missing, and isIntegralNumber() is false for strings and decimals; a long-sized value is required.
        if (node != null && node.isIntegralNumber() && node.canConvertToLong()
                && node.asLong() > 0 && node.asLong() <= max) {
            return node.asLong();
        }
        return defaultValue;
    }

}
