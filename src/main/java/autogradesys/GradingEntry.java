package autogradesys;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    public static Map<String, Map<String, String>> getSubQuestionScoresByStudent(Map<String, int[]> scores) {
    Map<String, Map<String, String>> result = new LinkedHashMap<>();

    for (Map.Entry<String, int[]> entry : scores.entrySet()) {
        String[] parts = entry.getKey().split(" > ");

        // Only keep paths exactly at the sub-question depth (5 segments)
        if (parts.length != 5) continue;

        String studentName = parts[2];
        String subQuestionName = parts[4];
        int[] tally = entry.getValue();
        String scoreStr = tally[0] + "/" + tally[1];

        result.computeIfAbsent(studentName, k -> new LinkedHashMap<>())
              .put(subQuestionName, scoreStr);
        }

    return result;
    }
    public static void writeScoresToJson(Map<String, int[]> scores, Path outputFile) throws Exception {
    Map<String, Map<String, String>> studentScores = getSubQuestionScoresByStudent(scores);

    StringBuilder json = new StringBuilder();
    json.append("{\n");

    int studentIndex = 0;
    for (Map.Entry<String, Map<String, String>> studentEntry : studentScores.entrySet()) {
        json.append("  \"").append(escapeJson(studentEntry.getKey())).append("\": {\n");

        int subQIndex = 0;
        Map<String, String> subScores = studentEntry.getValue();
        for (Map.Entry<String, String> subEntry : subScores.entrySet()) {
            json.append("    \"").append(escapeJson(subEntry.getKey())).append("\": \"")
                .append(escapeJson(subEntry.getValue())).append("\"");
            subQIndex++;
            if (subQIndex < subScores.size()) json.append(",");
            json.append("\n");
        }

        json.append("  }");
        studentIndex++;
        if (studentIndex < studentScores.size()) json.append(",");
        json.append("\n");
        }

    json.append("}\n");

    Files.writeString(outputFile, json.toString());
    }

private static String escapeJson(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
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

    public Map<String, int[]> getScores() {
        return scores;
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
        Path outputJson = Paths.get("scores.json"); // written to project root, same working-dir logic as before
        try {
            PerStudentListener.writeScoresToJson(perStudentListener.getScores(), outputJson);
        } catch (Exception e) {
            // TODO Auto-generated c
            System.err.println("Cannot generate JSON file");
        }
System.out.println("Scores written to: " + outputJson.toAbsolutePath());
    }
}
