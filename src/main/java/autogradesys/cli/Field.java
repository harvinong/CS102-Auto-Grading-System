package autogradesys.cli;

public enum Field {
    // The blank starter template folder (a directory, so no extensions).
    STARTING_FOLDER(
            "Starting Folder"),
    // The folder holding the students' submissions.
    SUBMISSIONS(
            "Submissions Folder"),
    // The folder holding the instructor's tester files.
    TESTERS(
            "Tester Files Folder"),
    // The scoresheet, which must be a .csv file.
    SCORESHEET(
            "Scoresheet File",
            "csv");

    // The words shown to the user when this field is asked for.
    private String name;
    // The file extensions allowed; empty means the field is a folder.
    private String[] extensions;

    // A folder field: no extensions are allowed.
    /**
     * Describes one prompted input field, including its type and accepted extensions.
     */
    private Field(String field) {
        this.name = field;
        this.extensions = new String[0];
    }

    // A file field: only the listed extensions are accepted.
    /**
     * Describes one prompted input field, including its type and accepted extensions.
     */
    private Field(String field, String... extensions) {
        this.name = field;
        this.extensions = extensions;
    }

    /**
     * Returns the prompt name used when displaying this field.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Reports whether this field expects a directory rather than a regular file.
     */
    public boolean isDir() {
        // A field with no extensions is a folder.
        return this.extensions.length == 0;
    }

    /**
     * Returns the file extensions accepted by this input field.
     */
    public String[] getExtensions() {
        return this.extensions;
    }

    /**
     * Returns the human-readable prompt description for this field.
     */
    public String getDescription() {
        // Folders are described with one word.
        if (isDir()) {
            return "folder";
        }

        // Files
        // Work on a copy so the real list is not changed.
        String[] tempExt = this.extensions.clone();

        // With two or more extensions, put "and" before the last one.
        if (this.extensions.length >= 2) {
            tempExt[tempExt.length - 1] = "and " + tempExt[tempExt.length - 1];
        }

        // Two extensions read "csv and txt file"; otherwise they are comma separated.
        if (tempExt.length == 2) {
            return String.join(" ", tempExt) + " file";
        } else {
            return String.join(", ", tempExt) + " file";
        }
    }

}
