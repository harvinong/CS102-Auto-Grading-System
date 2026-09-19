/*
 * Name: Fen Lai
 * Email ID: fen.lai.2022
 */

import java.util.*;
import java.io.*;

public class Q2a {

     /*
     * Write the method getAverageAge 
     */
    public static double getAverageAge(String filename, String surname) {
        // insert your code here
        double totalAge = 0.0;
        int peopleCount = 0;
        double averageAge = 0.0;

        try (Scanner sc = new Scanner(new File(filename));) {
            while (sc.hasNext()) {
                String row = sc.nextLine();
                Scanner lineScanner = new Scanner(row);

                lineScanner.useDelimiter("-");
                String fullName = lineScanner.next();
                int age = lineScanner.nextInt();

                String[] splitName = fullName.split(" ");
                if (splitName[0].toUpperCase().equals(surname.toUpperCase()) || splitName[splitName.length-1].toUpperCase().equals(surname.toUpperCase())) {
                    totalAge += age;
                    peopleCount += 1;
                }
            }
            if (peopleCount==0) {
                return 0.0;
            } else {
                averageAge = totalAge / peopleCount;
            }
            return averageAge;
        } catch (FileNotFoundException e) {
            return -1.0;
        } // to make this code compile. Please modify accordingly!
    }

    //Do not modify the following codes
    public static void main(String[] args) {
        int tcNum = 1;
        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, "persons.txt", "LEE");
            double expected = 36.0;
            double actual = getAverageAge("persons.txt", "LEE");
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected == actual){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }
        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, "persons.txt", "Lee");
            double expected = 36.0;
            double actual = getAverageAge("persons.txt", "Lee");
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected == actual){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }
        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, "persons.txt", "WONG");
            double expected = 25.0;
            double actual = getAverageAge("persons.txt", "WONG");
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected == actual){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }
        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, "persons.txt", "Chan");
            double expected = 27.0;
            double actual = getAverageAge("persons.txt", "Chan");
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected == actual){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }
        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, "persons.txt", "Ong");
            double expected = 0.0;
            double actual = getAverageAge("persons.txt", "Ong");
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected == actual){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }

       {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, "nosuchfile.txt", "Lee");
            double expected = -1.0;
            double actual = getAverageAge("nosuchfile.txt", "Lee");
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected == actual){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }
    } 
}