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

    private ConfigKey(String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }

    @Override
    public String toString() {
        return this.value;
    }

}