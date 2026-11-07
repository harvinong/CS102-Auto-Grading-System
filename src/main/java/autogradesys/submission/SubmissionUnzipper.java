package autogradesys.submission;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Unzips one student's zip file into a folder, and refuses files that would end up outside it. */
public final class SubmissionUnzipper {
    private SubmissionUnzipper() {
    }

    public static void unzip(Path zipFile, Path targetFolder) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.contains("__MACOSX/") || name.endsWith(".DS_Store")) {
                    continue;
                }

                Path out = targetFolder.resolve(name).normalize();
                if (!out.startsWith(targetFolder)) {
                    throw new IOException("Unsafe path in zip: " + name);
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (InputStream in = zip.getInputStream(entry)) {
                        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }
}
