package ${packageName};

import org.apache.commons.lang3.RandomUtils;

import java.text.*;
import java.util.*;

public class Client {

    // TODO: Implement BubbleSort
    // TODO: Implement MergeSort

    // TODO: Create a SortStrategy interface according to the UML class diagram
    // TODO: Make the sorting algorithms implement this interface.

    // TODO: Create and implement a Context class according to the UML class diagram
    // TODO: Create and implement a Policy class as described in the problem statement

    /**
     * Main method.
     * Add code to demonstrate your implementation here.
     */
    public static void main(String[] args) throws ParseException {

        // TODO: Init Context and Policy


        // Run 10 times to simulate different sorting strategies for different Array sizes
        for (int i = 0; i < 10; i++) {
            List<Date> dates = createRandomDatesList();

            // TODO: Configure context

            System.out.print("Unsorted Array of course dates = ");
            printDateList(dates);

            // TODO: Sort dates

            System.out.print("Sorted Array of course dates = ");
            printDateList(dates);
        }
    }

    /**
     * Generates a List of random Date objects with random List size between 5 and 15.
     */
    private static List<Date> createRandomDatesList() throws ParseException {
        int listLength = RandomUtils.nextInt(5, 15);
        List<Date> list = new ArrayList<>();

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
        Date lowestDate = dateFormat.parse("08.11.2016");
        Date highestDate = dateFormat.parse("15.04.2019");

        for (int i = 0; i < listLength; i++) {
            Date randomDate = randomDateWithin(lowestDate, highestDate);
            list.add(randomDate);
        }
        return list;
    }

    /**
     * Creates a random Date within given Range
     */
    private static Date randomDateWithin(Date low, Date high) {
        long randomLong = RandomUtils.nextLong(low.getTime(), high.getTime());
        return new Date(randomLong);
    }

    /**
     * Prints out given Array of Date objects
     */
    private static void printDateList(List<Date> list) {
        System.out.println(list.toString());
    }
}
