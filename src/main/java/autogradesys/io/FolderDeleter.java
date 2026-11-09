package autogradesys.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Deletes a folder together with everything inside it. */
public final class FolderDeleter {
    private FolderDeleter() {
    }

    public static void delete(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }

        List<Path> paths;

        try (Stream<Path> walk = Files.walk(folder)) {
            paths = walk.sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());
        }

        for (Path path : paths) {

            // Remove Windows read-only attribute before deletion
            DosFileAttributeView dosView =
                    Files.getFileAttributeView(path, DosFileAttributeView.class);

            if (dosView != null) {
                dosView.setReadOnly(false);
            }

            // Delete file or directory
            Files.delete(path);
        }
    }
}
