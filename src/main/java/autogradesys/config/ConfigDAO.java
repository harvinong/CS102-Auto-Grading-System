package autogradesys.config;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ConfigDAO {

    public static final File DEF_CONFIG_FILE = Path.of(".", "config.json").toFile();

    private File startingFolder;
    private File testersFolder;
    private File scoresheetFile;

    public boolean load() {
        if (!DEF_CONFIG_FILE.exists()) {
            return false;
        }

        try {
            // Ref: https://www.geeksforgeeks.org/java/how-to-read-and-write-json-files-in-java/
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(DEF_CONFIG_FILE);

            // Load values
            startingFolder = Path.of(jsonNode.get(ConfigKey.STARTING_FOLDER.toString()).asText()).toFile();
            testersFolder = Path.of(jsonNode.get(ConfigKey.TESTERS.toString()).asText()).toFile();
            scoresheetFile = Path.of(jsonNode.get(ConfigKey.SCORESHEET.toString()).asText()).toFile();

            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean save() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode jsonNode = objectMapper.createObjectNode();

        // Write into json object
        jsonNode.put(ConfigKey.STARTING_FOLDER.toString(), this.startingFolder.toPath().toAbsolutePath().toString());
        jsonNode.put(ConfigKey.TESTERS.toString(), this.testersFolder.toPath().toAbsolutePath().toString());
        jsonNode.put(ConfigKey.SCORESHEET.toString(), this.scoresheetFile.toPath().toAbsolutePath().toString());

        // Save into memory
        try (PrintStream ps = new PrintStream(DEF_CONFIG_FILE)) {
            ps.append(jsonNode.toPrettyString());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public File getStartingFolder() {
        return startingFolder;
    }

    public File getTestersFolder() {
        return testersFolder;
    }

    public File getScoresheetFile() {
        return scoresheetFile;
    }

    public void setStartingFolder(File startingFolder) {
        this.startingFolder = startingFolder;
    }

    public void setTestersFolder(File testersFolder) {
        this.testersFolder = testersFolder;
    }

    public void setScoresheetFile(File scoresheetFile) {
        this.scoresheetFile = scoresheetFile;
    }

}
