package autogradesys.display;

public enum Prompt {
    SUBMISSIONS_PROMPT(
        "Enter submissions folder path : "
        ),
    TESTERS_PROMPT(
        "Enter tester files folder path : "
    ),
    SCORESHEET_PROMPT(
        "Enter scoresheet file path : ",
        "csv"
    );

    private String field;
    private String[] extensions;

    private Prompt(String field) {
        this.field = field;
        this.extensions = null;
    }
    
    private Prompt(String field, String... extensions) {
        this.field = field;
        this.extensions = extensions;
    }

    public String getField() {
        return this.field;
    }

    public boolean isDir() {
        return this.extensions == null;
    }

    public String[] getExtensions() {
        return this.extensions;
    }

}
