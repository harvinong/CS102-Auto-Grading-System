package autogradesys.config;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Fixed defaults: the cache folder and the zip file-name pattern.
 * The menu's own settings are stored in config.json.
 */
public final class AppSettings {
    private static final Path DEFAULT_CACHE_DIR = Path.of("cache");
    private static final Pattern DEFAULT_ZIP_NAME_PATTERN = Pattern.compile(
            "^\\d{4}-\\d{4}-(.+)\\.zip$", Pattern.CASE_INSENSITIVE);
    private final Path cacheDir;
    private final Pattern zipNamePattern;
    /**
     * Stores the cache location and archive filename pattern used by the application.
     */
    public AppSettings(Path cacheDir, Pattern zipNamePattern) {
        this.cacheDir = cacheDir;
        this.zipNamePattern = zipNamePattern;
    }
    /**
     * Loads the persisted configuration or scoresheet data into this object.
     */
    public static AppSettings load() { return defaultSettings(); }
    /**
     * Returns the application settings used when no custom configuration is available.
     */
    public static AppSettings defaultSettings() { return new AppSettings(DEFAULT_CACHE_DIR, DEFAULT_ZIP_NAME_PATTERN); }
    /**
     * Returns the directory where reusable submission working copies are stored.
     */
    public Path getCacheDir() { return cacheDir; }
    /**
     * Returns the pattern used to recognize incoming submission archive names.
     */
    public Pattern getZipNamePattern() { return zipNamePattern; }
}
