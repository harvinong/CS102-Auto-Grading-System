package autogradesys.io;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import autogradesys.validation.Student;

/**
 * Reads registered students from the CSV scoresheet. The username comes from a "Username" column
 * (the title-based export) or, failing that, from the "student" column that grading writes marks into.
 */
public final class ScoresheetReader {
    private static final String USERNAME_COLUMN = "Username";
    private static final String STUDENT_COLUMN = "student";
    private static final String EMAIL_COLUMN = "Email";
    private static final String FIRST_NAME_COLUMN = "First Name";
    private static final String LAST_NAME_COLUMN = "Last Name";
    /**
     * Prevents instantiation; the class is used through {@link #read}.
     */
    private ScoresheetReader() { }

    /**
     * Reads the registered students from the scoresheet CSV. Rows with no username, and the "% full marks"
     * summary row that grading adds, are skipped. A last name of "_" is treated as empty.
     *
     * @param scoresheet the CSV file; it needs a "Username" column or, failing that, a "student" column
     * @return one {@link Student} per row, in file order, with username (without "#"), full name and email;
     *         name and email are empty strings when their columns are absent
     * @throws IOException if the file cannot be read, is empty, has no username column, or has an unclosed quote
     */
    public static List<Student> read(File scoresheet) throws IOException {
        List<List<String>> rows = parse(Files.readString(scoresheet.toPath(), StandardCharsets.UTF_8));
        if (rows.isEmpty()) throw new IOException("The scoresheet is empty.");
        List<String> header = rows.get(0);
        int username = columnOf(header, USERNAME_COLUMN);
        if (username < 0) username = columnOf(header, STUDENT_COLUMN);
        if (username < 0) {
            throw new IOException("The scoresheet has no '" + USERNAME_COLUMN + "' or '" + STUDENT_COLUMN + "' column.");
        }
        int email = columnOf(header, EMAIL_COLUMN);
        int first = columnOf(header, FIRST_NAME_COLUMN);
        int last = columnOf(header, LAST_NAME_COLUMN);
        List<Student> result = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String name = cell(row, username).replace("#", "").trim();
            if (name.isEmpty() || name.equalsIgnoreCase("% full marks")) continue;
            String surname = cell(row, last);
            String fullName = (cell(row, first) + " " + ("_".equals(surname) ? "" : surname)).trim();
            result.add(new Student(name, fullName, cell(row, email)));
        }
        return result;
    }

    /**
     * Finds a header column by title, ignoring case and surrounding spaces.
     *
     * @param header the header cells in file order
     * @param title the column title to find
     * @return the zero-based position of the first match, or -1 if there is no such column
     */
    private static int columnOf(List<String> header, String title) {
        for (int i = 0; i < header.size(); i++) if (header.get(i).trim().equalsIgnoreCase(title)) return i;
        return -1;
    }
    /**
     * Reads one cell of a row safely, so a short row does not cause an out-of-range error.
     *
     * @param row the cells of one row
     * @param column the zero-based column wanted; -1 means the column does not exist
     * @return the trimmed cell text, or an empty string if the column is -1 or beyond the end of the row
     */
    private static String cell(List<String> row, int column) {
        return column < 0 || column >= row.size() ? "" : row.get(column).trim();
    }
    /**
     * Splits CSV text into rows and cells following RFC 4180 quoting: commas and line breaks inside quotes belong to the cell, and two quotes mean one quote.
     *
     * @param text the full contents of the CSV file
     * @return the rows in order, each a list of cells; blank lines come back as a row with one empty cell
     * @throws IOException if a quoted cell is never closed
     */
    private static List<List<String>> parse(String text) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                    else quoted = false;
                } else cell.append(c);
            } else if (c == '"' && cell.length() == 0) quoted = true;
            else if (c == ',') { row.add(cell.toString()); cell.setLength(0); }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString()); cell.setLength(0);
                if (!row.isEmpty()) rows.add(row); row = new ArrayList<>();
            } else cell.append(c);
        }
        if (quoted) throw new IOException("Unclosed quoted CSV field");
        if (!row.isEmpty() || cell.length() > 0) { row.add(cell.toString()); rows.add(row); }
        return rows;
    }
}
