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

class PerStudentListener implements TestExecutionListener {

    // Key = full path like "2023-2024-chee.teo.2022 > Q2 > Q2b"
    // Value = [passed, total]
    private final Map<String, int[]> scores = new LinkedHashMap<>();
    private TestPlan testPlan;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        this.testPlan = testPlan;
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult result) {
        if (!testIdentifier.isTest()) return; // only count leaf DynamicTests
        if (result.getStatus() != TestExecutionResult.Status.SUCCESSFUL) {
        System.out.println("FAILED: " + testIdentifier.getDisplayName());
        result.getThrowable().ifPresent(t -> {
            System.out.println("  Reason: " + t);
            t.printStackTrace();
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

    private String stripPrefix(String displayName) {
        return displayName.startsWith("2023-2024-")
                ? displayName.substring("2023-2024-".length())
                : displayName;
    }

    public void printScores() {
        scores.forEach((path, s) ->
                System.out.println(path + ": " + s[0] + "/" + s[1]));
    }
}
public class GradingEntry {
    
    public static void main(String[] args) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(StudentFileExec.class))
                .build();

        Launcher launcher = LauncherFactory.create();

        PerStudentListener perStudentListener = new PerStudentListener();
        launcher.registerTestExecutionListeners(perStudentListener);
        launcher.execute(request);

        System.out.println("=== Per-student scores ===");
        perStudentListener.printScores();
    }
}
