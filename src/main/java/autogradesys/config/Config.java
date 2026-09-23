package autogradesys.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;



public class Config {

    public static final File DEF_CONFIG_FILE = Path.of(".", "config.json").toFile();
    
    // Configuration parameters
    private File submissionsPath;
    private File testersPath;
    private File scoresheetPath;

    public Config(File submissionsPath, File testersPath, File scoresheetPath) {
        this.submissionsPath = submissionsPath;
        this.testersPath = testersPath;
        this.scoresheetPath = scoresheetPath;
    }

    public File getSubmissionsPath() {
        return submissionsPath;
    }

    public File getTestersPath() {
        return testersPath;
    }

    public File getScoresheetPath() {
        return scoresheetPath;
    }    

    public static boolean configInitialized() {
        return DEF_CONFIG_FILE.exists() && loadConfig() != null;
    }

    public static Config loadConfig() {
        return loadConfig(DEF_CONFIG_FILE);
    }

    public static Config loadConfig(File configFile) {
        try {
            // Ref: https://www.geeksforgeeks.org/java/how-to-read-and-write-json-files-in-java/
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(DEF_CONFIG_FILE);
            
            // Load values
            Path submissionsPath = Path.of(jsonNode.get(ConfigKey.SUBMISSIONS.toString()).asText());
            Path testersPath = Path.of(jsonNode.get(ConfigKey.TESTERS.toString()).asText());
            Path scoresheetPath = Path.of(jsonNode.get(ConfigKey.SCORESHEET.toString()).asText());

            return new Config(submissionsPath.toFile(), testersPath.toFile(), scoresheetPath.toFile());
        } catch (IOException e) {
            return null;
        }
    }

    public boolean saveConfig() {
        return saveConfig(DEF_CONFIG_FILE);
    }

    public boolean saveConfig(File configFile) {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode jsonNode = objectMapper.createObjectNode();
        
        // Write into json object
        jsonNode.put(ConfigKey.SUBMISSIONS.toString(), this.submissionsPath.toString());
        jsonNode.put(ConfigKey.TESTERS.toString(), this.testersPath.toString());
        jsonNode.put(ConfigKey.SCORESHEET.toString(), this.scoresheetPath.toString());

        // Save into memory
        try {
            objectMapper.writeValue(configFile, jsonNode);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

}
