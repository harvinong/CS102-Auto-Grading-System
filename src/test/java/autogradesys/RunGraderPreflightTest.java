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

    @TempDir
    Path root;

    private static void file(Path base, String rel, String content) throws IOException {
        Path p = base.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    private String runMenu(String input) throws IOException, InterruptedException {
        Path work = Files.createDirectories(root.resolve("work"));
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), "autogradesys.App")
                .directory(work.toFile())
                .redirectErrorStream(true)
                .start();
        process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return output;
    }

    @Test
    void gradeStudentsPrintsThePreflightThenTheExistingTodoLine() throws Exception {
        Path template = root.resolve("RenameToYourStudentID");
        file(template, "Q1/Q1a.java", "class Q1a {}");
        Path testers = Files.createDirectories(root.resolve("testers"));
        Path scores = root.resolve("scores.csv");
        Files.writeString(scores, "username,final_score\n");
        Path subs = root.resolve("subs");
        file(subs, "alice/Q1/Q1a.java", "class Q1a { int x; }");
        file(subs, "bob/RenameToYourStudentID/Q1/Q1a.java", "class Q1a { int x; }");

        String input = String.join("\n", template.toString(), testers.toString(), scores.toString(),
                "3", subs.toString(), "0") + "\n";
        String output = runMenu(input);

        assertTrue(output.contains("Checked 2 submissions: 1 ready, 1 need attention"), output);
        assertTrue(output.contains("[WRAPPED_FOLDER] bob/RenameToYourStudentID/"), output);
        int report = output.indexOf("__ Submission check");
        int todo = output.indexOf("Running autograder!");
        assertTrue(report >= 0 && todo > report, output);
        assertFalse(output.contains("Exception"), output);
        assertEquals("username,final_score\n", Files.readString(scores));
        assertFalse(Files.exists(subs.resolve("alice").resolve("Q1").resolve("Q1a.class")));
    }
}
