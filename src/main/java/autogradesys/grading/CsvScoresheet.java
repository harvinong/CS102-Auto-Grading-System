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

/** Reads and atomically updates a CSV scoresheet while preserving existing rows.
 *
 * <p>Only known student score cells are changed; unrelated columns and rows
 * remain part of the rendered file.</p>
 */
public final class CsvScoresheet {
    private CsvScoresheet() {
    }

    /**
     * Applies the supplied scores and replaces the file only after rendering succeeds.
     *
     * @param path scoresheet to read and update
     * @param scores scores keyed by student and question
     * @param reporter destination for recoverable and fatal diagnostics
     * @return {@code true} when the updated scoresheet is safely written
     */
    /** Checks setup without modifying the scoresheet. */
    public static boolean isValid(Path path) {
        // Perform this small step of the grading workflow.
        try {
            // Prepare or update the value needed by the next grading step.
            List<List<String>> rows = parse(Files.readString(path.toAbsolutePath().normalize(), StandardCharsets.UTF_8));
            // Check this condition so grading follows the intended path.
            if (rows.isEmpty() || rows.get(0).isEmpty()
                    || rows.get(0).stream().allMatch(String::isBlank)) {
                // Return this computed result to the caller.
                return false;
            }
            // Prepare or update the value needed by the next grading step.
            List<String> header = rows.get(0);
            // Check this condition so grading follows the intended path.
            if (!header.contains("student")) {
                // Return this computed result to the caller.
                return false;
            }
            // Prepare or update the value needed by the next grading step.
            Set<String> headers = new HashSet<>();
            // Check this condition so grading follows the intended path.
            if (header.stream().anyMatch(column -> !headers.add(column))) {
                // Return this computed result to the caller.
                return false;
            }
            // Return this computed result to the caller.
            return rows.stream().skip(1).allMatch(row -> row.size() <= header.size());
        } catch (IOException | IllegalArgumentException exception) {
            // Return this computed result to the caller.
            return false;
        }
    }

    public static boolean update(Path path, Map<String, Map<String, Integer>> scores,
            ErrorReporter reporter) {
        // Prepare or update the value needed by the next grading step.
        Path absolute = path.toAbsolutePath().normalize();
        // Perform this small step of the grading workflow.
        final List<List<String>> rows;
        // Perform this small step of the grading workflow.
        try {
            // Prepare or update the value needed by the next grading step.
            rows = parse(Files.readString(absolute, StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException exception) {
            reporter.fatal("scoresheet", "scoresheet-read",
                    "Could not read scoresheet " + absolute + ": " + exception.getMessage(),
                    // Perform this small step of the grading workflow.
                    "Check that the CSV exists, is readable, and uses valid CSV quoting.");
            // Return this computed result to the caller.
            return false;
        }
        // Check this condition so grading follows the intended path.
        if (rows.isEmpty() || rows.get(0).isEmpty()
                || rows.get(0).stream().allMatch(String::isBlank)) {
            reporter.fatal("scoresheet", "scoresheet-header",
                    "Scoresheet has no usable header row.",
                    // Perform this small step of the grading workflow.
                    "Add a header row beginning with student and one column per question.");
            // Return this computed result to the caller.
            return false;
        }

        // Prepare or update the value needed by the next grading step.
        List<String> header = new ArrayList<>(rows.get(0));
        // Prepare or update the value needed by the next grading step.
        int studentColumn = header.indexOf("student");
        // Check this condition so grading follows the intended path.
        if (studentColumn < 0) {
            reporter.fatal("scoresheet", "scoresheet-header",
                    "Scoresheet must have a student column.",
                    // Perform this small step of the grading workflow.
                    "Add a header named student before grading.");
            // Return this computed result to the caller.
            return false;
        }
        // Prepare or update the value needed by the next grading step.
        Set<String> uniqueHeaders = new HashSet<>();
        // Repeat this operation for each relevant grading item.
        for (String column : header) {
            // Check this condition so grading follows the intended path.
            if (!uniqueHeaders.add(column)) {
                reporter.fatal("scoresheet", "scoresheet-header",
                        "Scoresheet contains duplicate column: " + column,
                        // Perform this small step of the grading workflow.
                        "Keep one unique column for each student field and question.");
                // Return this computed result to the caller.
                return false;
            }
        }

        // Build a complete copy first so pre-existing rows survive score updates.
        List<List<String>> writableRows = new ArrayList<>();
        // Perform this small step of the grading workflow.
        writableRows.add(header);
        // Prepare or update the value needed by the next grading step.
        Set<String> seenStudents = new LinkedHashSet<>();
        // Repeat this operation for each relevant grading item.
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            // Prepare or update the value needed by the next grading step.
            List<String> row = new ArrayList<>(rows.get(rowIndex));
            // Check this condition so grading follows the intended path.
            if (row.size() > header.size()) {
                reporter.fatal("scoresheet", "scoresheet-row",
                        "Scoresheet row " + (rowIndex + 1) + " has more values than its header.",
                        // Perform this small step of the grading workflow.
                        "Fix the CSV row before grading; the original file was left unchanged.");
                // Return this computed result to the caller.
                return false;
            }
            // Repeat this operation for each relevant grading item.
            while (row.size() < header.size()) {
                // Perform this small step of the grading workflow.
                row.add("");
            }
            // Prepare or update the value needed by the next grading step.
            String student = row.get(studentColumn);
            // Check this condition so grading follows the intended path.
            if (student.isBlank()) {
                reporter.recoverable("scoresheet", "scoresheet-row",
                        "Scoresheet row " + (rowIndex + 1) + " has no student name.",
                        // Perform this small step of the grading workflow.
                        "Fill in the student column; unrelated rows and scores will be preserved.",
                        // Perform this small step of the grading workflow.
                        null, absolute.toString(), null);
                // Perform this small step of the grading workflow.
                writableRows.add(row);
                // Perform this small step of the grading workflow.
                continue;
            }
            // Perform this small step of the grading workflow.
            seenStudents.add(student);
            // Prepare or update the value needed by the next grading step.
            Map<String, Integer> studentScores = scores.get(student);
            // Check this condition so grading follows the intended path.
            if (studentScores != null) {
                // Repeat this operation for each relevant grading item.
                for (Map.Entry<String, Integer> score : studentScores.entrySet()) {
                    // Prepare or update the value needed by the next grading step.
                    int questionColumn = header.indexOf(score.getKey());
                    // Check this condition so grading follows the intended path.
                    if (questionColumn < 0) {
                        reporter.recoverable("scoresheet", "scoresheet-update",
                                "Scoresheet is missing question column: " + score.getKey(),
                                // Perform this small step of the grading workflow.
                                "Add the question column before the next grading session; existing rows are safe.",
                                // Perform this small step of the grading workflow.
                                student, absolute.toString(), score.getKey());
                    } else {
                        // Perform this small step of the grading workflow.
                        row.set(questionColumn, Integer.toString(score.getValue()));
                    }
                }
            }
            // Perform this small step of the grading workflow.
            writableRows.add(row);
        }

        // Repeat this operation for each relevant grading item.
        for (String student : scores.keySet()) {
            // Check this condition so grading follows the intended path.
            if (!seenStudents.contains(student)) {
                reporter.recoverable("scoresheet", "scoresheet-row",
                        "No scoresheet row exists for student: " + student,
                        "Add a row for the student if their scores should be recorded.",
                        // Perform this small step of the grading workflow.
                        student, absolute.toString(), null);
            }
        }

        // Prepare or update the value needed by the next grading step.
        Path temporary = null;
        // Perform this small step of the grading workflow.
        try {
            // Prepare or update the value needed by the next grading step.
            Path parent = absolute.getParent();
            // Check this condition so grading follows the intended path.
            if (parent == null) {
                // Report this unrecoverable condition to the caller.
                throw new IOException("scoresheet has no parent directory");
            }
            // Perform this small step of the grading workflow.
            Files.createDirectories(parent);
            // Prepare or update the value needed by the next grading step.
            temporary = Files.createTempFile(parent, absolute.getFileName().toString() + ".", ".tmp");
            Files.writeString(temporary, render(writableRows), StandardCharsets.UTF_8,
                    // Perform this small step of the grading workflow.
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            // Replace only the completed temporary file, preserving the original on failure.
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE,
                        // Perform this small step of the grading workflow.
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                // Perform this small step of the grading workflow.
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            // Return this computed result to the caller.
            return true;
        } catch (IOException exception) {
            // Check this condition so grading follows the intended path.
            if (temporary != null) {
                // Perform this small step of the grading workflow.
                try {
                    // Perform this small step of the grading workflow.
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Preserve the original write error for the user.
                }
            }
            reporter.fatal("scoresheet", "scoresheet-write",
                    "Could not safely update scoresheet " + absolute + ": " + exception.getMessage(),
                    // Perform this small step of the grading workflow.
                    "Check permissions and ensure the scoresheet is not read-only; the original was retained.");
            // Return this computed result to the caller.
            return false;
        }
    }

    private static List<List<String>> parse(String content) throws IOException {
        // Prepare or update the value needed by the next grading step.
        List<List<String>> rows = new ArrayList<>();
        // Prepare or update the value needed by the next grading step.
        List<String> row = new ArrayList<>();
        // Prepare or update the value needed by the next grading step.
        StringBuilder field = new StringBuilder();
        // Prepare or update the value needed by the next grading step.
        boolean quoted = false;
        // Prepare or update the value needed by the next grading step.
        boolean afterQuote = false;
        // Repeat this operation for each relevant grading item.
        for (int index = 0; index < content.length(); index++) {
            // Prepare or update the value needed by the next grading step.
            char character = content.charAt(index);
            // Check this condition so grading follows the intended path.
            if (quoted) {
                // Check this condition so grading follows the intended path.
                if (character == '"') {
                    // Check this condition so grading follows the intended path.
                    if (index + 1 < content.length() && content.charAt(index + 1) == '"') {
                        // Perform this small step of the grading workflow.
                        field.append('"');
                        // Perform this small step of the grading workflow.
                        index++;
                    } else {
                        // Prepare or update the value needed by the next grading step.
                        quoted = false;
                        // Prepare or update the value needed by the next grading step.
                        afterQuote = true;
                    }
                } else {
                    // Perform this small step of the grading workflow.
                    field.append(character);
                }
            } else if (afterQuote) {
                // Check this condition so grading follows the intended path.
                if (character == ',') {
                    // Perform this small step of the grading workflow.
                    row.add(field.toString());
                    // Perform this small step of the grading workflow.
                    field.setLength(0);
                    // Prepare or update the value needed by the next grading step.
                    afterQuote = false;
                } else if (character == '\n' || character == '\r') {
                    // Perform this small step of the grading workflow.
                    row.add(field.toString());
                    // Perform this small step of the grading workflow.
                    field.setLength(0);
                    // Perform this small step of the grading workflow.
                    rows.add(row);
                    // Prepare or update the value needed by the next grading step.
                    row = new ArrayList<>();
                    // Prepare or update the value needed by the next grading step.
                    afterQuote = false;
                    // Check this condition so grading follows the intended path.
                    if (character == '\r' && index + 1 < content.length() && content.charAt(index + 1) == '\n') {
                        // Perform this small step of the grading workflow.
                        index++;
                    }
                } else {
                    // Report this unrecoverable condition to the caller.
                    throw new IOException("characters found after a quoted CSV field");
                }
            } else if (character == '"' && field.length() == 0) {
                // Prepare or update the value needed by the next grading step.
                quoted = true;
            } else if (character == ',') {
                // Perform this small step of the grading workflow.
                row.add(field.toString());
                // Perform this small step of the grading workflow.
                field.setLength(0);
            } else if (character == '\n' || character == '\r') {
                // Perform this small step of the grading workflow.
                row.add(field.toString());
                // Perform this small step of the grading workflow.
                field.setLength(0);
                // Perform this small step of the grading workflow.
                rows.add(row);
                // Prepare or update the value needed by the next grading step.
                row = new ArrayList<>();
                // Check this condition so grading follows the intended path.
                if (character == '\r' && index + 1 < content.length() && content.charAt(index + 1) == '\n') {
                    // Perform this small step of the grading workflow.
                    index++;
                }
            } else {
                // Perform this small step of the grading workflow.
                field.append(character);
            }
        }
        // Check this condition so grading follows the intended path.
        if (quoted) {
            // Report this unrecoverable condition to the caller.
            throw new IOException("unterminated quoted CSV field");
        }
        // Check this condition so grading follows the intended path.
        if (afterQuote) {
            // Perform this small step of the grading workflow.
            row.add(field.toString());
        } else if (field.length() > 0 || !row.isEmpty() || (!content.isEmpty() && !content.endsWith("\n") && !content.endsWith("\r"))) {
            // Perform this small step of the grading workflow.
            row.add(field.toString());
        }
        // Check this condition so grading follows the intended path.
        if (!row.isEmpty()) {
            // Perform this small step of the grading workflow.
            rows.add(row);
        }
        // Return this computed result to the caller.
        return rows;
    }

    private static String render(List<List<String>> rows) {
        // Prepare or update the value needed by the next grading step.
        StringBuilder output = new StringBuilder();
        // Repeat this operation for each relevant grading item.
        for (List<String> row : rows) {
            // Repeat this operation for each relevant grading item.
            for (int index = 0; index < row.size(); index++) {
                // Check this condition so grading follows the intended path.
                if (index > 0) {
                    // Perform this small step of the grading workflow.
                    output.append(',');
                }
                // Prepare or update the value needed by the next grading step.
                String value = row.get(index);
                // Check this condition so grading follows the intended path.
                if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                        || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                    // Perform this small step of the grading workflow.
                    output.append('"').append(value.replace("\"", "\"\""));
                    // Perform this small step of the grading workflow.
                    output.append('"');
                } else {
                    // Perform this small step of the grading workflow.
                    output.append(value);
                }
            }
            // Perform this small step of the grading workflow.
            output.append('\n');
        }
        // Return this computed result to the caller.
        return output.toString();
    }
}
