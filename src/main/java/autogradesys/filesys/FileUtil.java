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
    // Used by the menu whenever it needs a folder or file from the user.
    public static Path promptPath(String prompt, Scanner reader) throws Exception {
        // Show the question without a newline, so the answer is typed on the same line.
        System.out.print(prompt);
        // Read the user's entire line, including spaces in the path.
        String pathString = reader.nextLine();
        // Convert the text to a Path; this does not check that the path exists.
        Path dirpath = Paths.get(pathString);

        // Relative paths would depend on where the program was launched, so they are refused.
        if (!dirpath.isAbsolute()) {
            // The caller catches this and tells the user to try again.
            throw new Exception("The path must be absolute.");
        }

        // Valid absolute path, existence not yet checked.
        return dirpath;
    }

    /**
     * Get the file/directory object from the path given
     * 
     * @param path The path of the file/directory
     * @return The file/directory object that exist in the file system.
     * @throws FileNotFoundException If the file/directory does not exist.
     */
    // Converts a Path to a File only if something exists there.
    public static File getFile(Path path) throws FileNotFoundException {
        // Legacy File object, which the rest of the code expects.
        File file = path.toFile();

        // Nothing at that location.
        if (!file.exists()) {
            // Include the path in the message so the user can spot a typo.
            throw new FileNotFoundException("File \"%s\" does not exist.".formatted(path.toString()));
        }

        // Exists, so hand it back.
        return file;
    }

}
