package autogradesys;



public class App {
    public static final String SUBMISSIONS_PROMPT = "Enter submissions folder path  : ";
    public static final String TESTERS_PROMPT     = "Enter tester files folder path : ";
    public static final String SCORESHEET_PROMPT  = "Enter scoresheet file path     : ";

    public static void main(String[] args) {
        // Title
        // T  H  E    S  I  M  P  S  O  N  SSSSSS
        System.out.println("Auto-Grader System");
        GradingEntry.main(null);

       

    }
}
