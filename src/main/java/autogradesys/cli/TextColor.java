package autogradesys.cli;

public enum TextColor {
    // Bold green for good news.
    SUCCESS("\33[1;32m"),
    // Bold blue for plain progress messages.
    INFO("\33[1;34m"),
    // Bold yellow for things that need a look.
    WARNING("\33[1;33m"),
    // Bold red for failures.
    ERROR("\33[1;31m");
    
    // The code that switches every colour back off.
    private static final String SUFFIX_ESCAPE_CODE = "\33[m";
    // The code that switches this colour on.
    private String prefixEscapeCode;

    // Each constant above passes its own colour code in here.
    /**
     * Creates a text color instance.
     */
    private TextColor(String prefixEscapeCode) {
        this.prefixEscapeCode = prefixEscapeCode;
    }

    /**Apply color to string when printed out to console.
     * 
     * @param s String to be colored
     * @return Colored string
     */
    public String apply(String s) {
        // Colour only a real terminal: piped output, log files and tests get plain text, and NO_COLOR turns colour off.
        if (System.console() == null || System.getenv("NO_COLOR") != null) return s;
        // Wrap the text in this colour's start code and the reset code.
        return this.prefixEscapeCode + s + SUFFIX_ESCAPE_CODE;
    }

    /** 
     * Get action tag such as [INFO] with its color
     * using the enum's own name (SUCCESS, INFO, ...) as the tag text.
     * @return the text in square brackets, then colored.
     */
    public String getActionTag() {
        return getActionTag(this.toString());
    }

    /**
     * Get action tag such as [INFO] with its color
     * @param s text to be put inside the square brackets
     * @return the text in square brackets, then colored.
     */
    public String getActionTag(String s) {
        return apply("[" + s + "]");
    }

}
