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
    private ScoresheetReader() { }

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

    private static int columnOf(List<String> header, String title) {
        for (int i = 0; i < header.size(); i++) if (header.get(i).trim().equalsIgnoreCase(title)) return i;
        return -1;
    }
    private static String cell(List<String> row, int column) {
        return column < 0 || column >= row.size() ? "" : row.get(column).trim();
    }
    /** Small RFC-4180-compatible parser, shared in spirit with grading.CsvScoresheet. */
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
