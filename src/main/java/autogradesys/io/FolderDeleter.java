package autogradesys.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
            paths = walk.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        }
        for (Path path : paths) {
            Files.delete(path);
        }
    }
}
