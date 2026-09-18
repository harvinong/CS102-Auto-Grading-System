package autogradesys;

import java.util.Scanner;
import java.io.File;

import autogradesys.filesys.FileUtil;
import autogradesys.display.Cli;
import autogradesys.display.Prompt;


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
        Scanner sc = new Scanner(System.in);

        File submissions = Cli.promptFile(Prompt.SUBMISSIONS_PROMPT, sc);
        File testers = Cli.promptFile(Prompt.TESTERS_PROMPT, sc);
        File scoresheet = Cli.promptFile(Prompt.SCORESHEET_PROMPT, sc);
        
        // TODO: Put the function that passes in the three file objects.

        // Close the scanner after the user exits the program.
        sc.close();
        
        // TODO: Display exit messages
        System.out.println();
        System.out.printf("\"%s\"%n\"%s\"%n\"%s\"%n", submissions, testers, scoresheet);

    }
}
