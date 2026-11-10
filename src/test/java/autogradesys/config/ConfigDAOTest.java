package autogradesys.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks the grading timeout and output limit that config.json carries next to the three paths. */
class ConfigDAOTest {

    // Fresh temp folder per test, so the developer's ./config.json is never read or written.
    @TempDir
    Path root;

    // Writes a config.json with the three required paths plus any extra JSON members (e.g. the limits), and returns its location.
    private File configWith(String extraMembers) throws IOException {
        Path file = root.resolve("config.json");
        Files.writeString(file, "{\"starting_folder_path\":\"/a\",\"testers_path\":\"/b\",\"scoresheet_path\":\"/c.csv\""
                + extraMembers + "}");
        return file.toFile();
    }

    @Test
    void configWithoutLimitKeysKeepsTheOldDefaults() throws IOException {
        // An existing config.json written before the limits existed.
        ConfigDAO dao = new ConfigDAO(configWith(""));

        // Loading still succeeds and the limits are the previous hard-coded 2 seconds and 1 MiB.
        assertTrue(dao.load());
        assertEquals(2, dao.getTimeoutSeconds());
        assertEquals(1_048_576L, dao.getOutputLimitBytes());
    }

    @Test
    void limitsFromConfigAreUsed() throws IOException {
        ConfigDAO dao = new ConfigDAO(configWith(",\"grading_timeout_seconds\":7,\"output_limit_bytes\":4096"));

        assertTrue(dao.load());
        assertEquals(7, dao.getTimeoutSeconds());
        assertEquals(4096, dao.getOutputLimitBytes());
    }

    @Test
    void invalidLimitsInTheFileFallBackToDefaultsInsteadOfFailingTheLoad() throws IOException {
        // Zero, negative, text, a decimal and a value far above both maximums are all unusable as limits.
        for (String bad : new String[] { "0", "-5", "\"ten\"", "1.5", "null", "9999999999999999" }) {
            ConfigDAO dao = new ConfigDAO(configWith(",\"grading_timeout_seconds\":" + bad
                    + ",\"output_limit_bytes\":" + bad));

            // The paths still load, the limits are the defaults.
            assertTrue(dao.load(), bad);
            assertEquals(ConfigDAO.DEFAULT_TIMEOUT_SECONDS, dao.getTimeoutSeconds(), bad);
            assertEquals(ConfigDAO.DEFAULT_OUTPUT_LIMIT_BYTES, dao.getOutputLimitBytes(), bad);
        }
    }

    @Test
    void newDaoStartsOnTheDefaults() {
        ConfigDAO dao = new ConfigDAO(root.resolve("missing.json").toFile());

        // No file yet: load reports false but the limits are already usable for a first-run user.
        assertFalse(dao.load());
        assertEquals(2, dao.getTimeoutSeconds());
        assertEquals(1_048_576L, dao.getOutputLimitBytes());
    }

    @Test
    void savedLimitsRoundTrip() {
        File file = root.resolve("config.json").toFile();
        ConfigDAO writer = new ConfigDAO(file);
        writer.setStartingFolder(root.toFile());
        writer.setTestersFile(root.toFile());
        writer.setScoresheetFile(root.resolve("s.csv").toFile());
        writer.setTimeoutSeconds(9);
        writer.setOutputLimitBytes(2048);
        assertTrue(writer.save());

        // A second DAO reading the same file sees the saved values.
        ConfigDAO reader = new ConfigDAO(file);
        assertTrue(reader.load());
        assertEquals(9, reader.getTimeoutSeconds());
        assertEquals(2048, reader.getOutputLimitBytes());
    }

    @Test
    void settersRejectNonPositiveValues() {
        ConfigDAO dao = new ConfigDAO(root.resolve("config.json").toFile());

        assertThrows(IllegalArgumentException.class, () -> dao.setTimeoutSeconds(0));
        assertThrows(IllegalArgumentException.class, () -> dao.setTimeoutSeconds(-1));
        assertThrows(IllegalArgumentException.class, () -> dao.setOutputLimitBytes(0));
        assertThrows(IllegalArgumentException.class, () -> dao.setOutputLimitBytes(-1));
        // One above each maximum is refused too, while the maximums themselves are accepted further down.
        assertThrows(IllegalArgumentException.class, () -> dao.setTimeoutSeconds(ConfigDAO.MAX_TIMEOUT_SECONDS + 1));
        assertThrows(IllegalArgumentException.class,
                () -> dao.setOutputLimitBytes(ConfigDAO.MAX_OUTPUT_LIMIT_BYTES + 1));
        // A rejected value must not overwrite the previous one.
        assertEquals(2, dao.getTimeoutSeconds());
        assertEquals(1_048_576L, dao.getOutputLimitBytes());
        // The largest allowed values are kept.
        dao.setTimeoutSeconds(ConfigDAO.MAX_TIMEOUT_SECONDS);
        dao.setOutputLimitBytes(ConfigDAO.MAX_OUTPUT_LIMIT_BYTES);
        assertEquals(ConfigDAO.MAX_TIMEOUT_SECONDS, dao.getTimeoutSeconds());
        assertEquals(ConfigDAO.MAX_OUTPUT_LIMIT_BYTES, dao.getOutputLimitBytes());
    }
}
