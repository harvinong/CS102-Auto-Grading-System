package autogradesys.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Pattern;

/** Settings from autograde.properties: the cache folder and the pattern for zip file names. */
public class AppSettings {
    private static final String FILE = "/autograde.properties";
    private static final String DEFAULT_CACHE_DIR = "cache";
    private static final String DEFAULT_ZIP_NAME_PATTERN = "^\\d{4}-\\d{4}-(.+)\\.zip$";

    private final Path cacheDir;
    private final Pattern zipNamePattern;

    public AppSettings(Path cacheDir, Pattern zipNamePattern) {
        this.cacheDir = cacheDir;
        this.zipNamePattern = zipNamePattern;
    }

    public static AppSettings load() {
        Properties properties = new Properties();
        try (InputStream in = AppSettings.class.getResourceAsStream(FILE)) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
        }
        return fromProperties(properties);
    }

    public static AppSettings defaultSettings() {
        return fromProperties(new Properties());
    }

    private static AppSettings fromProperties(Properties properties) {
        String cacheDir = properties.getProperty("cache.dir", DEFAULT_CACHE_DIR);
        String pattern = properties.getProperty("zip.name.pattern", DEFAULT_ZIP_NAME_PATTERN);
        return new AppSettings(Path.of(cacheDir), Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
    }

    public Path getCacheDir() {
        return cacheDir;
    }

    public Pattern getZipNamePattern() {
        return zipNamePattern;
    }
}
