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
// An immutable record: the report is just these three values plus helper methods.
/**
 * Stores all findings and student statuses produced by the submission preflight.
 */
public record PreflightReport(String templateName, List<String> students, List<PreflightFinding> findings) {

    // How wide the name column is, so "ready" / "needs attention" line up in the console.
    private static final int NAME_WIDTH = 28;

    // True when the check could not run at all (bad template or submissions folder).
    // Treat any fatal finding as a setup failure that must stop grading before compilation.
    /**
     * Reports whether the findings contain a fatal condition.
     */
    public boolean fatal() {
        // Any single FATAL finding is enough.
        return findings.stream().anyMatch(f -> f.severity() == Severity.FATAL);
    }

    // True when this student has at least one ERROR finding.
    /**
     * Reports whether the specified student has a finding requiring intervention.
     */
    public boolean needsAttention(String student) {
        // Look through every finding of the run for one that belongs to this student and has ERROR severity.
        return findings.stream()
                // Keep only this student's findings that are errors.
                .anyMatch(f -> f.student().equals(student) && f.severity() == Severity.ERROR);
    }

    // True when this folder carries the template's name; grading skips it, so it is neither ready nor needing attention.
    /**
     * Reports whether the student appears to have submitted a copy of the template.
     */
    public boolean templateCopy(String student) {
        // A TEMPLATE_COPY finding for this folder marks it as an untouched copy of the starter template.
        return findings.stream()
                .anyMatch(f -> f.student().equals(student) && f.category() == PreflightFinding.Category.TEMPLATE_COPY);
    }

    // How many students have no ERROR finding (template copies are not graded, so they are not counted).
    /**
     * Counts students whose submissions are ready for grading.
     */
    public long readyCount() {
        // Count the students who do not need attention.
        return students.stream().filter(s -> !templateCopy(s) && !needsAttention(s)).count();
    }

    // How many folders are template copies that grading skips.
    /**
     * Counts students whose submissions cannot be graded as submitted.
     */
    public long notGradedCount() {
        // Count the folders carrying a TEMPLATE_COPY finding; JavaGrader is told to skip exactly these.
        return students.stream().filter(this::templateCopy).count();
    }

    // How many findings have the given severity (used for the summary line).
    /**
     * Counts findings having the requested severity.
     */
    public long count(Severity severity) {
        // Count the findings whose severity matches.
        return findings.stream().filter(f -> f.severity() == severity).count();
    }

    // Writes the whole report to the given stream (the console in the real program).
    /**
     * Writes the current report or diagnostic information to the supplied output stream.
     */
    public void print(PrintStream out) {
        // Blank line to separate the report from the menu text above it.
        out.println();
        // Heading that names the template everything was compared against.
        out.println("__ Submission check (template: " + templateName + ") __");

        // A fatal report prints only its diagnostics, never an "all ready" result.
        if (fatal()) {
            // Show each diagnostic with its fix.
            for (PreflightFinding f : findings) {
                printFinding(out, f);
            }
            // Say clearly that no student was looked at.
            out.println("Submission check skipped: no submissions were scanned.");
            // Repeat the safety boundary.
            out.println("(No files were changed during this check.)");
            // Trailing blank line before the menu returns.
            out.println();
            // Nothing else to print.
            return;
        }

        // First print findings about the run as a whole (e.g. loose files, ignored archives).
        for (PreflightFinding f : findings) {
            // Run-level findings have an empty student name.
            if (f.student().isEmpty()) {
                printFinding(out, f);
            }
        }

        // Then one block per student, already sorted by name.
        for (String student : students) {
            // Decide the status word for this student: a template copy is skipped by grading whatever else it has.
            String status = templateCopy(student) ? "not graded"
                    : needsAttention(student) ? "needs attention" : "ready";
            // Name, dots, then the status.
            out.println(pad(student) + status);
            // List that student's findings underneath, in discovery order.
            for (PreflightFinding f : findings) {
                // Only findings that belong to this student.
                if (f.student().equals(student)) {
                    printFinding(out, f);
                }
            }
        }

        // Students without an ERROR finding are ready; template copies are counted on their own.
        long ready = readyCount();
        long notGraded = notGradedCount();
        // One-line summary with all the counts.
        out.println("Checked " + students.size() + " submissions: "
                + ready + " ready, " + (students.size() - ready - notGraded) + " need attention"
                + (notGraded > 0 ? ", " + notGraded + " not graded" : "") + " ("
                + "errors: " + count(Severity.ERROR) + ", warnings: " + count(Severity.WARNING)
                + ", notes: " + count(Severity.NOTE) + ").");
        // Remind the reader what this check does not do.
        out.println("(Non-Java resources were synchronised in the working copy. Original submissions and the Starting Folder were not changed. Ready does not mean correct.)");
        // Trailing blank line before the menu returns.
        out.println();
    }

    // Pads a name with dots so the status words line up: "alice ........ ready".
    /**
     * Pads a student name to the report column width.
     */
    private static String pad(String name) {
        // Start with the name and one space.
        StringBuilder sb = new StringBuilder(name).append(' ');
        // Keep adding dots until the column is wide enough.
        while (sb.length() < NAME_WIDTH) {
            sb.append('.');
        }
        // One more space before the status word.
        return sb.append(' ').toString();
    }

    // Prints one finding as "  [CATEGORY] path - message -> fix".
    /**
     * Prints one preflight finding with its severity and suggested action.
     */
    private static void printFinding(PrintStream out, PreflightFinding f) {
        // Ignored clutter (IGNORED_JUNK) is never printed; AutoGradeMenu.runGrader records each one with ErrorReporter.ignored instead.
        if (f.category() == PreflightFinding.Category.IGNORED_JUNK) return;
        // Indent, bracketed category, then where it happened.
        StringBuilder sb = new StringBuilder("  ").append(f.getTagWithColor()).append(" ").append(f.path());
        // Add the explanation when there is one.
        if (!f.message().isEmpty()) {
            sb.append(" - ").append(f.message());
        }
        // Add the suggested fix when there is one.
        if (!f.fix().isEmpty()) {
            sb.append(" -> ").append(f.fix());
        }
        // Emit the finished line.
        out.println(sb);
    }
}
