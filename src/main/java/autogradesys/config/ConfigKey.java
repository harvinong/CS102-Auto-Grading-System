package autogradesys.config;

/**
 * ConfigKey
 */
enum ConfigKey {

    STARTING_FOLDER("starting_folder_path"),
    TESTERS("testers_path"),
    SCORESHEET("scoresheet_path"),
    // Optional keys: older config.json files do not have them, so ConfigDAO falls back to its defaults when they are absent.
    TIMEOUT_SECONDS("grading_timeout_seconds"),
    OUTPUT_LIMIT_BYTES("output_limit_bytes");

    private String value;

    /**
     * Stores the JSON key name used to persist one configuration value.
     * @param value the value used by this method.
     */
    private ConfigKey(String value) {
        this.value = value;
    }

    /**
     * Returns the configuration key string used when reading or writing JSON.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    public String getValue() {
        return this.value;
    }

    @Override
    /**
     * Returns a readable representation of the submission or configuration value.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    public String toString() {
        return this.value;
    }

}