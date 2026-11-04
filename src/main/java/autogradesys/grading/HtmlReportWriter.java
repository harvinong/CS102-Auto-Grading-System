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

/** Writes a portable, self-contained view of one grading run. */
public final class HtmlReportWriter {
    private HtmlReportWriter() {
    }

    /** Writes only the supplied current run; existing reports are replaced. */
    public static void write(Path path, GradeResult result, List<ErrorRecord> errors) throws IOException {
        if (path == null) {
            throw new IOException("No HTML report path was provided.");
        }
        // Build a fresh document so a report never carries data from an earlier run.
        StringBuilder html = new StringBuilder();
        // Count passing question results for the summary cards.
        int passCount = result.scores().values().stream().mapToInt(scores ->
                (int) scores.values().stream().filter(score -> score == 1).count()).sum();
        // Count every student-question result, including failures.
        int outcomeCount = result.scores().values().stream().mapToInt(Map::size).sum();
        // Derive failures from total outcomes so the displayed totals agree.
        int failedCount = outcomeCount - passCount;
        // Start the self-contained page with metadata and the report stylesheet.
        html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>Grading run report</title><style>")
                .append("body{font-family:system-ui,sans-serif;line-height:1.45;margin:0;background:#f4f7fb;color:#172033}main{max-width:1100px;margin:auto;padding:32px 20px}header,.card{background:white;border:1px solid #d9e1ee;border-radius:14px;padding:22px;margin-bottom:18px;box-shadow:0 2px 8px #17203312}h1{margin:0 0 6px;color:#123b6d}h2{margin-top:0}.muted{color:#5c6b80;font-size:.9rem}.summary{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}.metric{background:#edf4ff;border-radius:10px;padding:14px}.metric strong{display:block;font-size:1.7rem;color:#123b6d}table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:10px;border-bottom:1px solid #e3e8f0;vertical-align:top}th{background:#f5f8fc}.pass{color:#087443;font-weight:700}.fail{color:#a32626;font-weight:700}.error{border-left:4px solid #d25a36;padding:12px 14px;margin:12px 0;background:#fff7f3;border-radius:4px}.error h3{font-size:1rem;margin:0 0 6px}.error p{margin:4px 0;white-space:pre-wrap;overflow-wrap:anywhere}.pill{display:inline-block;border-radius:99px;padding:2px 8px;background:#e8edf5;font-size:.8rem}@media(max-width:650px){main{padding:18px 10px}.summary{grid-template-columns:repeat(2,1fr)}table{font-size:.88rem}th,td{padding:8px 5px}}")
                .append("</style></head><body><main><header><h1>Grading run report</h1><div class=\"muted\">Run ID: ")
                .append(escape(UUID.randomUUID().toString())).append("<br>Started: ")
                .append(escape(Instant.now().toString())).append("</div></header>")
                .append("<section class=\"summary\"><div class=\"metric\"><strong>").append(result.studentCount())
                .append("</strong>students</div><div class=\"metric\"><strong>").append(outcomeCount)
                .append("</strong>question outcomes</div><div class=\"metric\"><strong class=\"pass\">").append(passCount)
                .append("</strong>passed</div><div class=\"metric\"><strong class=\"fail\">").append(failedCount)
                .append("</strong>failed</div></section>");
        html.append("<section class=\"card\"><h2>Student outcomes</h2><table><thead><tr><th>Student</th><th>Question</th><th>Score</th></tr></thead><tbody>");
        // Add one escaped table row for every result in this run.
        result.scores().forEach((student, scores) -> scores.forEach((question, score) -> html.append("<tr><td>")
                .append(escape(student)).append("</td><td>").append(escape(question)).append("</td><td class=\"")
                .append(score == 1 ? "pass\">PASS" : "fail\">FAIL").append(" (" ).append(score).append(")</td></tr>")));
        html.append("</tbody></table></section>");
        html.append("<section class=\"card\"><h2>Issues and guidance</h2>");
        // Show a friendly empty state when grading produced no diagnostics.
        if (errors.isEmpty()) {
            html.append("<p class=\"pass\">No issues were recorded.</p>");
        } else {
            // Render each diagnostic with guidance and escaped context.
            for (ErrorRecord error : errors) {
                html.append("<article class=\"error\"><h3><span class=\"pill\">")
                        .append(escape(error.category())).append("</span> ").append(escape(error.stage())).append("</h3>")
                        .append("<p><strong>Message:</strong> ").append(escape(error.message())).append("</p>")
                        .append("<p><strong>Guidance:</strong> ").append(escape(error.guidance())).append("</p>")
                        .append("<p class=\"muted\"><strong>Context:</strong> ").append(context(error)).append("</p>")
                        .append("</article>");
            }
        }
        html.append("</section><footer class=\"muted\">Generated from this grading run only. History JSONL remains append-only.</footer></main></body></html>\n");
        // Normalize the destination and create its parent folder when needed.
        Path absolute = path.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        // Replace the old report atomically in meaning: only this run's HTML is written.
        Files.writeString(absolute, html, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static String context(ErrorRecord error) {
        StringBuilder context = new StringBuilder();
        if (error.student() != null) context.append("student=").append(escape(error.student())).append(' ');
        if (error.submission() != null) context.append("submission=").append(escape(error.submission())).append(' ');
        if (error.question() != null) context.append("question=").append(escape(error.question())).append(' ');
        return context.length() == 0 ? "run-level" : context.toString().trim();
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (char character : value.toCharArray()) {
            switch (character) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(character);
            }
        }
        return escaped.toString();
    }
}
