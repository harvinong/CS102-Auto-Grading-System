package autogradesys.io;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Data Access Object for the course scoresheet (e.g. IS442-ScoreSheet.csv).
 *
 * This is the one class that reads and writes the scoresheet file. The file looks like this:
 *
 *   OrgDefinedId,Username,Last Name,First Name,Email,Calculated Final Grade Numerator,Calculated Final Grade Denominator,End-of-Line Indicator
 *   #01400001,#ping.lee.2023,_,PING LEE,ping.lee.2023@computing.smu.edu.sg,,20,#
 *
 * After marks are saved, one column per question sits between "Calculated Final Grade Denominator"
 * and "End-of-Line Indicator", and the total goes in "Calculated Final Grade Numerator":
 *
 *   OrgDefinedId,Username,...,Calculated Final Grade Numerator,Calculated Final Grade Denominator,Q1,Q2,Q3,End-of-Line Indicator
 *   #01400001,#ping.lee.2023,...,20,20,6,10,4,#
 *
 * It works like a small database:
 *   - load() reads every student row into a LinkedHashMap of ScoresheetRecord, keyed by username
 *     (without the "#"), in the same order as the scoresheet.
 *   - get(username) returns a student's record   (Read).
 *   - set(username, record) replaces it           (Update).
 *   - save() writes every record back to the same file; saveAs(file) writes them to a new file.
 *   Every other cell (OrgDefinedId, names, email, ...) is kept exactly as it was.
 *
 * How to use it:
 *   ScoresheetDAO dao = new ScoresheetDAO(scoresheetFile);
 *   dao.load();
 *   ScoresheetRecord ping = dao.get("ping.lee.2023");
 *   ping.marks().put("Q1", 6);
 *   dao.set("ping.lee.2023", ping);
 *   dao.saveAs(new File("IS442-ScoreSheet_2026-11-11_11-04-00.csv"));
 */
public class ScoresheetDAO {
    // Constants: the column names of the course scoresheet
    // The student's username, e.g. #ping.lee.2023
    public static final String USERNAME_COLUMN = "Username";
    // Another name for the username column, used by simple sheets such as "student,Q1"
    public static final String STUDENT_COLUMN = "student";
    // Where the student's total marks go
    public static final String TOTAL_COLUMN = "Calculated Final Grade Numerator";
    // The question columns go after this column...
    public static final String DENOMINATOR_COLUMN = "Calculated Final Grade Denominator";
    // ...and before this one, which must stay last
    public static final String END_COLUMN = "End-of-Line Indicator";

    // The CSV file this DAO reads from and saves to.
    private final File scoresheetFile;
    // The scoresheet's own columns (everything except the question columns), in file order.
    private ArrayList<String> columns = new ArrayList<>();
    // The question columns found in the file (e.g. Q1, Q2, Q3), in file order.
    private ArrayList<String> questions = new ArrayList<>();
    // Each student's cells for the scoresheet's own columns, keyed by username, in scoresheet order.
    private LinkedHashMap<String, ArrayList<String>> cells = new LinkedHashMap<>();
    // One record (the marks) per student, keyed by username, in scoresheet order.
    private LinkedHashMap<String, ScoresheetRecord> records = new LinkedHashMap<>();
    // The line ending the file uses (\r\n on Windows, \n on Mac), so saved files look the same.
    private String newLine = "\n";
    // True once load() has succeeded, so save() never writes an empty sheet over a real one.
    private boolean loaded = false;

    // Input: the scoresheet CSV file to read from and save to.
    // Output: a new ScoresheetDAO.
    /**
     * Opens the configured CSV scoresheet for loading, lookup, and updates.
     * @param scoresheetFile the argument supplied to this operation.
     */
    public ScoresheetDAO(File scoresheetFile) {
        this.scoresheetFile = scoresheetFile;
    }

    /**
     * reads the whole CSV. Every student row becomes a ScoresheetRecord (its marks in the
     * question columns) stored under its username, and the row's other cells are kept so they
     * can be written back unchanged. An empty mark cell means "not graded" and is left out.
     * 
     *         Username (or student) column, repeats a column, has a row longer than the header, lists a student
     *         twice, or has a mark that is not a whole number. On false, whatever was loaded before is kept.
     * Loads the persisted configuration or scoresheet data into this object.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public boolean load() {
        if (scoresheetFile == null) {
            return false;
        }
        // Every row of the file, including the header
        ArrayList<ArrayList<String>> rows;
        String text;
        try {
            text = Files.readString(scoresheetFile.toPath(), StandardCharsets.UTF_8);
            rows = parse(text);
        } catch (IOException e) {
            return false; // Missing, unreadable or badly quoted
        }
        if (rows.isEmpty()) {
            return false; // No header row
        }

        // The first row is the header, which lists the column names
        ArrayList<String> header = new ArrayList<>();
        for (String name : rows.get(0)) {
            header.add(name.trim());
        }
        // A repeated column name would make it unclear where a value belongs
        for (int i = 0; i < header.size(); i++) {
            if (columnOf(header, header.get(i)) != i) {
                return false;
            }
        }
        int usernameColumn = columnOf(header, USERNAME_COLUMN);
        // A simple sheet may call it "student" instead (the same fallback ScoresheetReader uses)
        if (usernameColumn < 0) {
            usernameColumn = columnOf(header, STUDENT_COLUMN);
        }
        if (usernameColumn < 0) {
            return false;
        }

        // The question columns are the ones between the denominator and the end-of-line columns
        int firstQuestion = columnOf(header, DENOMINATOR_COLUMN) + 1; // 0 when there is no denominator column
        int endColumn = columnOf(header, END_COLUMN);
        int afterLastQuestion = endColumn < 0 ? header.size() : endColumn;
        if (firstQuestion == 0) {
            afterLastQuestion = 0; // No denominator column: the sheet has no question columns yet
        }

        ArrayList<String> newColumns = new ArrayList<>();
        ArrayList<String> newQuestions = new ArrayList<>();
        for (int i = 0; i < header.size(); i++) {
            if (i >= firstQuestion && i < afterLastQuestion) {
                newQuestions.add(header.get(i));
            } else {
                newColumns.add(header.get(i));
            }
        }

        // Build new maps of cells and records, keyed by username, in the same order as the file
        LinkedHashMap<String, ArrayList<String>> newCells = new LinkedHashMap<>();
        LinkedHashMap<String, ScoresheetRecord> newRecords = new LinkedHashMap<>();
        for (int r = 1; r < rows.size(); r++) {
            ArrayList<String> row = rows.get(r);
            // Any row longer than the header is invalid
            if (row.size() > header.size()) {
                return false;
            }
            // Short rows are padded with empty cells
            while (row.size() < header.size()) {
                row.add("");
            }
            // The username without the "#" the course sheet puts in front; skip rows with none
            String username = row.get(usernameColumn).replace("#", "").trim();
            if (username.isEmpty()) {
                continue;
            }
            // The same student twice would make get() and set() ambiguous
            if (newRecords.containsKey(username)) {
                return false;
            }

            ArrayList<String> ownCells = new ArrayList<>();
            LinkedHashMap<String, Integer> marks = new LinkedHashMap<>();
            for (int i = 0; i < header.size(); i++) {
                String cell = row.get(i);
                if (i >= firstQuestion && i < afterLastQuestion) {
                    // A question cell: empty means not graded, otherwise it must be a whole number
                    if (!cell.trim().isEmpty()) {
                        try {
                            marks.put(header.get(i), Integer.parseInt(cell.trim()));
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    }
                } else {
                    ownCells.add(cell);
                }
            }
            newCells.put(username, ownCells);
            newRecords.put(username, new ScoresheetRecord(username, marks));
        }

        // Everything was read correctly, so replace what was loaded before
        columns = newColumns;
        questions = newQuestions;
        cells = newCells;
        records = newRecords;
        newLine = text.contains("\r\n") ? "\r\n" : "\n";
        loaded = true;
        return true;
    }

    /**
     * writes every record back to the scoresheet file it was loaded from. The file is first
     * written to a temporary file and then swapped in, so a failed save never leaves a broken file.
     * 
     *         (the original file is left as it was).
     * Persists the current configuration or scoresheet data to its configured file.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public boolean save() {
        if (!loaded || scoresheetFile == null) {
            return false; // Don't overwrite a real file with an empty one
        }
        Path target = scoresheetFile.toPath().toAbsolutePath();
        Path temporary = null;
        try {
            // Write everything to a temporary file in the same folder first.
            temporary = Files.createTempFile(target.getParent(), scoresheetFile.getName() + ".", ".tmp");
            Files.writeString(temporary, toText(), StandardCharsets.UTF_8);
            // Then replace the real scoresheet with the finished temporary file in one step.
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            // Something went wrong (for example the file is read-only); clean up the temporary file.
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Nothing more we can do; the original scoresheet is still untouched.
                }
            }
            return false;
        }
    }

    // Input: the new file to write, e.g. IS442-ScoreSheet_2026-11-11_11-04-00.csv
    // Function: writes every record to that file, leaving the original scoresheet unchanged.
    //           It never overwrites an existing file.
    // Output: true if the file was written; false if nothing has been loaded, the file already
    //         exists, or the write failed.
    /**
     * Save as.
     * @param newFile the argument supplied to this operation.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public boolean saveAs(File newFile) {
        if (!loaded || newFile == null) {
            return false;
        }
        try {
            // CREATE_NEW means: fail instead of overwriting an existing file
            Files.writeString(newFile.toPath(), toText(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // Input: a student's username, e.g. "ping.lee.2023" (a leading "#" is ignored).
    // Function: looks the student up in the map (Read).
    // Output: that student's record, or null if the username is not in the scoresheet.
    /**
     * Returns the scoresheet record for the requested username, or null when absent.
     * @param username the student username used for lookup.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public ScoresheetRecord get(String username) {
        if (username == null) {
            return null;
        }
        return records.get(username.replace("#", "").trim());
    }

    // Input: a student's username (the key) and the new record for that student.
    // Function: replaces the student's record in the map (Update). Only memory changes; call save()
    //           or saveAs() to write it to a file. A username that is not in the scoresheet is not
    //           added, because the DAO only reads and updates (no create or delete).
    // Output: true if the record was replaced; false if the username is not in the scoresheet or the record is null.
    /**
     * Stores or replaces the scoresheet record for the supplied username.
     * @param username the student username used for lookup.
     * @param record the diagnostic or scoresheet record to store.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public boolean set(String username, ScoresheetRecord record) {
        if (username == null || record == null) {
            return false;
        }
        // Only students already in the scoresheet can be updated.
        String key = username.replace("#", "").trim();
        if (!records.containsKey(key)) {
            return false;
        }
        records.put(key, record);
        return true;
    }

    // Input: nothing.
    // Function: lists every student's username, so a caller can go through all the records with get().
    // Output: the usernames in scoresheet order (empty before load()).
    /**
     * Returns the usernames currently represented in the loaded scoresheet.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public ArrayList<String> getUsernames() {
        return new ArrayList<>(records.keySet());
    }

    // Input: nothing.
    // Function: gives back the file this DAO reads and saves to.
    // Output: the scoresheet file.
    /**
     * Returns the CSV file used by this scoresheet data access object.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public File getScoresheetFile() {
        return scoresheetFile;
    }

    // Input: nothing (uses the loaded columns and records).
    // Function: builds the whole CSV text. The question columns are the loaded ones plus any new ones
    //           in the records, sorted (Q1, Q2, Q3), placed just before "End-of-Line Indicator".
    //           A student with marks gets their total in "Calculated Final Grade Numerator";
    //           a student with no marks keeps empty question cells and their original total cell.
    // Output: the CSV text, ready to write to a file.
    /**
     * To text.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    private String toText() {
        // Start from the loaded question columns and add new ones from the records
        ArrayList<String> allQuestions = new ArrayList<>(questions);
        for (ScoresheetRecord record : records.values()) {
            for (String question : record.marks().keySet()) {
                if (!allQuestions.contains(question)) {
                    allQuestions.add(question);
                }
            }
        }
        Collections.sort(allQuestions); // Sort alphabetically

        // The question columns go just before "End-of-Line Indicator", or at the end if there is none
        int insertAt = columnOf(columns, END_COLUMN);
        if (insertAt < 0) {
            insertAt = columns.size();
        }
        int totalColumn = columnOf(columns, TOTAL_COLUMN);

        // Header row
        ArrayList<String> header = new ArrayList<>(columns);
        header.addAll(insertAt, allQuestions);
        String text = joinLine(header) + newLine;

        // One row per student
        for (String username : records.keySet()) {
            ScoresheetRecord record = records.get(username);
            ArrayList<String> row = new ArrayList<>(cells.get(username));
            // Only students with marks get a total; the others keep what the sheet had
            if (!record.marks().isEmpty() && totalColumn >= 0) {
                row.set(totalColumn, Integer.toString(record.finalScore()));
            }
            // One cell per question: the mark, or empty if the student has none for it
            ArrayList<String> markCells = new ArrayList<>();
            for (String question : allQuestions) {
                Integer mark = record.marks().get(question);
                markCells.add(mark == null ? "" : Integer.toString(mark));
            }
            row.addAll(insertAt, markCells);
            text += joinLine(row) + newLine;
        }
        return text;
    }

    // Input: the column names and the column to find.
    // Function: looks for the column, ignoring spaces and upper/lower case.
    // Output: the column's position, or -1 if it is not there.
    /**
     * Returns the index of a named CSV column, or the absent-column sentinel.
     * @param columns the argument supplied to this operation.
     * @param name the name used for lookup or display.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    private static int columnOf(List<String> columns, String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).trim().equalsIgnoreCase(name.trim())) {
                return i;
            }
        }
        return -1;
    }

    // Input: the cells of one row
    // Function: joins them with commas, quoting a cell only when it holds a comma, a quote or a line
    //           break, so the rest of the sheet keeps its original look
    // Output: one CSV line, e.g. #01400001,#ping.lee.2023,_,PING LEE
    /**
     * Join line.
     * @param row the argument supplied to this operation.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    private static String joinLine(List<String> row) {
        String line = "";
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) {
                line += ",";
            }
            String value = row.get(i);
            if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
                value = "\"" + value.replace("\"", "\"\"") + "\"";
            }
            line += value;
        }
        return line;
    }

    // Input: the full text of a CSV file.
    // Function: splits the text into rows and cells, one character at a time. Inside quotes, commas
    //           and line breaks are part of the cell, and "" means one quote character.
    //           Blank lines are skipped. Accepts both Windows (\r\n) and Unix (\n) line endings.
    // Output: a list of rows, each a list of cells.
    // Throws IOException if a quoted cell is never closed.
    /**
     * Parses CSV text into rows while preserving quoted fields and line structure.
     * @param text the text to parse, render, or escape.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     * @throws IOException when the operation cannot complete.
     */
    private static ArrayList<ArrayList<String>> parse(String text) throws IOException {
        // All finished rows.
        ArrayList<ArrayList<String>> result = new ArrayList<>();
        // The row being built right now.
        ArrayList<String> row = new ArrayList<>();
        // The cell being built right now.
        String cell = "";
        // True while we are between an opening and a closing quote.
        boolean inQuotes = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    // Two quotes in a row inside quotes stand for one real quote.
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell += '"';
                        // Skip the second quote.
                        i++;
                    } else {
                        // A single quote closes the quoted part.
                        inQuotes = false;
                    }
                } else {
                    // Anything else inside quotes, even a comma or line break, belongs to the cell.
                    cell += c;
                }
            } else if (c == '"' && cell.isEmpty()) {
                // A quote at the very start of a cell opens a quoted cell.
                inQuotes = true;
            } else if (c == ',') {
                // A comma ends the current cell.
                row.add(cell);
                cell = "";
            } else if (c == '\n' || c == '\r') {
                // Treat \r\n as one line break by skipping the \n after a \r.
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                // A line break ends the cell and the row.
                row.add(cell);
                cell = "";
                addRowIfNotBlank(result, row);
                row = new ArrayList<>();
            } else {
                // An ordinary character.
                cell += c;
            }
        }

        // A quote was opened and never closed, so the file is broken.
        if (inQuotes) {
            throw new IOException("Unclosed quoted CSV field");
        }
        // The last line may not end with a line break; keep it if it has anything in it.
        if (!row.isEmpty() || !cell.isEmpty()) {
            row.add(cell);
            addRowIfNotBlank(result, row);
        }
        return result;
    }

    // Input: the list of finished rows and one new row.
    // Function: adds the row unless it is a blank line (a single empty cell).
    // Output: nothing; "result" is changed directly.
    /**
     * Adds a parsed row when it contains at least one non-blank value.
     * @param result the completed grading result to export.
     * @param row the argument supplied to this operation.
     */
    private static void addRowIfNotBlank(ArrayList<ArrayList<String>> result, ArrayList<String> row) {
        boolean blank = row.size() == 1 && row.get(0).trim().isEmpty();
        if (!blank) {
            result.add(row);
        }
    }
}
