package autogradesys.config;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;


/**
 * 
 * Config
 */
public class Config {

    public static final File DEF_CONFIG_FILE = Path.of(".", "config.json").toFile();
    private static Config singleton = null;
    
    // Configuration parameters
    private File startingFolderPath = null;
    private File testersPath = null;
    private File scoresheetPath = null;

    private Config(File startingFolderPath, File testersPath, File scoresheetPath) {
        this.startingFolderPath = startingFolderPath;
        this.testersPath = testersPath;
        this.scoresheetPath = scoresheetPath;
    }

    public static Config get(File startingFolderPath, File testersPath, File scoresheetPath) {
        if (singleton == null) {
            singleton = new Config(startingFolderPath, testersPath, scoresheetPath);
        }
        singleton.startingFolderPath = startingFolderPath;
        singleton.testersPath = testersPath;
        singleton.scoresheetPath = scoresheetPath;
        return singleton;
    }

    public File getStartingFolderPath() {
        return startingFolderPath;
    }

    public File getTestersPath() {
        return testersPath;
    }

    public File getScoresheetPath() {
        return scoresheetPath;
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
            Path startingFolderPath = Path.of(jsonNode.get(ConfigKey.STARTING_FOLDER.toString()).asText());
            Path testersPath = Path.of(jsonNode.get(ConfigKey.TESTERS.toString()).asText());
            Path scoresheetPath = Path.of(jsonNode.get(ConfigKey.SCORESHEET.toString()).asText());

            // return Singleton
            return get(startingFolderPath.toFile(), testersPath.toFile(), scoresheetPath.toFile());
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
        jsonNode.put(ConfigKey.STARTING_FOLDER.toString(), this.startingFolderPath.toString());
        jsonNode.put(ConfigKey.TESTERS.toString(), this.testersPath.toString());
        jsonNode.put(ConfigKey.SCORESHEET.toString(), this.scoresheetPath.toString());

        // Save into memory
        try (PrintStream ps = new PrintStream(configFile)) {
            ps.append(jsonNode.toPrettyString());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

}
