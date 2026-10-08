package autogradesys.display.components;

import java.awt.event.ActionListener;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.Arrays;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.filechooser.FileFilter;

import autogradesys.display.Field;
import autogradesys.display.GUI;

class FIFFilter extends FileFilter {

    private Field field;

    public FIFFilter(Field field) {
        this.field = field;
    }

    @Override
    public boolean accept(File f) {
        // Directory Check
        if (f.isDirectory()) {
            return true;
        }

        // File Extension Check
        return Arrays.asList(field.getExtensions()).contains(getExtension(f));
    }

    @Override
    public String getDescription() {
        return field.getDescription();
    }

    private static String getExtension(File f) {
        // Ref:
        // https://www.geeksforgeeks.org/java/how-to-get-the-file-extension-in-java/
        String fileName = f.getName();
        int dotIndex = fileName.lastIndexOf('.');

        // if there is no dot or ends with a dot
        if (dotIndex == -1 || dotIndex == fileName.length() - 1) {
            return ""; // No file extension found
        } else {
            return fileName.substring(dotIndex + 1);
        }
    }

}

public class FileInputField extends JPanel {

    // Reactive Variable
    private File selectedFile = null;
    private FileFilter fileFilter;
    private JLabel formControl;
    private JButton formButton;

    // Param variable
    private ActionListener root;
    private Field field;

    public FileInputField(ActionListener root, Field field) {
        // Set variables
        this.root = root;
        this.field = field;
        this.fileFilter = new FIFFilter(field);

        // Initialize
        JLabel formLabel = new JLabel(field.getName());
        formLabel.setFont(GUI.FORM_LABEL_FONT);

        this.formControl = new JLabel();
        this.formControl.setFont(GUI.FORM_CONTROL_FONT);
        this.formControl.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        this.formControl.setVisible(false);

        this.formButton = new JButton("Choose " + (field.isDir() ? "Folder" : "File"));
        this.formButton.setFont(GUI.FORM_LABEL_FONT);
        this.formButton.setFocusable(false);
        this.formButton.addActionListener(e -> {
            selectFile(e);
        });

        // Pack
        this.add(formLabel);
        this.add(formControl);
        this.add(formButton);
        System.out.println("Initialized: " + field.getName());
    }

    private void selectFile(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.setFileFilter(this.fileFilter);
        int status;
        if (field.isDir()) {
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            status = fileChooser.showDialog(null, "Select Folder");
        } else {
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            status = fileChooser.showDialog(null, "Select File");
        }
        if (status == JFileChooser.APPROVE_OPTION) {
            // Set variables
            selectedFile = fileChooser.getSelectedFile();

            // Set UI
            this.formControl.setText(" " + selectedFile.getName() + " ");
            this.formControl.setVisible(true);

            // Send Event
            e.setSource(this);
            this.root.actionPerformed(e);
        }
    }

    public File getSelectedFile() {
        return this.selectedFile;
    }

    public Field getField() {
        return this.field;
    }

}
