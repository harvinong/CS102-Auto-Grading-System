package autogradesys;
import java.util.List;
import java.io.BufferedWriter;
import java.io.IOException; // The error Java raises when reading/writing a file fails
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path; // Represents a file location
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter; // Formats a date/time into a string
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import autogradesys.grading.GradeResult; // What JavaGrader.grade hands back: the scores

// Saves the grader's scores into a dated copy of the course scoresheet (e.g. IS442-ScoreSheet.csv).
// ScoresheetDAO does the file reading and writing; ScoreRecorder decides what marks go in and where
// the copy is saved. The original scoresheet is never changed.
public class ScoreRecorder {
    // Pattern for the date and time added to the file name, e.g. 2026-11-11_11-04-00
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSSSSSSSS");


<<<<<<< HEAD
=======
    // Input: the scoresheet file, the scores (student -> sub-question -> marks), and the error reporter.
    // Function: Merges the scores into the existing scoresheet. JavaGrader calls this after grading.
    // Output: true if the scoresheet was updated, false if something went wrong.
    /**
     * Updates the scoresheet with the marks produced by the grading run.
     * @param scoresheet the value used by this method.
     * @param scores the value used by this method.
     * @param reporter the value used by this method.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
    public static boolean recordScores(Path scoresheet, Map<String, Map<String, Integer>> scores,
            autogradesys.grading.ErrorReporter reporter) {
        return autogradesys.grading.CsvScoresheet.update(scoresheet, scores, reporter);
    }

>>>>>>> 4d262fc (Add parameter return and exception documentation)
    // Input: the GradeResult that JavaGrader.grade returns. It holds
    //        scores():         student -> (question -> marks earned), e.g. "alice" -> {"Q1" -> 2, "Q2" -> 0}
    //        availableMarks(): question -> marks it is worth,          e.g. "Q1" -> 3
    // Function: Writes the results to a NEW timestamped CSV in the same format as recordScores:
    //           username, one column per question (sorted), final_score, then a "% full marks" row.
    //           A student has full marks on a question when marks earned == marks it is worth.
    // Output: Path to the newly created CSV file
<<<<<<< HEAD
=======
    /**
     * Exports the grading result and diagnostics to the configured report files.
     * @param result the value used by this method.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     * @throws IOException when the operation cannot complete.
     */
>>>>>>> 4d262fc (Add parameter return and exception documentation)
    public static Path exportResults(GradeResult result) throws IOException {
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
        Collections.sort(questions); // Sort  alphabetically

        // Name the file after the current date and time, e.g. 2026-11-10_03-28-45-123456789.csv
        Path file = Path.of(LocalDateTime.now().format(TIMESTAMP) + ".csv");
        // CREATE_NEW means: fail instead of overwriting an existing file.
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            writer.write("username"); // First column title
            for (String question : questions) { // One column title per question
                writer.write("," + quote(question));
            }
            writer.write(",final_score\r\n"); // Last column title, then end the line

            // One row per student, and count full marks per question as we go
            Map<String, Integer> fullMarks = new LinkedHashMap<>(); 
            for (Map.Entry<String, Map<String, Integer>> entry : scores.entrySet()) { 
                writer.write(quote(entry.getKey())); // First cell: the student's username
                int total = 0; 
                for (String question : questions) { 
                    // A question the student has no entry for scores 0.
                    int marks = entry.getValue().getOrDefault(question, 0);
                    total += marks;
                    writer.write("," + marks);
                    // Full marks: earned everything the question is worth (and it is worth something).
                    int worth = result.marksFor(question);
                    if (worth > 0 && marks == worth) {
                        fullMarks.put(question, fullMarks.getOrDefault(question, 0) + 1);
                    }
                }
                writer.write("," + total + "\r\n"); // Last cell: the total, then end the row
            }

            // Last row: % of students who got full marks on each question
            int studentCount = scores.size(); 
            writer.write(quote("% full marks")); // First cell: the row's label
            for (String question : questions) { 
                int correct = fullMarks.getOrDefault(question, 0); // Students with full marks
                double percent = studentCount == 0 ? 0 : correct * 100.0 / studentCount;
                writer.write("," + String.format("%.1f%%", percent)); // e.g. 66.7%
            }
            writer.write(",\r\n"); // Leave the final_score cell empty, then end the row
        } 
        return file;
    }

    // Input: A text value to put in the CSV, e.g. a username or sub-question name
    // Function: Wraps the value in double quotes and doubles any quotes inside it, so commas cannot break the CSV
    // Output: The quoted value, e.g. chee"teo becomes "chee""teo"
<<<<<<< HEAD
=======
    /**
     * Escapes a value for safe inclusion in a CSV field.
     * @param value the value used by this method.
     * @return the value produced by this operation, including any empty or absent result defined by its contract.
     */
>>>>>>> 4d262fc (Add parameter return and exception documentation)
    private static String quote(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
