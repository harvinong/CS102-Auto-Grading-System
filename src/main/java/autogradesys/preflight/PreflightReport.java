package autogradesys.preflight;

import java.io.PrintStream;
import java.util.List;

import autogradesys.preflight.PreflightFinding.Severity;

/**
 * The outcome of a {@link SubmissionPreflight} run. Holds every discovered submission and
 * every finding, and prints them in a fixed, deterministic order.
 *
 * @param templateName Name of the starter template folder the submissions were compared with.
 * @param students     Every discovered submission, sorted by name.
 * @param findings     All findings, in the order they were discovered.
 */
public record PreflightReport(String templateName, List<String> students, List<PreflightFinding> findings) {

    private static final int NAME_WIDTH = 28;

    public boolean fatal() {
        return findings.stream().anyMatch(f -> f.severity() == Severity.FATAL);
    }

    public boolean needsAttention(String student) {
        return findings.stream()
                .anyMatch(f -> f.student().equals(student) && f.severity() == Severity.ERROR);
    }

    public long readyCount() {
        return students.stream().filter(s -> !needsAttention(s)).count();
    }

    public long count(Severity severity) {
        return findings.stream().filter(f -> f.severity() == severity).count();
    }

    public void print(PrintStream out) {
        out.println();
        out.println("__ Submission check (template: " + templateName + ") __");

        if (fatal()) {
            for (PreflightFinding f : findings) {
                printFinding(out, f);
            }
            out.println("Submission check skipped: no submissions were scanned.");
            out.println("(Read-only check: nothing was graded, changed or moved.)");
            out.println();
            return;
        }

        for (PreflightFinding f : findings) {
            if (f.student().isEmpty()) {
                printFinding(out, f);
            }
        }

        for (String student : students) {
            boolean attention = needsAttention(student);
            out.println(pad(student) + (attention ? "needs attention" : "ready"));
            for (PreflightFinding f : findings) {
                if (f.student().equals(student)) {
                    printFinding(out, f);
                }
            }
        }

        long ready = readyCount();
        out.println("Checked " + students.size() + " submissions: "
                + ready + " ready, " + (students.size() - ready) + " need attention ("
                + "errors: " + count(Severity.ERROR) + ", warnings: " + count(Severity.WARNING)
                + ", notes: " + count(Severity.NOTE) + ").");
        out.println("(Read-only check: nothing was graded, changed or moved. Ready does not mean correct.)");
        out.println();
    }

    private static String pad(String name) {
        StringBuilder sb = new StringBuilder(name).append(' ');
        while (sb.length() < NAME_WIDTH) {
            sb.append('.');
        }
        return sb.append(' ').toString();
    }

    private static void printFinding(PrintStream out, PreflightFinding f) {
        StringBuilder sb = new StringBuilder("  [").append(f.category()).append("] ").append(f.path());
        if (!f.message().isEmpty()) {
            sb.append(" - ").append(f.message());
        }
        if (!f.fix().isEmpty()) {
            sb.append(" -> ").append(f.fix());
        }
        out.println(sb);
    }
}
