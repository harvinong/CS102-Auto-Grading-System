/*
 * Name: Fen Lai
 * Email ID: fen.lai.2022
 */

import java.util.*;
import java.io.*;

public class Q2b {

     /*
     * Write the method getTopStudent
     */
    public static String getTopStudent(String filename, String courseName) throws DataException{
        // insert your code here    
        String result = "";
        double highestGpa = 0.0;
        String name = "";

        try (Scanner sc = new Scanner(new File(filename));) {
            while (sc.hasNext()) {
                String row = sc.nextLine();
                Scanner lineScanner = new Scanner(row);

                lineScanner.useDelimiter(",");
                String fullName = lineScanner.next();
                String courses = lineScanner.next();

                String[] splitCourses = courses.split("-");

                double totalGpa = 0.0;

                for (String course: splitCourses) {
                    String[] courseGPA = course.split("#");
                    String courseCode = courseGPA[0];
                    String gpa = courseGPA[1];
                    if (courseCode.equals(courseName)) {
                        System.out.println(gpa + " : " + highestGpa);
                        if (Double.parseDouble(gpa) > highestGpa) {
                            highestGpa = Double.parseDouble(gpa);
                            name = fullName;
                        }
                    }
                }

            }
            // if (highestGpa==0.0) {
            //     throw new DataException();
            // } else {
                result += name + "-" + highestGpa;
                return result;
            // }
        } catch (FileNotFoundException e) {
            throw new DataException();
        } catch (Exception e) {
            throw new DataException();
        }
            // to make this code compile. Please modify accordingly!
    }

    //Do not modify the following codes
    public static void main(String[] args) {
        int tcNum = 1;
        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, "students.txt", "IS101");
            String expected = "John LEE-4.0";
            String actual = getTopStudent("students.txt", "IS101");
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected.equals(actual)){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }
        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, "students.txt", "IS102");
            String expected = "Mary CHAN-3.5";
            String actual = getTopStudent("students.txt", "IS102");
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected.equals(actual)){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            } System.out.println("-------------------------------------------------------");
        }
        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, "students.txt", "IS103");
            String expected = "CHAN Wei Jun-4.0";
            String actual = getTopStudent("students.txt", "IS103");
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual );
            if (expected.equals(actual)){
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }
        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, "students.txt", "IS104");
            try{
                String actual = getTopStudent("students.txt", "IS104");
                System.out.println("Failed -> Expecting a Data Exception");
            } catch (DataException ex){
                System.out.println("Passed");
            } catch (Exception e) {
                System.out.println("Failed -> Exception");
                e.printStackTrace();
            }
            System.out.println("-------------------------------------------------------");
        }

        {
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, "nosuchfile.txt", "IS101");
            try{
                String actual = getTopStudent("nosuchfile.txt", "IS101");
                System.out.println("Failed -> Expecting a Data Exception");
            } catch (DataException ex){
                System.out.println("Passed");
            } catch (Exception e) {
                System.out.println("Failed -> Exception");
                e.printStackTrace();
            }
            System.out.println("-------------------------------------------------------");
        }
    } 
}