package autogradesys.config;

/**
 * ConfigKey
 */
enum ConfigKey {

    SUBMISSIONS("submissions_path"), 
    TESTERS("testers_path"), 
    SCORESHEET("scoresheet_path");

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