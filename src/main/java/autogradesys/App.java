package autogradesys;

import java.util.Scanner;
import java.io.IOException;
import java.io.File;

import autogradesys.filesys.FileUtil;


public class App {
    public static final String SUBMISSIONS_PROMPT = "Enter submissions folder path  : ";
    public static final String TESTERS_PROMPT     = "Enter tester files folder path : ";
    public static final String SCORESHEET_PROMPT  = "Enter scoresheet file path     : ";

    public static void main(String[] args) {
        // Title
        System.out.println("Auto-Grader System");
        System.out.println();

        // Prompt user for dirpaths
        // TODO: Improve the UX by making a save file and config.
        // TODO: Clean the code
        Scanner sc = new Scanner(System.in);

        File submissions = null;
        while (submissions == null) {
            try {
                submissions = FileUtil.getFile(FileUtil.promptPath(SUBMISSIONS_PROMPT, sc));
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
        
        File testers = null;
        while (testers == null) {
            try {
                testers     = FileUtil.getFile(FileUtil.promptPath(TESTERS_PROMPT, sc));
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
        
        File scoresheet = null;
        while (scoresheet == null) {
            try {
                scoresheet  = FileUtil.getFile(FileUtil.promptPath(SCORESHEET_PROMPT, sc));
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }

        // TODO: Put the function that passes in the three file objects.

        // Close the scanner after the user exits the program.
        sc.close();
        
        // TODO: Display exit messages
        System.out.println();
        System.out.printf("\"%s\"%n\"%s\"%n\"%s\"%n", submissions, testers, scoresheet);

    }
}
