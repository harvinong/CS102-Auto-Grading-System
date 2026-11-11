package autogradesys.io;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Data Access Object for the CSV scoresheet.
 *
 * This is the one class that reads and writes the scoresheet file. The file uses the same format as ScoreRecorder:
 *
 *   username,"Q1a","Q1b",final_score
 *   "chee.teo.2022",3,2,5
 *   "jing.lim.2021",1,0,1
 *   "% full marks",33.3%,0.0%,
 *
 * It works like a small database:
 *   - load() reads every student row into a LinkedHashMap of ScoresheetRecord, keyed by username,
 *     in the same order as the scoresheet.
 *   - get(username) returns a student's record   (Read).
 *   - set(username, record) replaces it           (Update).
 *   - save() writes every record back to the CSV in the same format.
 *
 * How to use it:
 *   ScoresheetDAO dao = new ScoresheetDAO(scoresheetFile);
 *   dao.load();
 *   ScoresheetRecord chee = dao.get("chee.teo.2022");
 *   chee.marks().put("Q1a", 3);
 *   dao.set("chee.teo.2022", chee);
 *   dao.save();
 */

public class ScoresheetDAO {
    // Constants
    // First column: the student's username.
    public static final String USERNAME_COLUMN = "username";
    // Last column: the total of all the sub-question marks.
    public static final String FINAL_SCORE_COLUMN = "final_score";
    // The summary row at the bottom; it is not a student.
    public static final String FULL_MARKS_ROW = "% full marks";
    // ScoreRecorder ends every line with \r\n, so we do the same.
    private static final String NEW_LINE = "\r\n";

    // The CSV file this DAO reads and writes.
    private final File scoresheetFile;
    // The sub-question columns (everything between username and final_score), in file order.
    private ArrayList<String> subQuestions = new ArrayList<>();
    // One record per student, keyed by username, in scoresheet order.
    private LinkedHashMap<String, ScoresheetRecord> records = new LinkedHashMap<>();
    // The "% full marks" row: sub-question -> cell text (e.g. "Q1a" -> "33.3%"). null if the file has none.
    private LinkedHashMap<String, String> fullMarksRow = null;
    // True once load() has succeeded, so save() never writes an empty sheet over a real one.
    private boolean loaded = false;

    // Input: the scoresheet CSV file to read from and write to.
    // Output: a new ScoresheetDAO.
    /**
     * Opens the configured CSV scoresheet for loading, lookup, and updates.
     */
    public ScoresheetDAO(File scoresheetFile) {
        this.scoresheetFile = scoresheetFile;
    }

    // Input: nothing
    // Function: reads the whole CSV and turns every student row into a ScoresheetRecord stored in the
    //           map under its username. The "% full marks" row is kept aside so save() can write it back.
    //           An empty mark cell counts as 0, the same as ScoreRecorder.
    // Output: true if the file was read; false if it is missing, unreadable, badly quoted, has no
    //         username column, repeats a column, has a row longer than the header, lists a student
    //         twice, or has a mark that is not a whole number. On false, whatever was loaded before is kept.

    /**
     * Loads the persisted configuration or scoresheet data into this object.
     */
    public boolean load() {
        // return false if invalid
        if (scoresheetFile == null || !scoresheetFile.isFile()){
            return false;
        } 

        // Every row of the file, including header
        ArrayList<ArrayList<String>> allRows;
    
        // Read the file into allRows, or return false if it cannot be read
        try{
            // Read the whole file as text, then split into rows of cells
            String text = Files.readString(scoresheetFile.toPath(), StandardCharsets.UTF_8);
            allRows = parse(text);
        } catch (IOException e) {
            return false;
        }

        if (allRows.isEmpty()) {
            return false; // No header row
        }

        // The first row is the header, which lists the column names
        ArrayList<String> header = new ArrayList<>();
        for (String name: allRows.get(0)) {
            header.add(name.trim());
        }

        // Check  for duplicate columns
        ArrayList<String> seen = new ArrayList<>();
        for (String name: header) {
            String lowerCase = name.toLowerCase();
            if (seen.contains(lowerCase)) {
                return false; // Duplicate column
            }
            seen.add(lowerCase);
        }

        // Find username column
        int usernameColumn = columnOf(header, USERNAME_COLUMN);
        if (usernameColumn < 0 ){
            return false; 
        }

        // Find final_score column
        int finalScoreColumn = columnOf(header, FINAL_SCORE_COLUMN);

        // The sub-question columns are everything between username and final_score
        ArrayList<String> newSubQuestions  = new ArrayList<>();
        for (int column = 0; column < header.size(); column++) {
            if (column != usernameColumn && column != finalScoreColumn) {
                newSubQuestions.add(header.get(column));
            }
        }

        // Build a new map of records, keyed by username, in the same order as the file
        LinkedHashMap<String, ScoresheetRecord> newRecords = new LinkedHashMap<>();
        LinkedHashMap<String, String> newFullMarksRow = null;

        for (int i = 1; i < allRows.size(); i++){
            ArrayList<String> row = allRows.get(i);
            // any row longer than the header is invalid
            if (row.size() > header.size()) {
                return false;
            }

            // fill missing cells with empty string
            while (row.size() < header.size()) {
                row.add("");
            }

            // If username is empty skip it
            String username = row.get(usernameColumn).trim();
            if (username.isEmpty()) {
                continue;
            }

            // summary row
            if (username.equalsIgnoreCase(FULL_MARKS_ROW)){
                newFullMarksRow = new LinkedHashMap<>();
                // Copy the sub-question cells into the new full marks row
                for (String subQuestion: newSubQuestions){
                    newFullMarksRow.put(subQuestion, row.get(header.indexOf(subQuestion)).trim());
                }
                continue; // Skip the rest of the loop, don't add a record for this row
            }

            // If the username is already in the map, the file is invalid
            if (newRecords.containsKey(username)) {
                return false;
            }

            // Read each sub-qestion mark as a whole number
            LinkedHashMap<String, Integer> marks = new LinkedHashMap<>();
            for (String subQuestion: newSubQuestions){
                String cell = row.get(header.indexOf(subQuestion)).trim();
                if (cell.isEmpty()) {
                    marks.put(subQuestion, 0);
                } else {
                    try {
                        int mark = Integer.parseInt(cell);
                        marks.put(subQuestion, mark);
                    } catch (NumberFormatException e) {
                        return false; // Not a whole number
                    }
                }
            }
            // Create a new record and add it to the map
            newRecords.put(username, new ScoresheetRecord(username, marks));
        }

        // If we got here, the file is valid. Save the new data.
        subQuestions = newSubQuestions;
        records = newRecords;
        fullMarksRow = newFullMarksRow;
        loaded = true;
        return true;
    }
    // Input: nothing (uses the records in memory).
    // Function: writes the scoresheet back in ScoreRecorder's format:
    //           header (username, sorted sub-questions, final_score), one row per student in the
    //           map's order with final_score added up again, then the "% full marks" row if there was one.
    //           A sub-question that a record now has but the file did not becomes a new column.
    //           It writes a temporary file first and then swaps it in, so a crash halfway cannot
    //           leave a broken scoresheet.
    // Output: true if the file was written; false if nothing has been loaded or the write failed
    //         (the original file is left as it was).

    /**
     * Persists the current configuration or scoresheet data to its configured file.
     */
    public boolean save() {
        if (!loaded ||  scoresheetFile == null) {
            return false; // Don't overwrite a real file with an empty one
        }

        // Start form loaded subquestions and add new ones from the records
        ArrayList<String> columns = new ArrayList<>(subQuestions);
        for (ScoresheetRecord record: records.values()) {
            for (String subQuestion: record.marks().keySet()) {
                if (!columns.contains(subQuestion)) {
                    columns.add(subQuestion);
                }
            }
        }

        // Sort the sub-question columns alphabetically
        Collections.sort(columns);

        // Header row
        String text = USERNAME_COLUMN;
        for (String subQuestion: columns) {
            text += "," + quote(subQuestion);
        }
        text += "," + FINAL_SCORE_COLUMN + NEW_LINE;
        
        // One row per student
        for (ScoresheetRecord record: records.values()) {
            text += quote(record.username());
            int total = 0;
            for (String subQuestion: columns) {
                int mark = record.marks().getOrDefault(subQuestion, 0);
                total += mark;
                text += "," + mark;
            }
            text += "," + total + NEW_LINE;
        }

        // The "% full marks" row, if there was one
        if (fullMarksRow != null) {
            text += quote(FULL_MARKS_ROW);
            for (String subQuestion: columns) {
                String cell = fullMarksRow.getOrDefault(subQuestion, "");
                text += "," + cell;
            }
            text += "," + NEW_LINE; // Leave the final_score cell empty
        }

        // Full path of the temporary file to write to first, in the same directory as the real file
        Path target = scoresheetFile.toPath().toAbsolutePath();
        Path folder = target.getParent();
        // The temporary file, created below.
        Path temporary = null;
        try {
            // Write everything to a temporary file in the same folder first.
            temporary = Files.createTempFile(folder, scoresheetFile.getName() + ".", ".tmp");
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            // Then replace the real scoresheet with the finished temporary file in one step.
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            // The new columns are now part of the file, so remember them.
            subQuestions = columns;
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
    
    // Input: a student's username, e.g. "chee.teo.2022".
    // Function: looks the student up in the map (Read).
    // Output: that student's record, or null if the username is not in the scoresheet.
    /**
     * Returns the scoresheet record for the requested username, or null when absent.
     */
    public ScoresheetRecord get(String username) {
        if (username == null) {
            return null;
        }
        return records.get(username.trim());
    }

    // Input: a student's username (the key) and the new record for that student.
    // Function: replaces the student's record in the map (Update). Only memory changes; call save()
    //           to write it to the file. A username that is not in the scoresheet is not added,
    //           because the DAO only reads and updates (no create or delete).
    // Output: true if the record was replaced; false if the username is not in the scoresheet or the record is null.
    /**
     * Stores or replaces the scoresheet record for the supplied username.
     */
    public boolean set(String username, ScoresheetRecord record) {
        if (username == null || record == null) {
            return false;
        }
        // Only students already in the scoresheet can be updated.
        String key = username.trim();
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
     */
    public ArrayList<String> getUsernames() {
        return new ArrayList<>(records.keySet());
    }
 
    // Input: nothing.
    // Function: gives back the file this DAO reads and writes.
    // Output: the scoresheet file.
    /**
     * Returns the CSV file used by this scoresheet data access object.
     */
    public File getScoresheetFile() {
        return scoresheetFile;
    }

    // Input: the column names and the column to find.
    // Function: looks for the column, ignoring spaces and upper/lower case.
    // Output: the column's position, or -1 if it is not there.
    /**
     * Returns the index of a named CSV column, or the absent-column sentinel.
     */
    private static int columnOf(List<String> columns, String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).trim().equalsIgnoreCase(name.trim())) {
                return i;
            }
        }
        return -1;
    }
 
    // Input: A text value to put in the CSV, e.g. a username or sub-question name.
    // Function: Wraps the value in double quotes and doubles any quotes inside it, so commas cannot
    //           break the CSV. Same as ScoreRecorder.quote().
    // Output: The quoted value, e.g. chee"teo becomes "chee""teo".
    /**
     * Escapes a value for safe inclusion in a CSV field.
     */
    private static String quote(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
 
    // Input: the full text of a CSV file.
    // Function: splits the text into rows and cells, one character at a time. Inside quotes, commas
    //           and line breaks are part of the cell, and "" means one quote character.
    //           Blank lines are skipped. Accepts both Windows (\r\n) and Unix (\n) line endings.
    // Output: a list of rows, each a list of cells.
    // Throws IOException if a quoted cell is never closed.
    /**
     * Parses CSV text into rows while preserving quoted fields and line structure.
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
     */
    private static void addRowIfNotBlank(ArrayList<ArrayList<String>> result, ArrayList<String> row) {
        boolean blank = row.size() == 1 && row.get(0).trim().isEmpty();
        if (!blank) {
            result.add(row);
        }
    }
}

