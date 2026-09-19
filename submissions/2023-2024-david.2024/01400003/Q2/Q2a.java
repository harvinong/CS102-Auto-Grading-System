/*
 * Name: David
 * Email ID:david.2024
 */

import java.util.*;
import java.io.*;

public class Q2a {

     /*
     * Write the method getAverageAge 
     */
    public static double getAverageAge(String filename, String surname) {
        // insert your code here
        double results = 0;
        Scanner fileSc = null;
        BufferedReader reader = null;

        try {                
            fileSc = new Scanner(new FileInputStream(filename));
            reader = new BufferedReader(new FileReader(filename));
            
        } catch (FileNotFoundException e) {
            System.out.println(filename + " is invalid");
        }
        int people = 0;
        int sum = 0;
        if(reader == null){
            return -1.0;
        }        
        try {
            String line;
            while ((line = reader.readLine())!= null) {
                String[] arry = line.split(" ");
                Boolean checker = false;
                for(String item : arry){
                    if(item.contains("-"))
                    {
                        String[] arry2 = item.split("-");
                        for(String item2 : arry2)
                        {
                            if (item2.toLowerCase().equals(surname.toLowerCase())){
                                checker = true;
                            }
                        }
                    }
                    else{
                    if (item.toLowerCase().equals(surname.toLowerCase())){
                        checker = true;
                    }
                }
                }
                if (checker){
                    String name = line;
                    int age = Integer.valueOf(name.substring(name.length()-2));
                    sum += age;
                    people += 1;
                }

            }
            System.out.println("The sum is " + sum);
        } catch (IOException e) {
            System.out.println(filename + " is invalid");
        }
        finally {
            if (fileSc != null) {
                fileSc.close();
            }
            
        }
        if(people != 0){
        results = sum / people;
        }

        return results; // to make this code compile. Please modify accordingly!
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