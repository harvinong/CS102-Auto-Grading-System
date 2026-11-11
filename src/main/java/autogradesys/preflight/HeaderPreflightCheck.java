package autogradesys.preflight;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import autogradesys.preflight.PreflightFinding.Category;
import autogradesys.preflight.PreflightFinding.Severity;
import autogradesys.validation.Student;
import autogradesys.validation.UsernameMatcher;
import autogradesys.validation.UsernameResolver;



/**
 * Read-only check for missing or incomplete Java file headers.
 * Does not modify submitted files.
 */
public final class HeaderPreflightCheck {

    private static final int HEADER_LINES = 10;

    private static final Pattern NAME_LINE = Pattern.compile(
            "(?im)^([ \\t]*(?:\\*+|//+)?[ \\t]*name[ \\t]*:)[ \\t]*(.*?)[ \\t]*$");

    private static final Pattern EMAIL_LINE = Pattern.compile(
            "(?im)^([ \\t]*(?:\\*+|//+)?[ \\t]*email(?:[ \\t]*id)?[ \\t]*:)[ \\t]*(.*?)[ \\t]*$");

    private static final Pattern HEADER_LABEL = Pattern.compile(
            "(?im)^[ \\t]*(?:\\*+|//+)?[ \\t]*(?:name|email(?:[ \\t]*id)?)[ \\t]*:");

    /**
     * Initializes the read-only checker for student source-file headers.
     */
    private HeaderPreflightCheck() {
    }

    /**
     * Adds missing/incomplete header findings to an existing preflight report.
     * @param report the argument supplied to this operation.
     * @param submissionsDir the argument supplied to this operation.
     * @param students the registered student identities used for matching.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    public static PreflightReport addFindings(
            PreflightReport report,
            Path submissionsDir,
            List<Student> students) {

        List<PreflightFinding> findings = new ArrayList<>();
        Map<String, String> owners = UsernameResolver.owners(report.students(), students);

        for (String folder : report.students()) {

            // Do not check the blank starter template.
            if (report.templateCopy(folder)) {
                continue;
            }

            // Associate the folder with its registered student.
            String claimed = UsernameResolver.claimedUsername(folder);

            Optional<Student> matched = UsernameMatcher.findExact(claimed, students);

            if (matched.isEmpty()) {
                matched = UsernameMatcher.findClosest(claimed, students);
            }

            if (matched.isEmpty()) {
                continue;
            }

            Student student = matched.get();

            // If another folder already owns this student's username,
            // the typo folder is ambiguous. Do not check it against that student.
            String owner = owners.get(student.username().toLowerCase(Locale.ROOT));

            if (owner != null && !owner.equals(folder)) {
                continue;
            }

            Path studentDir = submissionsDir.resolve(folder);

            if (!Files.isDirectory(studentDir, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }

            scanStudent(folder, studentDir, student, findings);
        }

        if (findings.isEmpty()) {
            return report;
        }

        // Keep the existing findings and append the header findings.
        List<PreflightFinding> all = new ArrayList<>(report.findings());
        all.addAll(findings);

        return new PreflightReport(
                report.templateName(),
                report.students(),
                all);
    }

    /**
     * Scans one submission against the expected folder and file layout.
     * @param folder the directory whose contents are processed.
     * @param studentDir the directory containing one student submission.
     * @param student the registered student identity.
     * @param findings the argument supplied to this operation.
     */
    private static void scanStudent(
            String folder,
            Path studentDir,
            Student student,
            List<PreflightFinding> findings) {

        try (Stream<Path> paths = Files.walk(studentDir)) {

            paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".java"))
                    .filter(path -> !isJunk(studentDir.relativize(path)))
                    .sorted()
                    .forEach(path -> checkFile(folder, studentDir, path, student, findings));

        } catch (IOException e) {
            // The normal preflight scan is responsible for reporting unreadable files/folders.
        }
    }

    /**
     * Checks one source file header against the registered student identity.
     * @param folder the directory whose contents are processed.
     * @param studentDir the directory containing one student submission.
     * @param file the file being validated or processed.
     * @param student the registered student identity.
     * @param findings the argument supplied to this operation.
     */
    private static void checkFile(
            String folder,
            Path studentDir,
            Path file,
            Student student,
            List<PreflightFinding> findings) {

        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String header = firstLines(content, HEADER_LINES);

            String relativePath = studentDir.relativize(file)
                    .toString().replace('\\', '/');

            String displayPath = folder + "/" + relativePath;

            // No Name or Email ID label appears in the header.
            if (!HEADER_LABEL.matcher(header).find()) {
                findings.add(new PreflightFinding(
                        folder,
                        Category.MISSING_HEADER,
                        Severity.ERROR,
                        displayPath,
                        "Java file is missing a header.",
                        "Choose \"Fix common mistakes automatically\" to add or repair the header."));
                return;
            }

            // A header exists, but it must contain both valid fields.
            Matcher nameMatcher = NAME_LINE.matcher(header);
            Matcher emailMatcher = EMAIL_LINE.matcher(header);

            boolean validName = nameMatcher.find()
                    && !nameMatcher.group(2).isBlank()
                    && nameMatches(nameMatcher.group(2), student.fullName());

            boolean validEmail = emailMatcher.find()
                    && !emailMatcher.group(2).isBlank()
                    && emailMatcher.group(2).toLowerCase(Locale.ROOT)
                            .contains(student.username().toLowerCase(Locale.ROOT));

            if (!validName || !validEmail) {
                findings.add(new PreflightFinding(
                        folder,
                        Category.INCOMPLETE_HEADER,
                        Severity.ERROR,
                        displayPath,
                        "Java file has an incomplete or incorrect header.",
                        "Choose \"Fix common mistakes automatically\" to repair the header."));
            }

        } catch (IOException e) {
            // Do not misreport unreadable files as having missing headers.
        }
    }

    /**
     * Returns the first requested lines of a source file for diagnostic messages.
     * @param content the argument supplied to this operation.
     * @param maxLines the argument supplied to this operation.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    private static String firstLines(String content, int maxLines) {
        int end = 0;
        int lines = 0;

        while (end < content.length() && lines < maxLines) {
            char c = content.charAt(end++);
            if (c == '\n') {
                lines++;
            }
        }

        return content.substring(0, end);
    }

    /**
     * Reports whether a header name identifies the expected student.
     * @param headerName the argument supplied to this operation.
     * @param fullName the argument supplied to this operation.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    private static boolean nameMatches(String headerName, String fullName) {
        String[] expectedParts = fullName.toLowerCase(Locale.ROOT).trim().split("\\s+");
        String actual = headerName.toLowerCase(Locale.ROOT);

        for (String part : expectedParts) {
            if (!actual.contains(part)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Reports whether a path is a generated or otherwise ignored submission file.
     * @param relativePath the argument supplied to this operation.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
     */
    private static boolean isJunk(Path relativePath) {
        for (Path part : relativePath) {
            String name = part.toString();

            if (name.startsWith(".") || name.equals("__MACOSX")) {
                return true;
            }
        }

        return false;
    }
}