package autogradesys.lib;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Stream;
public class unpacker {

    //getAllZip() is entry function
    
    //just a checker if current file is a zip
    /**
     * Checks whether a file really is a zip by reading its first four bytes, not by its name.
     *
     * @param f the file to test
     * @return true if the file starts with a zip signature; false if it does not, or if it cannot be read
     */
    private static boolean isZip(File f){
        int fileSig = 0;
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")){
            fileSig = raf.readInt();
        } catch (IOException e) {

        }
        return fileSig == 0x504B0304 || fileSig == 0x504B0506 || fileSig == 0x504B0708;
    }
    // gets all zipped files of submission final arraylist will not include anything that is not a zip
    /**
     * Finds the zip files in the {@code student-submission} folder under the current working directory.
     * The folder path is built with Windows backslashes.
     *
     * @return the full path of each zip file found; empty if the folder is missing or holds no zip
     */
    public static ArrayList<String> getAllZip() {
        ArrayList<String> nameOfAllFile = new ArrayList<>();
        String tmp = System.getProperty("user.dir") + "\\student-submission";  
        File subFolder = new File(tmp); 
        File[] allSub = subFolder.listFiles();
        if (allSub != null) {
            for (File stuSub : allSub) {
                if (isZip(stuSub)){
                    nameOfAllFile.add(tmp + "\\" + stuSub.getName());
                }
            }
        }
        return nameOfAllFile;
        
    }
    //unpacks the zip file and creates a folder with the zip's name
    // can do error checking here for like fucked up zip or smth
    /**
     * Extracts a zip into a sibling folder named after it (the name without {@code .zip}), overwriting
     * files that already exist there. Problems are printed rather than thrown, so a bad entry does not stop the rest.
     *
     * @param srcPath the path to the zip file; if it does not end in {@code .zip} the same text is used as the folder name
     */
    public static void unpack(String srcPath){
        try {
            Path zipPath = Path.of(srcPath);
            String folderName = srcPath.endsWith(".zip") 
                ? srcPath.substring(0, srcPath.length() - 4)
                : srcPath;
            Path destDir = zipPath.toAbsolutePath().getParent().resolve(folderName);
            Files.createDirectories(destDir);
            try (FileSystem zipFs = FileSystems.newFileSystem(zipPath, Map.of())) {
                for (Path root : zipFs.getRootDirectories()) {
                    try (Stream<Path> entries = Files.walk(root)) {
                        entries.forEach(source -> {
                            try {
                                Path relative = root.relativize(source);
                                
                                Path target = destDir.resolve(relative.toString());

                                if (Files.isDirectory(source)) {
                                    Files.createDirectories(target);
                                } else {
                                    if (target.getParent() != null) {
                                        Files.createDirectories(target.getParent());
                                    }
                                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                    }
                }
            }
        } catch (IOException e) {
            
            System.out.println("error occured");
            e.printStackTrace();
            
            
        }
    }

  
}
