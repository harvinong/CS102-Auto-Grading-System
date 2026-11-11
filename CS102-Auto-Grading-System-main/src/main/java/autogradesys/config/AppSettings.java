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
    public AppSettings(Path cacheDir, Pattern zipNamePattern) {
        this.cacheDir = cacheDir;
        this.zipNamePattern = zipNamePattern;
    }
    public static AppSettings load() { return defaultSettings(); }
    public static AppSettings defaultSettings() { return new AppSettings(DEFAULT_CACHE_DIR, DEFAULT_ZIP_NAME_PATTERN); }
    public Path getCacheDir() { return cacheDir; }
    public Pattern getZipNamePattern() { return zipNamePattern; }
}
