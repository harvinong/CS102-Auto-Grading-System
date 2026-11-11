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
     * Reports whether a filename has a ZIP extension.
     * @param f the argument supplied to this operation.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
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
     * Returns the ZIP files discovered in the configured source directory.
     * @return the result of this operation; an empty, absent, or false result indicates that no matching value or successful update was available.
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
     * Extracts selected ZIP entries while preventing paths from escaping the destination.
     * @param srcPath the argument supplied to this operation.
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
