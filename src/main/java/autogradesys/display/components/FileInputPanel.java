package autogradesys.display.components;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import autogradesys.display.Field;
import autogradesys.display.GUI;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class FileInputPanel extends JPanel implements ActionListener {
    
    private FileInputField startingFolderInput;
    private FileInputField testersInput;
    private FileInputField scoresheetInput;

    public FileInputPanel(String title) {
        this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        this.setAlignmentX(JPanel.LEFT_ALIGNMENT);

        // Initialize
        JPanel titlePanel = new JPanel();
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(GUI.HEADER_FONT);
        titlePanel.add(titleLabel);
        
        
        this.startingFolderInput = new FileInputField(this, Field.STARTING_FOLDER);
        this.testersInput = new FileInputField(this, Field.TESTERS);
        this.scoresheetInput = new FileInputField(this, Field.SCORESHEET);

        // Insert widgets
        this.add(titlePanel);
        this.add(this.startingFolderInput);
        this.add(this.testersInput);
        this.add(this.scoresheetInput);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() instanceof FileInputField) {
            FileInputField fif = (FileInputField) e.getSource();
            System.out.printf(
                    "Receives \"%s\" from \"%s\" input field.%n",
                    fif.getSelectedFile().getName(), fif.getField().getName()
                );
        }
    }

}
