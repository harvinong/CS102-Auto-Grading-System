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
import java.util.zip.ZipFile;
public class unpacker {

    //getAllZip() is entry function
    
    //just a checker if current file is a zip
    private static boolean isZip(File f){
        int fileSig = 0;
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")){
            fileSig = raf.readInt();
        } catch (IOException e) {

        }
        return fileSig == 0x504B0304 || fileSig == 0x504B0506 || fileSig == 0x504B0708;
    }
    // gets all zipped files of submission final arraylist will not include anything that is not a zip
    public static ArrayList<String> getAllZip() {
        ArrayList<String> nameOfAllFile = new ArrayList<>();
        String p = System.getProperty("user.dir");
        String c =new File("").getAbsolutePath();
        System.out.println(p);
        String tmp = p + "\\student-submission";
        File subFolder = new File(tmp); //you would put path to student-submission here instead
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
    //unpacks the zip file and returns a folder with the zip's name
    public static void unpack(String srcPath){
        try {
            ZipFile zipFile = new ZipFile(srcPath);
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
