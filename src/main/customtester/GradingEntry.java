package autogradesys;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

class PerStudentListener implements TestExecutionListener { //listens to the test's execution

    // Key = full path like "2023-2024-chee.teo.2022 > Q2 > Q2b"
    // Value = [passed, total]
    private final Map<String, int[]> scores = new LinkedHashMap<>();
    private TestPlan testPlan;

    @Override
    /**
     * Keeps the JUnit test plan so each finished test can later be traced back to its parent groups.
     *
     * @param testPlan the plan JUnit is about to execute, which holds the tree of tests and their ancestors
     */
    public void testPlanExecutionStarted(TestPlan testPlan) {
        this.testPlan = testPlan; //this is just a callback that has the whole tree of what to run where what where why whatever
    }

    @Override
    /**
     * Counts one finished test towards every level of its path (student, question, sub-question), and prints
     * the name and reason of a test that did not pass. Containers such as classes and groups are ignored.
     *
     * @param testIdentifier the test that just finished; non-leaf nodes are skipped
     * @param result whether the test passed, failed or was aborted, and the exception if there was one
     */
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult result) {
        if (!testIdentifier.isTest()) return; // only count leaf DynamicTests
        if (result.getStatus() != TestExecutionResult.Status.SUCCESSFUL) {
        System.out.println("FAILED: " + testIdentifier.getDisplayName());
        result.getThrowable().ifPresent(t -> {
            System.out.println("  Reason: " + t);
            
        });
    }
        boolean passed = result.getStatus() == TestExecutionResult.Status.SUCCESSFUL;

       
        List<String> ancestorNames = collectAncestorDisplayNames(testIdentifier);

        
        StringBuilder pathBuilder = new StringBuilder();
        for (String name : ancestorNames) {
            if (pathBuilder.length() > 0) pathBuilder.append(" > ");
            pathBuilder.append(stripPrefix(name));

            String key = pathBuilder.toString();
            int[] tally = scores.computeIfAbsent(key, k -> new int[2]);
            tally[1]++; // total++
            if (passed) tally[0]++; // passed++
        }
    }

    /**
     * Turns the flat path tallies into per-student sub-question scores. Only paths exactly five levels deep
     * are sub-questions; shallower paths (totals for a student or a question) are skipped.
     *
     * @param scores tallies keyed by the full path joined with " > ", each value being {passed, total}
     * @return a map from student folder name to (sub-question name to its {@link Score}), in the order first
     *         seen; empty when no sub-question results were recorded
     */
    public static Map<String, Map<String, Score>> getSubQuestionScoresByStudent(Map<String, int[]> scores) {
    Map<String, Map<String, Score>> result = new LinkedHashMap<>();

    for (Map.Entry<String, int[]> entry : scores.entrySet()) {
        String[] parts = entry.getKey().split(" > ");
        if (parts.length != 5) continue;            // sub-question depth only

        int[] tally = entry.getValue();
        result.computeIfAbsent(parts[2], k -> new LinkedHashMap<>())
              .put(parts[4], new Score(tally[0], tally[1]));
    }
    return result;
}
   


    /**
     * Lists the display names of the groups a test sits in, outermost first, leaving out the engine-level root.
     *
     * @param leaf the test whose ancestors are wanted
     * @return the ancestor names from outermost to innermost; empty if the test has no ancestor below the root
     */
    private List<String> collectAncestorDisplayNames(TestIdentifier leaf) {
        LinkedList<String> names = new LinkedList<>();
        Optional<TestIdentifier> current = testPlan.getParent(leaf);

        while (current.isPresent()) {
            TestIdentifier node = current.get();
            if (testPlan.getParent(node).isEmpty()) break; // stop before the engine-level root
            names.addFirst(node.getDisplayName());
            current = testPlan.getParent(node);
        }
        return names;
    }

    /**
     * Removes the academic-year prefix from a name so paths show only the student.
     *
     * @param displayName a JUnit display name, which may start with {@code 2023-2024-}
     * @return the name without that prefix, or the name unchanged when it does not start with it
     */
    private String stripPrefix(String displayName) {
        return displayName.startsWith("2023-2024-")
                ? displayName.substring("2023-2024-".length())
                : displayName;
    }

    /**
     * Prints every collected path with its passed and total counts to standard output, one {@code path: passed/total} line each.
     */
    public void printScores() {
        scores.forEach((path, s) ->
                System.out.println(path + ": " + s[0] + "/" + s[1]));
    }

    /**
     * Exposes the tallies gathered so far.
     *
     * @return the live map from a full path (joined with " > ") to {passed, total}; changes made to it affect this listener
     */
    public Map<String, int[]> getScores() {
        return scores;
    }
}
public class GradingEntry {
    
    /**
     * Runs the {@link StudentFileExec} test class through the JUnit launcher, waits for it to finish, and
     * collects the sub-question scores.
     *
     * @return a map from student folder name to (sub-question name to its {@link Score}); empty if no
     *         sub-question results were produced
     */
    public static Map<String, Map<String, Score>> runGrading() {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(StudentFileExec.class)) // finds this class 
                .build();

        Launcher launcher = LauncherFactory.create();
        PerStudentListener listener = new PerStudentListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);               // blocks until all tests finish

        return PerStudentListener.getSubQuestionScoresByStudent(listener.getScores());
    }
    /**
     * Runs the grading and prints one {@code student -> scores} line per student.
     *
     * @param args unused; command-line arguments are ignored
     */
    public static void main(String[] args) {
        Map<String, Map<String, Score>> results = runGrading();
        results.forEach((student, subs) -> System.out.println(student + " -> " + subs));
    }
}
