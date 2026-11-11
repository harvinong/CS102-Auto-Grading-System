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
     * Creates one key constant.
     *
     * @param value the exact key name used in {@code config.json}
     */
    private ConfigKey(String value) {
        this.value = value;
    }

    /**
     * Gives the key name used in the JSON file.
     *
     * @return the key text, e.g. {@code starting_folder_path}
     */
    public String getValue() {
        return this.value;
    }

    @Override
    /**
     * Gives the same key text as {@link #getValue()}, so a constant can be used directly as a JSON key.
     *
     * @return the key text, e.g. {@code grading_timeout_seconds}
     */
    public String toString() {
        return this.value;
    }

}