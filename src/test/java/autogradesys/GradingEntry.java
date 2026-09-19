package autogradesys;

import java.util.HashMap;
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

    private final Map<String, int[]> scores = new HashMap<>(); // studentName -> [passed, total]
    private TestPlan testPlan; // captured once execution starts

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        this.testPlan = testPlan; // stash it so executionFinished can use it later
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult result) {
        if (testIdentifier.isTest()) { // only count leaf DynamicTests, not containers
            String studentName = findStudentContainerName(testIdentifier)
                    .orElse("unknown");

            scores.computeIfAbsent(studentName, k -> new int[2]);
            scores.get(studentName)[1]++; // total++
            if (result.getStatus() == TestExecutionResult.Status.SUCCESSFUL) {
                scores.get(studentName)[0]++; // passed++
            }
        }
    }

    // Walk UP the tree from a leaf test until we find the container
    // whose display name looks like "Student: alice" — that's our target level.
    private Optional<String> findStudentContainerName(TestIdentifier identifier) {
        Optional<TestIdentifier> current = testPlan.getParent(identifier);

        while (current.isPresent()) {
            TestIdentifier node = current.get();
            String displayName = node.getDisplayName();

            if (displayName.startsWith("Student: ")) {
                return Optional.of(displayName.substring("Student: ".length()));
            }

            current = testPlan.getParent(node); // climb one more level up
        }

        return Optional.empty(); // reached the top without finding a student container
    }

    public void printScores() {
        scores.forEach((student, s) ->
                System.out.println(student + ": " + s[0] + "/" + s[1]));
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
