package autogradesys.grading;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads and atomically updates a CSV scoresheet while preserving existing rows.
 *
 * <p>Grading flow: JavaGrader first calls {@link #isValid} during setup, grades everything, then
 * calls {@link #update} (through ScoreRecorder) once to write each student's marks into the matching student row and question
 * column. The new file is written to a temp file and swapped in, so a crash cannot leave a
 * half-written scoresheet.</p>
 *
 * <p>Only known student score cells are changed; unrelated columns and rows
 * remain part of the rendered file.</p>
 *
 * <p>Two layouts are accepted. The team's layout has a {@code student} column and one column per
 * question. The instructors' eLearn export (e.g. {@code IS442-ScoreSheet.csv}) has a {@code Username}
 * column with values like {@code #ping.lee.2023}; each student's total goes into its
 * {@code Calculated Final Grade Numerator} column, and per-question columns are filled if present.</p>
 */
public final class CsvScoresheet {
    // Column that holds the username in the team's layout.
    private static final String STUDENT_COLUMN = "student";
    // Column that holds the username in the instructors' eLearn export.
    private static final String USERNAME_COLUMN = "Username";
    // Column that receives each student's total in the instructors' eLearn export.
    private static final String NUMERATOR_COLUMN = "Calculated Final Grade Numerator";

    // Static utility class; no instances.
    private CsvScoresheet() {
    }

    /** Checks, without modifying the file, that the CSV parses and has a usable unique header with a student column. */
    public static boolean isValid(Path path) {
        // Reading or parsing can throw, and any failure simply means "not a valid scoresheet" for this yes/no check.
        try {
            // Read the whole file as UTF-8 and split it into rows of cells with the hand-written CSV parser below.
            List<List<String>> rows = parse(Files.readString(path.toAbsolutePath().normalize(), StandardCharsets.UTF_8));
            // An empty file or a blank first row has no header, so grading could not tell which column is which.
            if (rows.isEmpty() || rows.get(0).isEmpty()
                    || rows.get(0).stream().allMatch(String::isBlank)) {
                // Reject a scoresheet without a usable header.
                return false;
            }
            // The first row names the columns: "student" plus one column per question.
            List<String> header = rows.get(0);
            // Scores are matched to rows by the "student" or "Username" column, so one must exist.
            if (studentColumn(header) < 0) {
                // Without it no row can be tied to a student.
                return false;
            }
            // Tracks column names already seen, to spot duplicates.
            Set<String> headers = new HashSet<>();
            // Set.add returns false for a repeated name, so this finds duplicate columns, which would make "which q1 column?" ambiguous.
            if (header.stream().anyMatch(column -> !headers.add(column))) {
                // Duplicate headers are rejected.
                return false;
            }
            // Data rows may be short (padded later) but must not have more cells than the header.
            return rows.stream().skip(1).allMatch(row -> row.size() <= header.size());
        // Unreadable file or broken CSV quoting.
        } catch (IOException | IllegalArgumentException exception) {
            // Treat any read or parse failure as invalid; JavaGrader.validSetup then reports the fatal error.
            return false;
        }
    }

    /**
     * Creates a current-format scoresheet and writes the supplied student scores.
     *
     * <p>Not part of the menu grading flow (that updates an existing sheet with {@link #update});
     * ScoreRecorder.recordScores(Map) calls it to write a brand new timestamped CSV.</p>
     */
    public static void create(Path path, Map<String, Map<String, Integer>> scores) throws IOException {
        // Ordered set of question names seen across all students; these become the question columns, in first-seen order.
        Set<String> questions = new LinkedHashSet<>();
        // Merge every student's question names, since different students may have been graded on different questions.
        for (Map<String, Integer> student : scores.values()) questions.addAll(student.keySet());
        // Header row: the fixed student column first, then one column per question.
        List<String> header = new ArrayList<>();
        header.add("student");
        header.addAll(questions);
        // Table of rows to render, starting with the header.
        List<List<String>> rows = new ArrayList<>();
        rows.add(header);
        // One data row per student: their name, then a mark for every question column.
        for (Map.Entry<String, Map<String, Integer>> entry : scores.entrySet()) {
            // The row starts with the student's name, which fills the student column.
            List<String> row = new ArrayList<>(); row.add(entry.getKey());
            // A question this student has no mark for is written as 0 so every cell holds a number.
            for (String question : questions) row.add(String.valueOf(entry.getValue().getOrDefault(question, 0)));
            rows.add(row);
        }
        // CREATE_NEW refuses to overwrite an existing file, so a new timestamped sheet can never replace an old one.
        Files.writeString(path, render(rows, "\n"), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    // Called at the end of grading (JavaGrader.grade -> ScoreRecorder.recordScores); returns true only if the file was replaced successfully.
    /**
     * Applies the supplied scores and replaces the file only after rendering succeeds.
     *
     * @param path scoresheet to read and update
     * @param scores scores keyed by student and question
     * @param reporter destination for recoverable and fatal diagnostics
     * @return {@code true} when the updated scoresheet is safely written
     */
    public static boolean update(Path path, Map<String, Map<String, Integer>> scores,
            ErrorReporter reporter) {
        // Resolve once and reuse, so messages and file operations name the same location.
        Path absolute = path.toAbsolutePath().normalize();
        // Parsed contents of the existing sheet, assigned inside the try block.
        final List<List<String>> rows;
        // Line ending to write back, so a Windows (\r\n) sheet from eLearn keeps its line endings.
        final String newline;
        try {
            // Load the current file into memory before changing anything.
            String content = Files.readString(absolute, StandardCharsets.UTF_8);
            newline = content.contains("\r\n") ? "\r\n" : "\n";
            rows = parse(content);
        // The file vanished or has invalid CSV quoting since the earlier validation.
        } catch (IOException | IllegalArgumentException exception) {
            // Fatal: scores cannot be merged into a file we cannot read.
            reporter.fatal("scoresheet", "scoresheet-read",
                    "Could not read scoresheet " + absolute + ": " + exception.getMessage(),
                    "Check that the CSV exists, is readable, and uses valid CSV quoting.");
            // Nothing was written, so the original is untouched.
            return false;
        }
        // Repeat the header sanity check, because update() can be called without isValid().
        if (rows.isEmpty() || rows.get(0).isEmpty()
                || rows.get(0).stream().allMatch(String::isBlank)) {
            // Fatal: with no header the columns are unknown.
            reporter.fatal("scoresheet", "scoresheet-header",
                    "Scoresheet has no usable header row.",
                    "Add a header row beginning with student and one column per question.");
            // Abort without modifying the file.
            return false;
        }

        // Copy the header into a mutable list so it can be extended without altering the parsed rows.
        List<String> header = new ArrayList<>(rows.get(0));
        // Position of the student-name column, used to look up each row's owner.
        int studentColumn = studentColumn(header);
        // Position of the eLearn total column, or -1 when the sheet has none.
        int numeratorColumn = columnIgnoringCase(header, NUMERATOR_COLUMN);
        // -1 means neither a student nor a Username column exists.
        if (studentColumn < 0) {
            // Fatal: rows cannot be matched to students.
            reporter.fatal("scoresheet", "scoresheet-header",
                    "Scoresheet must have a student or Username column.",
                    "Add a header named student (or use the eLearn export with its Username column) before grading.");
            // Abort and leave the file as it was.
            return false;
        }
        // Used to detect repeated column names.
        Set<String> uniqueHeaders = new HashSet<>();
        // Check each column name once.
        for (String column : header) {
            // add() is false when the name was already present.
            if (!uniqueHeaders.add(column)) {
                // Fatal: an ambiguous header could cause a score to land in the wrong column.
                reporter.fatal("scoresheet", "scoresheet-header",
                        "Scoresheet contains duplicate column: " + column,
                        "Keep one unique column for each student field and question.");
                // Abort without writing.
                return false;
            }
        }

        // Output table built in memory; the file is only touched once it is complete.
        List<List<String>> writableRows = new ArrayList<>();
        // The header goes first, as in the source file.
        writableRows.add(header);
        // Students found in the sheet, used afterwards to find graded students that have no row.
        Set<String> seenStudents = new LinkedHashSet<>();
        // Go through data rows only; index 0 was the header.
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            // Mutable copy of this row, so cells can be overwritten or padded.
            List<String> row = new ArrayList<>(rows.get(rowIndex));
            // More cells than columns means the row is damaged (often an unquoted comma).
            if (row.size() > header.size()) {
                // Fatal rather than guessing which cell belongs where; rowIndex + 1 gives the human line number.
                reporter.fatal("scoresheet", "scoresheet-row",
                        "Scoresheet row " + (rowIndex + 1) + " has more values than its header.",
                        "Fix the CSV row before grading; the original file was left unchanged.");
                // Abort so the original file is left unchanged.
                return false;
            }
            // Short rows are normal (blank trailing cells are often dropped).
            while (row.size() < header.size()) {
                // Pad with empty cells so every row lines up with the header.
                row.add("");
            }
            // The student name that identifies whose scores belong in this row; eLearn writes it as #ping.lee.2023.
            String student = studentKey(row.get(studentColumn));
            // A row with no student name cannot receive scores.
            if (student.isBlank()) {
                // Warn but carry on: the rest of the sheet is fine.
                reporter.recoverable("scoresheet", "scoresheet-row",
                        "Scoresheet row " + (rowIndex + 1) + " has no student name.",
                        "Fill in the student column; unrelated rows and scores will be preserved.",
                        null, absolute.toString(), null);
                // Keep the nameless row exactly as it was so no teacher data is lost.
                writableRows.add(row);
                // Skip the score lookup for this row.
                continue;
            }
            // Note that this graded student has a row.
            seenStudents.add(student);
            // Scores computed for this student, or null if they were not graded in this session.
            Map<String, Integer> studentScores = scores.get(student);
            // Rows for students who were not graded are left untouched.
            if (studentScores != null) {
                // The eLearn export takes the student's total in its numerator column.
                if (numeratorColumn >= 0) {
                    int total = studentScores.values().stream().mapToInt(Integer::intValue).sum();
                    row.set(numeratorColumn, Integer.toString(total));
                }
                // One entry per question: key is the question name, value is the marks earned.
                for (Map.Entry<String, Integer> score : studentScores.entrySet()) {
                    // The question name must match a column header exactly.
                    int questionColumn = header.indexOf(score.getKey());
                    // A missing question column only matters when there is no total column to hold the marks.
                    if (questionColumn < 0 && numeratorColumn < 0) {
                        // Report it but keep going; the teacher can add the column and regrade.
                        reporter.recoverable("scoresheet", "scoresheet-update",
                                "Scoresheet is missing question column: " + score.getKey(),
                                "Add the question column before the next grading session; existing rows are safe.",
                                student, absolute.toString(), score.getKey());
                    } else if (questionColumn >= 0) {
                        // Write the marks into the matching column of this student's row.
                        row.set(questionColumn, Integer.toString(score.getValue()));
                    }
                }
            }
            // Add the updated row to the output table.
            writableRows.add(row);
        }

        // Compare every graded student with the students found in the sheet.
        for (String student : scores.keySet()) {
            // This graded student has no row in the CSV.
            if (!seenStudents.contains(student)) {
                // Report it so their scores are not silently lost; the file itself is not extended with new rows.
                reporter.recoverable("scoresheet", "scoresheet-row",
                        "No scoresheet row exists for student: " + student,
                        "Add a row for the student if their scores should be recorded.",
                        student, absolute.toString(), null);
            }
        }

        // Temp file next to the real one; declared outside the try so the error path can delete it.
        Path temporary = null;
        // Writing and renaming can fail with IOException, handled at the bottom.
        try {
            // Folder of the scoresheet; the temp file is created there so the final move stays on the same file system.
            Path parent = absolute.getParent();
            // A path with no parent folder cannot hold a sibling temp file.
            if (parent == null) {
                // Jump to the catch block below, which reports a fatal write error.
                throw new IOException("scoresheet has no parent directory");
            }
            // Make sure the folder exists; does nothing if it does.
            Files.createDirectories(parent);
            // Create a uniquely named file such as scores.csv.12345.tmp beside the original.
            temporary = Files.createTempFile(parent, absolute.getFileName().toString() + ".", ".tmp");
            // Write the full new CSV text to the temp file, so the original is untouched until the result is complete.
            Files.writeString(temporary, render(writableRows, newline), StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            // Swap the finished temp file over the original in one step, so readers never see a half-written sheet.
            try {
                // ATOMIC_MOVE makes the replacement all-or-nothing at the filesystem level.
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            // Some filesystems cannot do atomic moves.
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                // Fall back to an ordinary replace, which is still safe because the temp file is already complete.
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            // The scoresheet now contains the new scores.
            return true;
        // Any failure while preparing, writing or moving the file ends up here.
        } catch (IOException exception) {
            // Only clean up if the temp file was actually created.
            if (temporary != null) {
                // Deleting can fail too, and that must not hide the original problem.
                try {
                    // Remove the half-finished temp file so stray .tmp files do not pile up.
                    Files.deleteIfExists(temporary);
                // Cleanup failed; ignored on purpose.
                } catch (IOException ignored) {
                }
            }
            // Report that the CSV could not be updated; the original file still holds the old scores.
            reporter.fatal("scoresheet", "scoresheet-write",
                    "Could not safely update scoresheet " + absolute + ": " + exception.getMessage(),
                    "Check permissions and ensure the scoresheet is not read-only; the original was retained.");
            // Tell JavaGrader the scores were not saved.
            return false;
        }
    }

    // Index of the username column: "student" (team layout) or "Username" (eLearn export), or -1.
    private static int studentColumn(List<String> header) {
        int student = header.indexOf(STUDENT_COLUMN);
        return student >= 0 ? student : columnIgnoringCase(header, USERNAME_COLUMN);
    }

    // Index of the column whose trimmed name matches, ignoring case, or -1.
    private static int columnIgnoringCase(List<String> header, String name) {
        for (int index = 0; index < header.size(); index++) {
            if (header.get(index).trim().equalsIgnoreCase(name)) {
                return index;
            }
        }
        return -1;
    }

    // eLearn marks text cells with a leading '#', so "#ping.lee.2023" is the student ping.lee.2023.
    private static String studentKey(String cell) {
        String trimmed = cell.trim();
        return trimmed.startsWith("#") ? trimmed.substring(1).trim() : trimmed;
    }

    // Minimal RFC 4180 style parser: commas separate cells, quotes wrap cells containing commas or newlines, and "" is a literal quote.
    private static List<List<String>> parse(String content) throws IOException {
        // Completed rows.
        List<List<String>> rows = new ArrayList<>();
        // Cells of the row being read.
        List<String> row = new ArrayList<>();
        // Characters of the cell being read.
        StringBuilder field = new StringBuilder();
        // True while inside a "..." cell, where commas and newlines are ordinary text.
        boolean quoted = false;
        // True right after a closing quote, where only a comma or line break is legal.
        boolean afterQuote = false;
        // Single pass over the text, one character at a time (a small state machine).
        for (int index = 0; index < content.length(); index++) {
            // Character being processed.
            char character = content.charAt(index);
            // State 1: inside quotes.
            if (quoted) {
                // A quote is either an escaped quote or the end of the cell.
                if (character == '"') {
                    // Two quotes in a row mean one literal quote character.
                    if (index + 1 < content.length() && content.charAt(index + 1) == '"') {
                        // Keep a single quote in the cell value.
                        field.append('"');
                        // Skip the second quote of the pair.
                        index++;
                    } else {
                        // A lone quote closes the cell.
                        quoted = false;
                        // Next character must be a comma or newline.
                        afterQuote = true;
                    }
                } else {
                    // Any other character inside quotes, including commas and newlines, is cell text.
                    field.append(character);
                }
            // State 2: a quoted cell has just ended.
            } else if (afterQuote) {
                // Comma: this cell is complete.
                if (character == ',') {
                    // Store the finished cell.
                    row.add(field.toString());
                    // Empty the buffer for the next cell.
                    field.setLength(0);
                    // Back to normal reading.
                    afterQuote = false;
                // Line break: this cell and the whole row are complete.
                } else if (character == '\n' || character == '\r') {
                    // Store the last cell of the row.
                    row.add(field.toString());
                    // Reset for the next row.
                    field.setLength(0);
                    // Store the finished row.
                    rows.add(row);
                    // Start a new row.
                    row = new ArrayList<>();
                    // Back to normal reading.
                    afterQuote = false;
                    // Windows line endings are a carriage return plus a line feed, which must count as one break.
                    if (character == '\r' && index + 1 < content.length() && content.charAt(index + 1) == '\n') {
                        // Skip the line feed that follows the carriage return.
                        index++;
                    }
                } else {
                    // Something like "abc"x is malformed; stop rather than guess.
                    throw new IOException("characters found after a quoted CSV field");
                }
            // State 3: a quote at the very start of a cell opens a quoted cell.
            } else if (character == '"' && field.length() == 0) {
                // Enter quote mode.
                quoted = true;
            // Unquoted comma: end of the cell.
            } else if (character == ',') {
                // Store the cell.
                row.add(field.toString());
                // Reset for the next cell.
                field.setLength(0);
            // Unquoted line break: end of the row.
            } else if (character == '\n' || character == '\r') {
                // Store the last cell.
                row.add(field.toString());
                // Reset the buffer.
                field.setLength(0);
                // Store the row.
                rows.add(row);
                // Begin the next row.
                row = new ArrayList<>();
                // Treat a Windows carriage return plus line feed pair as a single line break.
                if (character == '\r' && index + 1 < content.length() && content.charAt(index + 1) == '\n') {
                    // Consume the line feed of the pair.
                    index++;
                }
            } else {
                // Ordinary character: add it to the current cell.
                field.append(character);
            }
        }
        // End of input while still inside quotes.
        if (quoted) {
            // The closing quote is missing, so the file is damaged.
            throw new IOException("unterminated quoted CSV field");
        }
        // Input ended right after a quoted cell.
        if (afterQuote) {
            // Store that final cell.
            row.add(field.toString());
        // Input ended without a trailing newline, so a final cell or row may still be pending.
        } else if (field.length() > 0 || !row.isEmpty() || (!content.isEmpty() && !content.endsWith("\n") && !content.endsWith("\r"))) {
            // Store the pending last cell (possibly empty after a trailing comma).
            row.add(field.toString());
        }
        // Add the last row only if it holds anything; this avoids a phantom empty row after the final newline.
        if (!row.isEmpty()) {
            // Store the last row.
            rows.add(row);
        }
        // The whole file as rows of cells.
        return rows;
    }

    // Turns rows back into CSV text, quoting cells that need it.
    private static String render(List<List<String>> rows, String newline) {
        // Holds the CSV text being built.
        StringBuilder output = new StringBuilder();
        // One output line per row.
        for (List<String> row : rows) {
            // Go through the cells left to right.
            for (int index = 0; index < row.size(); index++) {
                // No comma before the first cell.
                if (index > 0) {
                    // Separator between cells.
                    output.append(',');
                }
                // Cell text to write.
                String value = row.get(index);
                // A comma, quote or line break in the text would corrupt the layout unless the cell is quoted.
                if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                        || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                    // Open the quote and double any quote characters inside.
                    output.append('"').append(value.replace("\"", "\"\""));
                    // Close the quote.
                    output.append('"');
                } else {
                    // Plain cell: write as is.
                    output.append(value);
                }
            }
            // End the row with the same line ending the file had.
            output.append(newline);
        }
        // The finished CSV text, ready to be written to the temp file.
        return output.toString();
    }
}
