package autogradesys;
import java.io.BufferedWriter; // Writes text to a file efficiently
import java.io.IOException; // The error Java raises when reading/writing a file fails
import java.nio.charset.StandardCharsets; // Save as UTF-8
import java.nio.file.*; // File operations
import java.time.*;
import java.time.format.DateTimeFormatter; // Formats a date/time into a string
import java.util.*;

// Writes the grader's scores to a CSV file, one row per student and one column per sub-question.
public class ScoreRecorder {
    // Pattern for the file name, e.g. 2026-11-04_03-28-45-123456789.csv, so every run gets a new file
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSSSSSSSS");
    private static final String PATH_SEPARATOR = " > "; // Matches the separator
    private static final int STUDENT_LEVEL = 1; // "student" entry
    private static final int SUB_QUESTION_LEVEL = 3; // does this label have 3 parts? Then it’s a sub-question, so record it.
    private static final int PASSED = 0; // The first element of the int[] is the number of passed test cases.
    private static final int TOTAL = 1; // The second element of the int[] is the number of test cases run.

    // Input: Map<String, int[]> from the grader's PerStudentListener (GradingEntry.java).
    //        Keys are paths: "student", "student > Q1" or "student > Q1 > Q1a".
    //        Values are {passed, total}. Each passed test case is worth one mark.
    // Function: Takes the sub-question results and writes one row per student to a CSV file
    // Output: Path to the newly created CSV file
    public static Path recordScores(Map<String, int[]> results) throws IOException {
        // Reorganize the grader's labels into: student -> (sub-question -> marks)
        Map<String, Map<String, Integer>> scores = toStudentScores(results);

        // Every sub-question any student was graded on becomes a column, sorted: Q1a, Q1b, Q2a, ...
        List<String> subQuestions = new ArrayList<>(); // Start with an empty list of column names
        for (Map<String, Integer> studentScores : scores.values()) { // Go through each student's marks
            for (String subQuestion : studentScores.keySet()) { // Go through each sub-question they were graded on
                if (!subQuestions.contains(subQuestion)) { // Only add it if it is not already a column
                    subQuestions.add(subQuestion);
                }
            }
        }
        Collections.sort(subQuestions); // Sort the column names alphabetically: Q1a, Q1b, Q2a, ...

        // Name the file after the current date and time, e.g. 2026-11-04_03-28-45-123456789.csv
        Path file = Path.of(LocalDateTime.now().format(TIMESTAMP) + ".csv");
        // Open the file for writing. CREATE_NEW means: fail instead of overwriting an existing file.
        // "try (...)" closes the file automatically at the end, even if something goes wrong.
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            // Header row: username,"Q1a","Q1b",...,final_score
            writer.write("username"); // First column title
            for (String subQuestion : subQuestions) { // One column title per sub-question
                writer.write("," + quote(subQuestion)); // Comma separates columns
            }
            writer.write(",final_score\r\n"); // Last column title, then end the line (\r\n = Windows new line)

            // One row per student
            for (Map.Entry<String, Map<String, Integer>> entry : scores.entrySet()) { // entry = one student
                writer.write(quote(entry.getKey())); // First cell: the student's username
                int total = 0; // Running total of this student's marks
                for (String subQuestion : subQuestions) { // Fill the cells in the same order as the header
                    // A sub-question the student did not submit has no entry, so it scores 0.
                    int marks = entry.getValue().getOrDefault(subQuestion, 0);
                    total += marks; // Add this sub-question's marks to the total
                    writer.write("," + marks); // Write the marks into this column
                }
                writer.write("," + total + "\r\n"); // Last cell: the total, then end the row
            }

            // Last row: % of students who got full marks on each sub-question 
            Map<String, Integer> fullMarks = countFullMarks(results); // e.g. {"Q1a" -> 2, "Q1b" -> 1}
            int studentCount = scores.size(); // Number of students in the CSV
            writer.write(quote("% full marks")); // First cell: the row's label
            for (String subQuestion : subQuestions) { // Same column order as the header
                int correct = fullMarks.getOrDefault(subQuestion, 0); // Students who passed every test case
                double percent = studentCount == 0 ? 0 : correct * 100.0 / studentCount; // Avoid dividing by 0
                writer.write("," + String.format("%.1f%%", percent)); // e.g. 66.7%
            }
            writer.write(",\r\n"); // Leave the final_score cell empty, then end the row
        } // The file is closed (and saved) here
        return file;
    }

    // Input: Map<String, int[]> from the grader's PerStudentListener, same format as recordScores.
    // Function: Keeps only the sub-question entries ("student > Q1 > Q1a") and groups them by student.
    //           Question-level entries ("student > Q1") are skipped because they repeat the sub-question totals.
    //           A student with no gradable sub-question still gets an empty entry, so they appear in the CSV.
    // Output: Map of student -> (sub-question -> marks), e.g. "chee.teo.2022" -> {"Q1a" -> 2, "Q1b" -> 1}
    static Map<String, Map<String, Integer>> toStudentScores(Map<String, int[]> results) {
        Map<String, Map<String, Integer>> scores = new LinkedHashMap<>(); // Empty result, keeps students in order
        for (Map.Entry<String, int[]> entry : results.entrySet()) { // Look at every line of the grader's scoreboard
            // Split the label into parts, e.g. "chee > Q1 > Q1a" becomes ["chee", "Q1", "Q1a"]
            String[] path = entry.getKey().split(PATH_SEPARATOR);
            String student = path[0]; // The first part is always the student's name

            if (path.length == STUDENT_LEVEL) { // 1 part, e.g. "chee": the student's overall line
                // Register the student even if no sub-question could be graded, so they still get a row.
                scores.computeIfAbsent(student, k -> new LinkedHashMap<>());
            } else if (path.length == SUB_QUESTION_LEVEL) { // 3 parts, e.g. "chee > Q1 > Q1a": a sub-question
                scores.computeIfAbsent(student, k -> new LinkedHashMap<>()) // Get this student's marks (create if new)
                        .put(path[2], entry.getValue()[PASSED]); // Save sub-question name -> number of tests passed
            }
            // 2 parts, e.g. "chee > Q1": skipped, because it is just Q1a + Q1b added together
        }
        return scores; // e.g. {"chee" -> {"Q1a" -> 2, "Q1b" -> 1}}
    }

    // Input: Map<String, int[]> from the grader's PerStudentListener, same format as recordScores.
    // Function: Counts, for each sub-question, how many students passed every test case (passed == total).
    //           A student who did not submit, or whose file did not compile, is not counted as correct.
    // Output: Map of sub-question -> number of students with full marks, e.g. {"Q1a" -> 2, "Q1b" -> 1}
    static Map<String, Integer> countFullMarks(Map<String, int[]> results) {
        Map<String, Integer> fullMarks = new LinkedHashMap<>(); // Empty result
        for (Map.Entry<String, int[]> entry : results.entrySet()) { // Look at every line of the grader's scoreboard
            String[] path = entry.getKey().split(PATH_SEPARATOR); // e.g. ["chee", "Q1", "Q1a"]
            int[] counts = entry.getValue(); // {passed, total}
            // Only sub-question lines, and only when every test case passed
            if (path.length == SUB_QUESTION_LEVEL && counts[TOTAL] > 0 && counts[PASSED] == counts[TOTAL]) {
                fullMarks.put(path[2], fullMarks.getOrDefault(path[2], 0) + 1); // Add 1 to this sub-question's count
            }
        }
        return fullMarks;
    }

    // Input: A text value to put in the CSV, e.g. a username or sub-question name
    // Function: Wraps the value in double quotes and doubles any quotes inside it, so commas cannot break the CSV
    // Output: The quoted value, e.g. chee"teo becomes "chee""teo"
    private static String quote(String value) {
        // In CSV, a " inside a quoted value is written as "", then the whole value is wrapped in "..."
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
