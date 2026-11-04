package autogradesys.filesys;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.*;
import java.util.Scanner;

/**
 * 
 * FileUtil
 */
public class FileUtil {
    
    /**
     * Ask user for the filepath.
     * 
     * @param prompt The prompt that is printed in the terminal
     * @param reader The reader to reads the user input
     * @return The path the user has input in absolute form
     * @throws Exception If the path is not absolute
     */
    public static Path promptPath(String prompt, Scanner reader) throws Exception {
        System.out.print(prompt);
        String pathString = reader.nextLine();
        Path dirpath = Paths.get(pathString);

        if (!dirpath.isAbsolute()) {
            throw new Exception("The path must be absolute.");
        }

        return dirpath;
    }

    /**
     * Get the file/directory object from the path given
     * 
     * @param path The path of the file/directory
     * @return The file/directory object that exist in the file system.
     * @throws FileNotFoundException If the file/directory does not exist.
     */
    public static File getFile(Path path) throws FileNotFoundException {
        File file = path.toFile();

        if (!file.exists()) {
            throw new FileNotFoundException("File \"%s\" does not exist.".formatted(path.toString()));
        }

        return file;
    }

}
