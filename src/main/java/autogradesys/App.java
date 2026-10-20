package autogradesys;

import java.util.Scanner;


public class App {

    public static void main(String[] args) {
        try (
            Scanner sc = new Scanner(System.in);
        ) {
            AutoGradeCtrl ctrl = new AutoGradeCtrl();
            AutoGradeMenu agMenu = new AutoGradeMenu(sc, ctrl);
            agMenu.readOption();
        }

    }

}
