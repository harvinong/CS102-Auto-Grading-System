package autogradesys;

import autogradesys.display.GUI;

public class App {

    public static void main(String[] args) {
        System.out.println("Hello, World!");
        GUI myGui = new GUI("Auto-Grading System");
        myGui.run();        
    }

}
