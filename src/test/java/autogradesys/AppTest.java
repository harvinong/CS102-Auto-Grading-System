package autogradesys;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
/**
 * Unit test for simple App.
 */
public class AppTest {
    
    private static void unpacker(String filePath, String destPath) {
        File f = new File(destPath);
        if (!f.exists()) f.mkdir();
        FileInputStream fs;
        byte[] buf = new byte[1024];
        try {
            fs = new FileInputStream(filePath);
            ZipInputStream zs = new ZipInputStream(fs);
            ZipEntry ze = zs.getNextEntry();
            while (ze != null) {
                String filename = ze.getName();
                File newFile = new  File(destPath + File.separator + filename);
                new File(newFile.getParent()).mkdirs();
                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zs.read(buf)) > 0) {
                    fos.write(buf, 0 , len);

                }
                fos.close();
                zs.close();
                ze = zs.getNextEntry();
            }
            zs.closeEntry();
            zs.close();
            fs.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /**
     * Rigorous Test :-)
     * 
     */
    @Test
    public void shouldAnswerWithTrue() {
        assertTrue(true);
    }
    public static void main(String[] args) {
        unpacker("C:\\Users\\mista\\Desktop\\serverproj\\Project\\student-submission\\2023-2024-chee.teo.2022.zip",new File("").getAbsolutePath().concat("cheeteo"));   
    }
}
