package autogradesys.display;
import javax.swing.JFrame;

import autogradesys.display.components.FileInputPanel;

import java.awt.Font;

public class GUI extends JFrame {

    // Constants
    public static final Font HEADER_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 24);
    public static final Font FORM_LABEL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 16);
    public static final Font FORM_CONTROL_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 16);

    public GUI(String title) {
        // Initialize window
        super();
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setTitle(title);

        // Make widgets
        FileInputPanel fip = new FileInputPanel("Add Default Templates");
        this.add(fip);
    }

    public void run() {
        // Show window
        this.pack();
        this.setVisible(true);
    }

}
