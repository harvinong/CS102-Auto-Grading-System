package autogradesys.cli;

public enum TextColor {
    SUCCESS("\33[1;32m"),
    INFO("\33[1;36m"),
    WARNING("\33[1;33m"),
    ERROR("\33[1;31m");
    
    private static final String SUFFIX_ESCAPE_SEQUENCE = "\33[m";
    private String prefixEscapeCode;

    private TextColor(String prefixEscapeCode) {
        this.prefixEscapeCode = prefixEscapeCode;
    }

    /**Apply color to string when printed out to console.
     * 
     * @param s String to be colored
     * @return Colored string
     */
    public String apply(String s) {
        return this.prefixEscapeCode + s + SUFFIX_ESCAPE_SEQUENCE;
    }

    /** Get action tag such as [INFO] with its color */
    public String getActionTag() {
        return apply("[" + this.toString() + "]");
    }

}
