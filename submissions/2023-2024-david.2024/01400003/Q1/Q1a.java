/*
 * Name: David
 * Email ID:david.2024
 */

import java.util.*;

public class Q1a {

    /*
     * Write the method getIsogramWords
     */
    public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
        // insert your code here.
        ArrayList<String> results_arr = new ArrayList<>();
        for (String word : inputs){
            ArrayList<Character> temp_arr = new ArrayList<>();
            String lower_word = word.toLowerCase();
            for(int i = 0; i < word.length(); i++){
                if (temp_arr.isEmpty()){
                    temp_arr.add(lower_word.charAt(i));
                }
                else if (!temp_arr.contains(lower_word.charAt(i))){
                    temp_arr.add(lower_word.charAt(i));
                }
            }

            if(word.length() == temp_arr.size()){
                results_arr.add(word);
            }
        }

        return results_arr; // to make this code compile. Please modify accordingly!
    }


    // Do not modify the following codes
    public static void main(String[] args) {
        int tcNum = 1;
        {
            ArrayList<String> inputs = new ArrayList<>();
            inputs = new ArrayList<>();
            inputs.add("cat");
            inputs.add("dog");
            inputs.add("tiger");

            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getIsogramWords(%s)%n", tcNum++, inputs);
            String expected = "[cat, dog, tiger]";
            ArrayList<String> actual = getIsogramWords(inputs);
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual);
            if (expected.equals(actual.toString())) {
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }

        {
            ArrayList<String> inputs = new ArrayList<>();
            inputs = new ArrayList<>();
            inputs.add("alan");
            inputs.add("ben");
            inputs.add("cindy");
            inputs.add("danny");
            inputs.add("evan");
            
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getIsogramWords(%s)%n", tcNum++, inputs);
            String expected = "[ben, cindy, evan]";
            ArrayList<String> actual = getIsogramWords(inputs);
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual);
            if (expected.equals(actual.toString())) {
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }

        {
            ArrayList<String> inputs = new ArrayList<>();
            inputs = new ArrayList<>();
            inputs.add("Alan");
            inputs.add("Ben");
            inputs.add("Cindy");
            inputs.add("Danny");
            inputs.add("Evelyne");
            
            System.out.println("-------------------------------------------------------");
            System.out.printf("Test %d: getIsogramWords(%s)%n", tcNum++, inputs);
            String expected = "[Ben, Cindy]";
            ArrayList<String> actual = getIsogramWords(inputs);
            System.out.println("Expected : " + expected);
            System.out.println("Actual   : " + actual);
            if (expected.equals(actual.toString())) {
                System.out.println("Passed");
            } else {
                System.out.println("Failed");
            }
            System.out.println("-------------------------------------------------------");
        }
    }
}