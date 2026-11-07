package autogradesys.cli;

public enum Field {
    STARTING_FOLDER(
            "Starting Folder"),
    SUBMISSIONS(
            "Submissions Folder"),
    TESTERS(
            "Tester Files Folder"),
    SCORESHEET(
            "Scoresheet File",
            "csv");

    private String name;
    private String[] extensions;

    private Field(String field) {
        this.name = field;
        this.extensions = new String[0];
    }

    private Field(String field, String... extensions) {
        this.name = field;
        this.extensions = extensions;
    }

    public String getName() {
        return this.name;
    }

    public boolean isDir() {
        return this.extensions.length == 0;
    }

    public String[] getExtensions() {
        return this.extensions;
    }

    public String getDescription() {
        if (isDir()) {
            return "folder";
        }

        // Files
        String[] tempExt = this.extensions.clone();

        if (this.extensions.length >= 2) {
            tempExt[tempExt.length - 1] = "and " + tempExt[tempExt.length - 1];
        }

        if (tempExt.length == 2) {
            return String.join(" ", tempExt) + " file";
        } else {
            return String.join(", ", tempExt) + " file";
        }
    }

}
