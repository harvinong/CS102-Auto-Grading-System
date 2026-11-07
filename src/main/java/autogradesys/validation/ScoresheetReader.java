package autogradesys.validation;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.opencsv.CSVReader;

/**
 * Reads the registered students from the scoresheet CSV. Columns are found by their title,
 * so the column order of the export does not matter.
 */

public final class ScoresheetReader {

    // Column titles used by the scoresheet export.
    private static final String USERNAME_COLUMN = "Username";
    private static final String EMAIL_COLUMN = "Email";
    private static final String FIRST_NAME_COLUMN = "First Name";
    private static final String LAST_NAME_COLUMN = "Last Name";
    // The export puts '#' in front of ids and '_' where a last name is empty.
    private static final String ID_PREFIX = "#";
    private static final String EMPTY_NAME = "_";

    private ScoresheetReader() {
    }

    /**
     * @param scoresheet The scoresheet CSV file.
     * @return Every student row, in file order.
     * @throws IOException If the file cannot be read or has no Username column.
     */
    public static List<Student> read(File scoresheet) throws IOException {
        try (CSVReader reader = new CSVReader(Files.newBufferedReader(scoresheet.toPath(), StandardCharsets.UTF_8))) {
            String[] header = reader.readNext();
            if (header == null) {
                throw new IOException("The scoresheet is empty.");
            }
            int usernameCol = columnOf(header, USERNAME_COLUMN);
            if (usernameCol < 0) {
                throw new IOException("The scoresheet has no '" + USERNAME_COLUMN + "' column.");
            }
            int emailCol = columnOf(header, EMAIL_COLUMN);
            int firstCol = columnOf(header, FIRST_NAME_COLUMN);
            int lastCol = columnOf(header, LAST_NAME_COLUMN);

            List<Student> students = new ArrayList<>();
            String[] row;
            while ((row = reader.readNext()) != null) {
                String username = cell(row, usernameCol).replace(ID_PREFIX, "").trim();
                if (username.isEmpty()) {
                    continue;
                }
                String last = cell(row, lastCol);
                String fullName = (cell(row, firstCol) + " " + (last.equals(EMPTY_NAME) ? "" : last)).trim();
                students.add(new Student(username, fullName, cell(row, emailCol)));
            }
            return students;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            // opencsv 5 added a checked CsvValidationException; catching Exception compiles on 4.x and 5.x.
            throw new IOException("Could not read the scoresheet: " + e.getMessage(), e);
        }
    }

    // Index of the column with this title (ignoring case), or -1 if there is none.
    private static int columnOf(String[] header, String title) {
        for (int i = 0; i < header.length; i++) {
            if (header[i].trim().equalsIgnoreCase(title)) {
                return i;
            }
        }
        return -1;
    }

    // The trimmed cell at this column, or "" if the column or cell does not exist.
    private static String cell(String[] row, int column) {
        return column < 0 || column >= row.length ? "" : row[column].trim();
    }
}
