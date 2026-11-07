package autogradesys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the real menu in a separate JVM: first-run setup, then "Grade students". The working
 * directory is a temp folder so the program's ./config.json never touches the developer's copy.
 */
class RunGraderPreflightTest {

    // Fresh temp folder per test; holds the template, submissions and the program's working directory.
    @TempDir
    Path root;

    // Creates a file (and its parent folders) with the given text.
    private static void file(Path base, String rel, String content) throws IOException {
        // Where the file goes.
        Path p = base.resolve(rel);
        // Make sure the parent folders exist.
        Files.createDirectories(p.getParent());
        // Write the content.
        Files.writeString(p, content);
    }

    // Starts the real program, types the given lines into it, and returns everything it printed.
    private String runMenu(String input) throws IOException, InterruptedException {
        // Working directory for the program: ./config.json is created here, not in the repository.
        Path work = Files.createDirectories(root.resolve("work"));
        // Launch "java -cp <this test's classpath> autogradesys.App" exactly like run.bat does.
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), "autogradesys.App")
                .directory(work.toFile())
                // Merge error output so a crash would show up in the assertions.
                .redirectErrorStream(true)
                .start();
        // Type the scripted answers, then close the input.
        process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();
        // Read everything the program printed.
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        // The program must exit normally.
        assertEquals(0, process.waitFor(), output);
        return output;
    }

    // The scripted answers: three config paths, menu option 3, the submissions path, then 0 to quit.
    private static String script(Path startingFolder, Path testers, Path scores, Path submissions) {
    return String.join("\n", startingFolder.toString(), testers.toString(), scores.toString(),
            "3", submissions.toString(), "N", "0") + "\n";
    }

    // New: for runs where the preflight is fatal, the prompt is skipped, so there is no "N"
    private static String scriptWithoutFixPrompt(Path startingFolder, Path testers, Path scores, Path submissions) {
        return String.join("\n", startingFolder.toString(), testers.toString(), scores.toString(),
                "3", submissions.toString(), "0") + "\n";
    }

    @Test
    void gradeStudentsPrintsThePreflightThenTheExistingTodoLine() throws Exception {
        // A one-question template.
        Path template = root.resolve("RenameToYourStudentID");
        file(template, "Q1/Q1a.java", "class Q1a {}");
        // The other two configuration inputs.
        Path testers = Files.createDirectories(root.resolve("testers"));
        Path scores = root.resolve("scores.csv");
        Files.writeString(scores, "username,final_score\n");
        // One good student and one whose work is inside an un-renamed wrapper folder.
        Path subs = root.resolve("subs");
        file(subs, "alice/Q1/Q1a.java", "class Q1a { int x; }");
        file(subs, "bob/RenameToYourStudentID/Q1/Q1a.java", "class Q1a { int x; }");

        // Run the real menu.
        String output = runMenu(script(template, testers, scores, subs));

        // The summary and the wrapper finding appear.
        assertTrue(output.contains("Checked 2 submissions: 1 ready, 1 need attention"), output);
        assertTrue(output.contains("[WRAPPED_FOLDER] bob/RenameToYourStudentID/"), output);
        // The report comes BEFORE the existing TODO line, which is still printed.
        int report = output.indexOf("__ Submission check");
        int todo = output.indexOf("Running autograder!");
        assertTrue(report >= 0 && todo > report, output);
        // Nothing crashed.
        assertFalse(output.contains("Exception"), output);
        // The scoresheet is untouched.
        assertEquals("username,final_score\n", Files.readString(scores));
        // No compiled class appeared next to the submitted source (nothing was compiled or run).
        assertFalse(Files.exists(subs.resolve("alice").resolve("Q1").resolve("Q1a.class")));
    }

    @Test
    void startingFolderOneLevelTooHighIsRejectedThroughTheMenu() throws Exception {
        // The instructor's course folder holds the template AND the submissions.
        Path course = root.resolve("course");
        file(course, "RenameToYourStudentID/Q1/Q1a.java", "class Q1a {}");
        file(course, "subs/alice/Q1/Q1a.java", "class Q1a { int x; }");
        Path testers = Files.createDirectories(root.resolve("testers"));
        Path scores = root.resolve("scores.csv");
        Files.writeString(scores, "username,final_score\n");

        // Mistake: the Starting Folder is the course folder instead of the template inside it.
        String output = runMenu(scriptWithoutFixPrompt(course, testers, scores, course.resolve("subs")));

        // A single clear diagnostic with the exact folder to use instead.
        assertTrue(output.contains("[TEMPLATE_INVALID]"), output);
        assertTrue(output.contains("one level too high"), output);
        assertTrue(output.contains("Set Starting Folder to " + course.resolve("RenameToYourStudentID")), output);
        // Nothing was scanned, so no student is listed as ready or needing attention.
        assertTrue(output.contains("no submissions were scanned"), output);
        assertFalse(output.contains("needs attention"), output);
        // The existing TODO line still follows.
        assertTrue(output.contains("Running autograder!"), output);
    }

    @Test
    void zipBesideItsExtractedFolderIsNotCountedThroughTheMenu() throws Exception {
        // A normal template and a student who is both extracted (frank/) and still zipped (frank.zip).
        Path template = root.resolve("RenameToYourStudentID");
        file(template, "Q1/Q1a.java", "class Q1a {}");
        Path testers = Files.createDirectories(root.resolve("testers"));
        Path scores = root.resolve("scores.csv");
        Files.writeString(scores, "username,final_score\n");
        Path subs = root.resolve("subs");
        file(subs, "frank/Q1/Q1a.java", "class Q1a { int x; }");
        file(subs, "frank.zip", "PK");

        String output = runMenu(script(template, testers, scores, subs));

        // Only one submission is counted and it is ready.
        assertTrue(output.contains("Checked 1 submissions: 1 ready, 0 need attention"), output);
        // The zip is explained by a note instead of being an extra student.
        assertTrue(output.contains("[ARCHIVE_ALREADY_EXTRACTED] frank.zip"), output);
        assertFalse(output.contains("[UNEXTRACTED_ARCHIVE]"), output);
    }
}
