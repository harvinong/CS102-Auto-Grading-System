package autogradesys.grading;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes a portable, self-contained view of one grading run.
 *
 * <p>Last step of the grade-students flow: after JavaGrader has scored everything and the
 * ErrorReporter has collected the problems, the menu passes both here and gets one HTML file
 * with summary cards, a per-student score table and the list of issues with guidance.</p>
 */
public final class HtmlReportWriter {
    // Static-only utility class, so no instances are allowed.
    private HtmlReportWriter() {
    }

    /** Writes only the supplied current run; existing reports are replaced. */
    // Entry point called by the menu after grading; IOException lets the menu report a write failure.
    public static void write(Path path, GradeResult result, List<ErrorRecord> errors) throws IOException {
        // A missing destination is a caller bug, so fail loudly.
        if (path == null) {
            // Use IOException so the caller's existing write-error handling covers it.
            throw new IOException("No HTML report path was provided.");
        }
        // The whole page is built in memory and written to disk in a single call at the end.
        StringBuilder html = new StringBuilder();
        // For each student, count the questions scored exactly 1, then add the counts together.
        int passCount = result.scores().values().stream().mapToInt(scores ->
                (int) scores.values().stream().filter(score -> score == 1).count()).sum();
        // Total number of student-question results, passes and failures together.
        int outcomeCount = result.scores().values().stream().mapToInt(Map::size).sum();
        // Anything not passed counts as failed, so the cards always add up.
        int failedCount = outcomeCount - passCount;
        // Page start: doctype, language and UTF-8 charset.
        html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                // Makes the report scale properly on phones.
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                // Browser tab title, then the opening of the inline stylesheet.
                .append("<title>Grading run report</title><style>")
                // All CSS is inline so the report is a single file that can be emailed or opened offline.
                .append("body{font-family:system-ui,sans-serif;line-height:1.45;margin:0;background:#f4f7fb;color:#172033}main{max-width:1100px;margin:auto;padding:32px 20px}header,.card{background:white;border:1px solid #d9e1ee;border-radius:14px;padding:22px;margin-bottom:18px;box-shadow:0 2px 8px #17203312}h1{margin:0 0 6px;color:#123b6d}h2{margin-top:0}.muted{color:#5c6b80;font-size:.9rem}.summary{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}.metric{background:#edf4ff;border-radius:10px;padding:14px}.metric strong{display:block;font-size:1.7rem;color:#123b6d}table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:10px;border-bottom:1px solid #e3e8f0;vertical-align:top}th{background:#f5f8fc}.pass{color:#087443;font-weight:700}.fail{color:#a32626;font-weight:700}.error{border-left:4px solid #d25a36;padding:12px 14px;margin:12px 0;background:#fff7f3;border-radius:4px}.error h3{font-size:1rem;margin:0 0 6px}.error p{margin:4px 0;white-space:pre-wrap;overflow-wrap:anywhere}.pill{display:inline-block;border-radius:99px;padding:2px 8px;background:#e8edf5;font-size:.8rem}@media(max-width:650px){main{padding:18px 10px}.summary{grid-template-columns:repeat(2,1fr)}table{font-size:.88rem}th,td{padding:8px 5px}}")
                // Close the stylesheet and open the page header with a Run ID line.
                .append("</style></head><body><main><header><h1>Grading run report</h1><div class=\"muted\">Run ID: ")
                // A random id identifies this run; escape() is used on all dynamic text for consistency.
                .append(escape(UUID.randomUUID().toString())).append("<br>Started: ")
                // Timestamp of when the report was generated, in UTC.
                .append(escape(Instant.now().toString())).append("</div></header>")
                // Summary cards, starting with the number of students graded.
                .append("<section class=\"summary\"><div class=\"metric\"><strong>").append(result.studentCount())
                // Second card: total question outcomes.
                .append("</strong>students</div><div class=\"metric\"><strong>").append(outcomeCount)
                // Third card: passes, styled green by the pass class.
                .append("</strong>question outcomes</div><div class=\"metric\"><strong class=\"pass\">").append(passCount)
                // Fourth card: failures, styled red.
                .append("</strong>passed</div><div class=\"metric\"><strong class=\"fail\">").append(failedCount)
                // Close the last card and the summary section.
                .append("</strong>failed</div></section>");
        // Open the results table with its Student, Question and Score columns.
        html.append("<section class=\"card\"><h2>Student outcomes</h2><table><thead><tr><th>Student</th><th>Question</th><th>Score</th></tr></thead><tbody>");
        // Walk every student and every question, emitting one table row per result in insertion order.
        result.scores().forEach((student, scores) -> scores.forEach((question, score) -> html.append("<tr><td>")
                // Student and question names come from folder names, so they are escaped before being placed in HTML.
                .append(escape(student)).append("</td><td>").append(escape(question)).append("</td><td class=\"")
                // Green PASS for a score of 1, otherwise red FAIL, with the numeric score in brackets.
                .append(score == 1 ? "pass\">PASS" : "fail\">FAIL").append(" (" ).append(score).append(")</td></tr>")));
        // Close the results table.
        html.append("</tbody></table></section>");
        // Open the second section, which lists every recorded error.
        html.append("<section class=\"card\"><h2>Issues and guidance</h2>");
        // No errors recorded in this run.
        if (errors.isEmpty()) {
            // Positive note so the section is not left blank.
            html.append("<p class=\"pass\">No issues were recorded.</p>");
        } else {
            // One block per ErrorRecord, in the order they were reported.
            for (ErrorRecord error : errors) {
                // Start the block with the category shown as a small pill.
                html.append("<article class=\"error\"><h3><span class=\"pill\">")
                        // Heading shows category and stage; error text can contain student code or paths, so it is escaped.
                        .append(escape(error.category())).append("</span> ").append(escape(error.stage())).append("</h3>")
                        // What went wrong; compile errors and output diffs from students are escaped here.
                        .append("<p><strong>Message:</strong> ").append(escape(error.message())).append("</p>")
                        // How to fix it.
                        .append("<p><strong>Guidance:</strong> ").append(escape(error.guidance())).append("</p>")
                        // Which student, submission and question it relates to (already escaped by context()).
                        .append("<p class=\"muted\"><strong>Context:</strong> ").append(context(error)).append("</p>")
                        // Close this error block.
                        .append("</article>");
            }
        }
        // Close the issues section and finish the page with a footer note, body and html tags.
        html.append("</section><footer class=\"muted\">Generated from this grading run only. History JSONL remains append-only.</footer></main></body></html>\n");
        // Clean absolute destination path.
        Path absolute = path.toAbsolutePath().normalize();
        // Folder that must exist before the file can be written.
        Path parent = absolute.getParent();
        // Guard for a path with no parent folder.
        if (parent != null) {
            // Create the report folder on first use; no-op if present.
            Files.createDirectories(parent);
        }
        // Write the finished page as UTF-8, creating the file if it does not exist.
        Files.writeString(absolute, html, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                // TRUNCATE_EXISTING discards any previous report, so the file only describes this run.
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    // Builds the escaped "student=... submission=... question=..." text for one error.
    private static String context(ErrorRecord error) {
        // Collects the parts that are present.
        StringBuilder context = new StringBuilder();
        // Add only the fields this error actually has; each is escaped and followed by a space.
        if (error.student() != null) context.append("student=").append(escape(error.student())).append(' ');
        // Include the submission path when known.
        if (error.submission() != null) context.append("submission=").append(escape(error.submission())).append(' ');
        // Include the question when known.
        if (error.question() != null) context.append("question=").append(escape(error.question())).append(' ');
        // Errors with no context (fatal setup problems) are labelled "run-level"; otherwise trim the trailing space.
        return context.length() == 0 ? "run-level" : context.toString().trim();
    }

    // Converts text to HTML-safe text, protecting the report from markup or script in student names or output.
    private static String escape(String value) {
        // Accumulates the safe version.
        StringBuilder escaped = new StringBuilder();
        // Check characters one at a time.
        for (char character : value.toCharArray()) {
            // Replace only the five characters that are special in HTML.
            switch (character) {
                // Must be handled so a literal ampersand is not read as the start of an entity.
                case '&' -> escaped.append("&amp;");
                // Stops text from opening a tag.
                case '<' -> escaped.append("&lt;");
                // Closing counterpart of the previous case.
                case '>' -> escaped.append("&gt;");
                // Protects double-quoted attribute values.
                case '"' -> escaped.append("&quot;");
                // Protects single-quoted attribute values.
                case '\'' -> escaped.append("&#39;");
                // Everything else is safe to copy unchanged.
                default -> escaped.append(character);
            }
        }
        // The finished safe string.
        return escaped.toString();
    }
}
