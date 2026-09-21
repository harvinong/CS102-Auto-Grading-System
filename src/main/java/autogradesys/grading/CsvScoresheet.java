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
        try {
            List<List<String>> rows = parse(Files.readString(path.toAbsolutePath().normalize(), StandardCharsets.UTF_8));
            if (rows.isEmpty() || rows.get(0).isEmpty()
                    || rows.get(0).stream().allMatch(String::isBlank)) {
                return false;
            }
            List<String> header = rows.get(0);
            if (!header.contains("student")) {
                return false;
            }
            Set<String> headers = new HashSet<>();
            if (header.stream().anyMatch(column -> !headers.add(column))) {
                return false;
            }
            return rows.stream().skip(1).allMatch(row -> row.size() <= header.size());
        } catch (IOException | IllegalArgumentException exception) {
            return false;
        }
    }

    public static boolean update(Path path, Map<String, Map<String, Integer>> scores,
            ErrorReporter reporter) {
        Path absolute = path.toAbsolutePath().normalize();
        final List<List<String>> rows;
        try {
            rows = parse(Files.readString(absolute, StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException exception) {
            reporter.fatal("scoresheet", "scoresheet-read",
                    "Could not read scoresheet " + absolute + ": " + exception.getMessage(),
                    "Check that the CSV exists, is readable, and uses valid CSV quoting.");
            return false;
        }
        if (rows.isEmpty() || rows.get(0).isEmpty()
                || rows.get(0).stream().allMatch(String::isBlank)) {
            reporter.fatal("scoresheet", "scoresheet-header",
                    "Scoresheet has no usable header row.",
                    "Add a header row beginning with student and one column per question.");
            return false;
        }

        List<String> header = new ArrayList<>(rows.get(0));
        int studentColumn = header.indexOf("student");
        if (studentColumn < 0) {
            reporter.fatal("scoresheet", "scoresheet-header",
                    "Scoresheet must have a student column.",
                    "Add a header named student before grading.");
            return false;
        }
        Set<String> uniqueHeaders = new HashSet<>();
        for (String column : header) {
            if (!uniqueHeaders.add(column)) {
                reporter.fatal("scoresheet", "scoresheet-header",
                        "Scoresheet contains duplicate column: " + column,
                        "Keep one unique column for each student field and question.");
                return false;
            }
        }

        // Build a complete copy first so pre-existing rows survive score updates.
        List<List<String>> writableRows = new ArrayList<>();
        writableRows.add(header);
        Set<String> seenStudents = new LinkedHashSet<>();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> row = new ArrayList<>(rows.get(rowIndex));
            if (row.size() > header.size()) {
                reporter.fatal("scoresheet", "scoresheet-row",
                        "Scoresheet row " + (rowIndex + 1) + " has more values than its header.",
                        "Fix the CSV row before grading; the original file was left unchanged.");
                return false;
            }
            while (row.size() < header.size()) {
                row.add("");
            }
            String student = row.get(studentColumn);
            if (student.isBlank()) {
                reporter.recoverable("scoresheet", "scoresheet-row",
                        "Scoresheet row " + (rowIndex + 1) + " has no student name.",
                        "Fill in the student column; unrelated rows and scores will be preserved.",
                        null, absolute.toString(), null);
                writableRows.add(row);
                continue;
            }
            seenStudents.add(student);
            Map<String, Integer> studentScores = scores.get(student);
            if (studentScores != null) {
                for (Map.Entry<String, Integer> score : studentScores.entrySet()) {
                    int questionColumn = header.indexOf(score.getKey());
                    if (questionColumn < 0) {
                        reporter.recoverable("scoresheet", "scoresheet-update",
                                "Scoresheet is missing question column: " + score.getKey(),
                                "Add the question column before the next grading session; existing rows are safe.",
                                student, absolute.toString(), score.getKey());
                    } else {
                        row.set(questionColumn, Integer.toString(score.getValue()));
                    }
                }
            }
            writableRows.add(row);
        }

        for (String student : scores.keySet()) {
            if (!seenStudents.contains(student)) {
                reporter.recoverable("scoresheet", "scoresheet-row",
                        "No scoresheet row exists for student: " + student,
                        "Add a row for the student if their scores should be recorded.",
                        student, absolute.toString(), null);
            }
        }

        Path temporary = null;
        try {
            Path parent = absolute.getParent();
            if (parent == null) {
                throw new IOException("scoresheet has no parent directory");
            }
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, absolute.getFileName().toString() + ".", ".tmp");
            Files.writeString(temporary, render(writableRows), StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            // Replace only the completed temporary file, preserving the original on failure.
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Preserve the original write error for the user.
                }
            }
            reporter.fatal("scoresheet", "scoresheet-write",
                    "Could not safely update scoresheet " + absolute + ": " + exception.getMessage(),
                    "Check permissions and ensure the scoresheet is not read-only; the original was retained.");
            return false;
        }
    }

    private static List<List<String>> parse(String content) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean afterQuote = false;
        for (int index = 0; index < content.length(); index++) {
            char character = content.charAt(index);
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < content.length() && content.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                        afterQuote = true;
                    }
                } else {
                    field.append(character);
                }
            } else if (afterQuote) {
                if (character == ',') {
                    row.add(field.toString());
                    field.setLength(0);
                    afterQuote = false;
                } else if (character == '\n' || character == '\r') {
                    row.add(field.toString());
                    field.setLength(0);
                    rows.add(row);
                    row = new ArrayList<>();
                    afterQuote = false;
                    if (character == '\r' && index + 1 < content.length() && content.charAt(index + 1) == '\n') {
                        index++;
                    }
                } else {
                    throw new IOException("characters found after a quoted CSV field");
                }
            } else if (character == '"' && field.length() == 0) {
                quoted = true;
            } else if (character == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (character == '\n' || character == '\r') {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                if (character == '\r' && index + 1 < content.length() && content.charAt(index + 1) == '\n') {
                    index++;
                }
            } else {
                field.append(character);
            }
        }
        if (quoted) {
            throw new IOException("unterminated quoted CSV field");
        }
        if (afterQuote) {
            row.add(field.toString());
        } else if (field.length() > 0 || !row.isEmpty() || (!content.isEmpty() && !content.endsWith("\n") && !content.endsWith("\r"))) {
            row.add(field.toString());
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        return rows;
    }

    private static String render(List<List<String>> rows) {
        StringBuilder output = new StringBuilder();
        for (List<String> row : rows) {
            for (int index = 0; index < row.size(); index++) {
                if (index > 0) {
                    output.append(',');
                }
                String value = row.get(index);
                if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                        || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                    output.append('"').append(value.replace("\"", "\"\""));
                    output.append('"');
                } else {
                    output.append(value);
                }
            }
            output.append('\n');
        }
        return output.toString();
    }
}
