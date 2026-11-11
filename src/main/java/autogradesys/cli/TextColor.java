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
     * Creates one colour style.
     *
     * @param prefixEscapeCode the terminal escape sequence that switches this colour on, e.g. bold green
     */
    private TextColor(String prefixEscapeCode) {
        this.prefixEscapeCode = prefixEscapeCode;
    }

    /**
     * Colours some text for the terminal, but only when colour is useful: output that is piped, redirected
     * or run without a console, or any run with the {@code NO_COLOR} environment variable set, stays plain.
     *
     * @param s the text to colour
     * @return the text wrapped in this colour's start code and the reset code, or {@code s} unchanged when colour is off
     */
    public String apply(String s) {
        // Colour only a real terminal: piped output, log files and tests get plain text, and NO_COLOR turns colour off.
        if (System.console() == null || System.getenv("NO_COLOR") != null) return s;
        // Wrap the text in this colour's start code and the reset code.
        return this.prefixEscapeCode + s + SUFFIX_ESCAPE_CODE;
    }

    /**
     * Builds a coloured tag using this constant's own name as the label, e.g. {@code [INFO]}.
     *
     * @return the name in square brackets, coloured as {@link #apply} does
     */
    public String getActionTag() {
        return getActionTag(this.toString());
    }

    /**
     * Builds a coloured tag with custom label text, e.g. {@code [DONE]}.
     *
     * @param s the text to place inside the square brackets
     * @return the bracketed text, coloured as {@link #apply} does
     */
    public String getActionTag(String s) {
        return apply("[" + s + "]");
    }

}
