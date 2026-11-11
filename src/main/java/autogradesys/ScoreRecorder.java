package autogradesys;
import java.io.BufferedWriter; // Writes text to a file efficiently
import java.io.IOException; // The error Java raises when reading/writing a file fails
import java.nio.charset.StandardCharsets; // Read and save as UTF-8
import java.nio.file.*; // File operations
import java.time.*;
import java.time.format.DateTimeFormatter; // Formats a date/time into a string
import java.util.*;

import autogradesys.grading.GradeResult; // What JavaGrader.grade hands back: scores + marks each question is worth

// Writes the grader's scores into a copy of the course scoresheet (e.g. IS442-ScoreSheet.csv).
// The original scoresheet is never changed.
public class ScoreRecorder {
    // Pattern for the date and time added to the file name, e.g. 2026-11-11_11-04-00
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    // Column that holds the student's username, e.g. #ping.lee.2023
    private static final String USERNAME_COLUMN = "Username";
    // Column where the student's total marks go
    private static final String TOTAL_COLUMN = "Calculated Final Grade Numerator";
    // Column that must stay last in the course scoresheet; the question columns go just before it
    private static final String END_COLUMN = "End-of-Line Indicator";

    // Input: the GradeResult that JavaGrader.grade returns, and the course scoresheet file. The result holds
    //        scores(): student -> (question -> marks earned), e.g. "ping.lee.2023" -> {"Q1" -> 6, "Q2" -> 10}
    // Function: Makes a copy of the scoresheet next to the original, named with the date and time,
    //           e.g. IS442-ScoreSheet_2026-11-11_11-04-00.csv. The copy keeps every row and column
    //           of the original, adds one column per question with each student's marks, and puts
    //           each student's total in "Calculated Final Grade Numerator".
    //           A student in the sheet with no graded submission keeps empty mark cells.
    //           A graded student who is not in the sheet is listed in a warning.
    // Output: Path to the newly created copy
    public static Path exportResults(GradeResult result, Path scoresheet) throws IOException {
        Map<String, Map<String, Integer>> scores = result.scores(); // student -> (question -> marks)

        // Every question any student was graded on becomes a column, sorted: Q1, Q2, ...
        List<String> questions = new ArrayList<>();
        for (Map<String, Integer> studentScores : scores.values()) {
            for (String question : studentScores.keySet()) {
                if (!questions.contains(question)) { // Only add it if it is not already a column
                    questions.add(question);
                }
            }
        }
        Collections.sort(questions); // Sort alphabetically

        // Read the original scoresheet, one line per row
        String text = Files.readString(scoresheet, StandardCharsets.UTF_8);
        // Keep the same line ending as the original file (Windows \r\n or Mac/Linux \n)
        String newLine = text.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\r?\n")));
        // Drop blank lines at the end of the file
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }
        if (lines.isEmpty()) {
            throw new IOException("The scoresheet is empty: " + scoresheet);
        }

        // The first row is the header, which names the columns
        List<String> header = splitLine(lines.get(0));
        int usernameColumn = columnOf(header, USERNAME_COLUMN);
        if (usernameColumn < 0) {
            throw new IOException("The scoresheet has no '" + USERNAME_COLUMN + "' column: " + scoresheet);
        }
        int totalColumn = columnOf(header, TOTAL_COLUMN); // -1 if the sheet has no total column
        // The question columns go just before "End-of-Line Indicator", or at the end if there is none
        int insertAt = columnOf(header, END_COLUMN);
        if (insertAt < 0) {
            insertAt = header.size();
        }

        // Build the new header: the original columns with the question columns added
        List<String> newHeader = new ArrayList<>(header);
        newHeader.addAll(insertAt, questions);

        // Usernames found in the sheet, to spot graded students who are missing from it
        List<String> seen = new ArrayList<>();

        // Name the copy after the original plus the date and time, and save it next to the original
        String name = scoresheet.getFileName().toString();
        String baseName = name.toLowerCase().endsWith(".csv") ? name.substring(0, name.length() - 4) : name;
        Path file = scoresheet.toAbsolutePath().resolveSibling(
                baseName + "_" + LocalDateTime.now().format(TIMESTAMP) + ".csv");

        // CREATE_NEW means: fail instead of overwriting an existing file
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            writer.write(joinLine(newHeader) + newLine); // Header row first

            // One output row per student row in the original sheet
            for (int i = 1; i < lines.size(); i++) {
                List<String> row = splitLine(lines.get(i));
                // Pad short rows so every row has a cell for every original column
                while (row.size() < header.size()) {
                    row.add("");
                }
                // The username without the "#" the course sheet puts in front, e.g. ping.lee.2023
                String username = row.get(usernameColumn).replace("#", "").trim();
                Map<String, Integer> studentScores = scores.get(username); // null if not graded
                if (studentScores != null) {
                    seen.add(username);
                }

                // One cell per question: the marks, or empty if this student was not graded
                List<String> markCells = new ArrayList<>();
                int total = 0;
                for (String question : questions) {
                    if (studentScores == null) {
                        markCells.add("");
                    } else {
                        // A question the student has no entry for scores 0
                        int marks = studentScores.getOrDefault(question, 0);
                        total += marks;
                        markCells.add(Integer.toString(marks));
                    }
                }
                // Put the total in "Calculated Final Grade Numerator"
                if (studentScores != null && totalColumn >= 0) {
                    row.set(totalColumn, Integer.toString(total));
                }
                row.addAll(insertAt, markCells); // Add the question cells in the same place as the header
                writer.write(joinLine(row) + newLine);
            }
        }

        // Warn about graded students the scoresheet does not list, so their marks are not lost silently
        for (String student : scores.keySet()) {
            if (!seen.contains(student)) {
                System.out.println("[WARNING] " + student + " was graded but is not in the scoresheet, "
                        + "so their marks are not in " + file.getFileName());
            }
        }
        return file;
    }

    // Input: the header row (a list of column names) and the column name to look for
    // Function: Finds the column, ignoring upper/lower case and spaces around the name
    // Output: the column's position (0 = first), or -1 if it is not there
    private static int columnOf(List<String> header, String column) {
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).trim().equalsIgnoreCase(column)) {
                return i;
            }
        }
        return -1;
    }

    // Input: one line of the CSV file, e.g. #01400001,#ping.lee.2023,_,PING LEE
    // Function: Splits the line at commas. A cell in double quotes may hold commas, and "" inside it means one quote.
    // Output: the cells, e.g. ["#01400001", "#ping.lee.2023", "_", "PING LEE"]
    private static List<String> splitLine(String line) {
        List<String> cells = new ArrayList<>();
        String cell = ""; // The cell being read
        boolean inQuotes = false; // True while inside a "quoted" cell
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell = cell + '"'; // "" inside quotes is one quote character
                    i++; // Skip the second quote
                } else if (c == '"') {
                    inQuotes = false; // End of the quoted cell
                } else {
                    cell = cell + c;
                }
            } else if (c == '"') {
                inQuotes = true; // Start of a quoted cell
            } else if (c == ',') {
                cells.add(cell); // A comma ends the cell
                cell = "";
            } else {
                cell = cell + c;
            }
        }
        cells.add(cell); // The last cell has no comma after it
        return cells;
    }

    // Input: the cells of one row
    // Function: Joins them with commas, quoting any cell that holds a comma, a quote or a line break
    // Output: one CSV line, e.g. #01400001,#ping.lee.2023,_,PING LEE
    private static String joinLine(List<String> cells) {
        String line = "";
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                line = line + ",";
            }
            line = line + quoteIfNeeded(cells.get(i));
        }
        return line;
    }

    // Input: a text value to put in the CSV
    // Function: Wraps the value in double quotes (doubling any quotes inside) only when it holds a comma,
    //           a quote or a line break, so the rest of the sheet keeps the original look
    // Output: the value, quoted if needed, e.g. Tan, Ann becomes "Tan, Ann"
    private static String quoteIfNeeded(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
