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
     * Creates a field that asks for a folder.
     *
     * @param field the words shown to the user when this field is requested, e.g. "Starting Folder"
     */
    private Field(String field) {
        this.name = field;
        this.extensions = new String[0];
    }

    // A file field: only the listed extensions are accepted.
    /**
     * Creates a field that asks for a file with one of the given extensions.
     *
     * @param field the words shown to the user when this field is requested
     * @param extensions the accepted file extensions without a dot, e.g. {@code "csv"}
     */
    private Field(String field, String... extensions) {
        this.name = field;
        this.extensions = extensions;
    }

    /**
     * Gives the wording used to ask for this field.
     *
     * @return the display name, such as "Scoresheet File"
     */
    public String getName() {
        return this.name;
    }

    /**
     * Tells whether this field is a folder rather than a file.
     *
     * @return true if no file extensions are required (a folder); false if it expects a file
     */
    public boolean isDir() {
        // A field with no extensions is a folder.
        return this.extensions.length == 0;
    }

    /**
     * Gives the file extensions this field accepts.
     *
     * @return the accepted extensions without a dot; an empty array for a folder field. The array is the field's own, so callers must not modify it
     */
    public String[] getExtensions() {
        return this.extensions;
    }

    /**
     * Builds a short phrase naming what the field expects, for use in messages.
     *
     * @return {@code "folder"} for a folder field, otherwise the extensions followed by "file", e.g.
     *         {@code "csv file"} or {@code "csv and txt file"}
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
