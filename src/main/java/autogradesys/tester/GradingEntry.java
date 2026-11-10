package autogradesys.tester;

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

class PerStudentListener implements TestExecutionListener { // listens to the test's execution

    // Key = full path like "2023-2024-chee.teo.2022 > Q2 > Q2b"
    // Value = [passed, total]
    private final Map<String, int[]> scores = new LinkedHashMap<>();
    private TestPlan testPlan;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        this.testPlan = testPlan; // this is just a callback that has the whole tree of what to run where what
                                  // where why whatever
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult result) {
        if (!testIdentifier.isTest())
            return; // only count leaf DynamicTests
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
            if (pathBuilder.length() > 0)
                pathBuilder.append(" > ");
            pathBuilder.append(stripPrefix(name));

            String key = pathBuilder.toString();
            int[] tally = scores.computeIfAbsent(key, k -> new int[2]);
            tally[1]++; // total++
            if (passed)
                tally[0]++; // passed++
        }
    }

    public static Map<String, Map<String, Score>> getSubQuestionScoresByStudent(Map<String, int[]> scores) {
        Map<String, Map<String, Score>> result = new LinkedHashMap<>();

        for (Map.Entry<String, int[]> entry : scores.entrySet()) {
            String[] parts = entry.getKey().split(" > ");
            if (parts.length != 5)
                continue; // sub-question depth only

            int[] tally = entry.getValue();
            result.computeIfAbsent(parts[2], k -> new LinkedHashMap<>())
                    .put(parts[4], new Score(tally[0], tally[1]));
        }
        return result;
    }

    private List<String> collectAncestorDisplayNames(TestIdentifier leaf) {
        LinkedList<String> names = new LinkedList<>();
        Optional<TestIdentifier> current = testPlan.getParent(leaf);

        while (current.isPresent()) {
            TestIdentifier node = current.get();
            if (testPlan.getParent(node).isEmpty())
                break; // stop before the engine-level root
            names.addFirst(node.getDisplayName());
            current = testPlan.getParent(node);
        }
        return names;
    }

    private String stripPrefix(String displayName) {
        return displayName.startsWith("2023-2024-")
                ? displayName.substring("2023-2024-".length())
                : displayName;
    }

    public void printScores() {
        scores.forEach((path, s) -> System.out.println(path + ": " + s[0] + "/" + s[1]));
    }

    public Map<String, int[]> getScores() {
        return scores;
    }
}

public class GradingEntry {

    public static Map<String, Map<String, Score>> runGrading() {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(StudentFileExecutor.class)) // finds this class
                .build();

        Launcher launcher = LauncherFactory.create();
        PerStudentListener listener = new PerStudentListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request); // blocks until all tests finish

        return PerStudentListener.getSubQuestionScoresByStudent(listener.getScores());
    }

    public static void main(String[] args) {
        Map<String, Map<String, Score>> results = runGrading();
        System.out.println(results.size());
        results.forEach((student, subs) -> System.out.println(student + " -> " + subs));
    }
}
