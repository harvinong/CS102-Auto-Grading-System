/*
 * Name: Teo Chee
 * Email ID: chee.teo.2022
 */

import java.util.*;
import java.io.*;

public class Q2a {

    /*
     * Write the method getAverageAge
     */
    public static double getAverageAge(String filename, String surname) {
        // insert your code here
        try (Scanner sc = new Scanner(new File(filename));) {
            double average = 0.0;
            int total = 0;
            int count = 0;
            while (sc.hasNext()) {
                String row = sc.nextLine();
                Scanner lineSc = new Scanner(row);
                lineSc.useDelimiter("-");
                String name = lineSc.next();
                int age = lineSc.nextInt();
                String[] words = name.split(" ");
                for (String word : words) {
                    if (word.equals(surname.toUpperCase())){
                    total += age;
                    count ++;
                    }
                }
                lineSc.close();
            }
            if (count == 0){
                return 0.0;
            }
            average = total / count;
            return average;
        } catch (FileNotFoundException e) {
            return -1.0;
        }
        // to make this code compile. Please modify accordingly!
    }

    // Do not modify the following codes
    public static void main(String[] args) {
        int tcNum = 1;
        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, "persons.txt", "LEE");
            double expected = 36.0;
            double actual = getAverageAge("persons.txt", "LEE");
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual);
            if (expected == actual) {
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
            System.out.println("Actual   : " + actual);
            if (expected == actual) {
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
            System.out.println("Actual   : " + actual);
            if (expected == actual) {
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
            System.out.println("Actual   : " + actual);
            if (expected == actual) {
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
            System.out.println("Actual   : " + actual);
            if (expected == actual) {
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
            System.out.println("Actual   : " + actual);
            if (expected == actual) {
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }
    }
}