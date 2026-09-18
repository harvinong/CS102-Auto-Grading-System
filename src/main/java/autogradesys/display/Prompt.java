package autogradesys.display;

public enum Prompt {
    SUBMISSIONS_PROMPT(
        "Enter submissions folder path  : ", 
        true),
    TESTERS_PROMPT(
        "Enter tester files folder path : ", 
        true),
    SCORESHEET_PROMPT(
        "Enter scoresheet file path     : ", 
        false, 
        "csv", "xls");

    private String field;
    private boolean isDir;
    private String[] extensions;

    private Prompt(String field, boolean isDir) {
        this.field = field;
        this.isDir = isDir;
        this.extensions = new String[0];
    }

    private Prompt(String field, boolean isDir, String... extensions) {
        this.field = field;
        this.isDir = isDir;
        this.extensions = extensions;
    }

    public String getField() {
        return this.field;
    }

    public boolean isDir() {
        return this.isDir;
    }

    public String[] getExtensions() {
        return this.extensions;
    }

}
