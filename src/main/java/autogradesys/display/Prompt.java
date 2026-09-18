package autogradesys.display;

public enum Prompt {
    SUBMISSIONS_PROMPT(
            "Enter submissions folder path  : "
        ),
    TESTERS_PROMPT(
            "Enter tester files folder path : "
        ),
    SCORESHEET_PROMPT(
            "Enter scoresheet file path     : ",
            "csv", "xls"
        );

    private String field;
    private boolean isDir;
    private String[] extensions;

    private Prompt(String field) {
        this.field = field;
        this.isDir = true;
        this.extensions = new String[0];
    }

    private Prompt(String field, String... extensions) {
        this.field = field;
        this.isDir = false;
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
