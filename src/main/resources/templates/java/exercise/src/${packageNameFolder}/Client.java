package $

{packageName};

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

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
        int listLength = randomIntegerWithin(5, 15);
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
        long randomLong = randomLongWithin(low.getTime(), high.getTime());
        return new Date(randomLong);
    }

    /**
     * Creates a random Long within given Range
     */
    private static long randomLongWithin(long low, long high) {
        return low + (long) (Math.random() * (high - low));
    }

    /**
     * Creates a random Integer within given Range
     */
    private static int randomIntegerWithin(int low, int high) {
        return low + (int) (Math.random() * (high - low));
    }

    /**
     * Prints out given Array of Date objects
     */
    private static void printDateList(List<Date> list) {
        System.out.println(list.toString());
    }
}
