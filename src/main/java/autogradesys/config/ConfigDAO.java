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

    // Per-process is for one question.
    // Per-process time limit used when config.json has no valid "grading_timeout_seconds"; matches the old hard-coded 2 seconds.
    public static final long DEFAULT_TIMEOUT_SECONDS = 2;
    // Cap on captured stdout plus stderr per process when config.json has no valid "output_limit_bytes"; 1 MiB as before.
    public static final long DEFAULT_OUTPUT_LIMIT_BYTES = 1L << 20;
    // Largest accepted timeout: one hour per process. Bigger values would let a looping submission stall grading for days, and past about 9e15 seconds the conversion to milliseconds overflows and crashes grading.
    public static final long MAX_TIMEOUT_SECONDS = 3600;
    // Largest accepted output cap: 64 MiB per process. The grader keeps the captured output in memory, so a much larger cap could exhaust the heap on a flooding submission.
    public static final long MAX_OUTPUT_LIMIT_BYTES = 64L << 20;

    // The file this instance reads and writes; the default constructor points it at ./config.json.
    private final File configFile;
    
    private File startingFolder;
    private File testersFolder;
    private File scoresheetFile;
    // Start on the defaults so a DAO that never loads (new user) or loads an older file still has usable limits.
    private long timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    private long outputLimitBytes = DEFAULT_OUTPUT_LIMIT_BYTES;

    /**
     * Creates a DAO that reads and writes {@code config.json} in the current working directory.
     */
    public ConfigDAO() {
        this(DEF_CONFIG_FILE);
    }

    // Lets tests point the DAO at a temporary file instead of the developer's ./config.json.
    /**
     * Creates a DAO for a specific configuration file, which lets tests avoid touching the real {@code config.json}.
     *
     * @param configFile the JSON file to read and write; it need not exist yet
     */
    public ConfigDAO(File configFile) {
        this.configFile = configFile;
    }

    /**
     * Reads the three paths and the two optional grading limits from the configuration file. A missing,
     * non-integer or out-of-range limit silently falls back to its default instead of failing the load.
     *
     * @return true if the file was read and the three paths were present; false if the file does not exist or cannot be parsed
     * @throws NullPointerException if the file parses but lacks one of the three required path keys; this is not caught
     */
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

    /**
     * Writes the current folders and grading limits to the configuration file as pretty-printed JSON, with
     * the paths made absolute. The limits are always written, even when they are the defaults.
     *
     * @return true if the file was written; false if it could not be opened or written
     * @throws NullPointerException if any of the three folders or files has not been set
     */
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

    /**
     * Gives the blank starter template folder.
     *
     * @return the folder, or null if none has been loaded or set
     */
    public File getStartingFolder() {
        return startingFolder;
    }

    /**
     * Gives the folder of instructor tester files.
     *
     * @return the folder, or null if none has been loaded or set
     */
    public File getTestersFolder() {
        return testersFolder;
    }

    /**
     * Gives the course scoresheet CSV.
     *
     * @return the CSV file, or null if none has been loaded or set
     */
    public File getScoresheetFile() {
        return scoresheetFile;
    }

    /**
     * Gives the time limit for one compile or run.
     *
     * @return seconds from 1 to {@link #MAX_TIMEOUT_SECONDS}; {@link #DEFAULT_TIMEOUT_SECONDS} if never set
     */
    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * Gives the cap on output captured from one process.
     *
     * @return bytes from 1 to {@link #MAX_OUTPUT_LIMIT_BYTES}; {@link #DEFAULT_OUTPUT_LIMIT_BYTES} if never set
     */
    public long getOutputLimitBytes() {
        return outputLimitBytes;
    }

    /**
     * Sets the blank starter template folder. It is not saved until {@link #save()}.
     *
     * @param startingFolder the template folder; nothing checks that it exists
     */
    public void setStartingFolder(File startingFolder) {
        this.startingFolder = startingFolder;
    }

    /**
     * Sets the folder of instructor tester files. It is not saved until {@link #save()}.
     *
     * @param testersFolder the testers folder; nothing checks that it exists
     */
    public void setTestersFolder(File testersFolder) {
        this.testersFolder = testersFolder;
    }

    /**
     * Sets the course scoresheet CSV. It is not saved until {@link #save()}.
     *
     * @param scoresheetFile the CSV file; nothing checks that it exists
     */
    public void setScoresheetFile(File scoresheetFile) {
        this.scoresheetFile = scoresheetFile;
    }

    // Rejects out-of-range limits here too, so a caller that skips the menu prompt cannot save a value that would stop every process at once or crash grading.
    /**
     * Sets the time limit for one compile or run. It is not saved until {@link #save()}.
     *
     * @param timeoutSeconds seconds from 1 to {@link #MAX_TIMEOUT_SECONDS}
     * @throws IllegalArgumentException if the value is zero, negative or above {@link #MAX_TIMEOUT_SECONDS}
     */
    public void setTimeoutSeconds(long timeoutSeconds) {
        // A limit of 0 or less would kill every compile and run immediately; above the maximum it could hang or overflow.
        if (timeoutSeconds <= 0 || timeoutSeconds > MAX_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException("Timeout must be from 1 to " + MAX_TIMEOUT_SECONDS
                    + " seconds: " + timeoutSeconds);
        }
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Sets the cap on output captured from one process. It is not saved until {@link #save()}.
     *
     * @param outputLimitBytes bytes from 1 to {@link #MAX_OUTPUT_LIMIT_BYTES}
     * @throws IllegalArgumentException if the value is zero, negative or above {@link #MAX_OUTPUT_LIMIT_BYTES}
     */
    public void setOutputLimitBytes(long outputLimitBytes) {
        // A cap of 0 or less could not hold any output, and JavaGrader would reject it as a fatal setup error; above the maximum it risks running out of memory.
        if (outputLimitBytes <= 0 || outputLimitBytes > MAX_OUTPUT_LIMIT_BYTES) {
            throw new IllegalArgumentException("Output limit must be from 1 to " + MAX_OUTPUT_LIMIT_BYTES
                    + " bytes: " + outputLimitBytes);
        }
        this.outputLimitBytes = outputLimitBytes;
    }

    // Reads an optional JSON number: only a whole number from 1 to max counts, everything else (absent, text, 1.5, 0, -3, too big) gives the default.
    /**
     * Reads an optional JSON number, accepting only a whole number from 1 to {@code max}.
     *
     * @param node the JSON value for the key; null when the key is absent
     * @param max the largest value accepted (inclusive)
     * @param defaultValue what to return when the value is absent or not acceptable
     * @return the number in {@code node}, or {@code defaultValue} if it is absent, text, a decimal, zero, negative or above {@code max}
     */
    private static long integerInRangeOrDefault(JsonNode node, long max, long defaultValue) {
        // node is null when the key is missing, and isIntegralNumber() is false for strings and decimals; a long-sized value is required.
        if (node != null && node.isIntegralNumber() && node.canConvertToLong()
                && node.asLong() > 0 && node.asLong() <= max) {
            return node.asLong();
        }
        return defaultValue;
    }

}
