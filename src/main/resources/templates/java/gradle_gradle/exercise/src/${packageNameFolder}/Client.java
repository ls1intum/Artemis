package ${packageName};

import org.apache.commons.lang3.RandomUtils;

import java.text.*;
import java.util.*;

public final class Client {

    // TODO: Implement BubbleSort
    // TODO: Implement MergeSort

    // TODO: Create a SortStrategy interface according to the UML class diagram
    // TODO: Make the sorting algorithms implement this interface.

    // TODO: Create and implement a Context class according to the UML class diagram
    // TODO: Create and implement a Policy class as described in the problem statement

    private static final int ITERATIONS = 10;

    private static final int RANDOM_FLOOR = 5;

    private static final int RANDOM_CEILING = 15;

    private Client() {
    }

    /**
     * Main method.
     * Add code to demonstrate your implementation here.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) throws ParseException {

        // TODO: Init Context and Policy

        // Run multiple times to simulate different sorting strategies for different Array sizes
        for (int i = 0; i < ITERATIONS; i++) {
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
     * Generates a List of random Date objects with random List size between
     * {@link #RANDOM_FLOOR} and {@link #RANDOM_CEILING}.
     *
     * @return a List of random Date objects
     * @throws ParseException if date string cannot be parsed
     */
    private static List<Date> createRandomDatesList() throws ParseException {
        int listLength = RandomUtils.nextInt(RANDOM_FLOOR, RANDOM_CEILING);
        List<Date> list = new ArrayList<>();

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
        Date lowestDate = dateFormat.parse("08.11.2016");
        Date highestDate = dateFormat.parse("03.11.2020");

        for (int i = 0; i < listLength; i++) {
            Date randomDate = randomDateWithin(lowestDate, highestDate);
            list.add(randomDate);
        }
        return list;
    }

    /**
     * Creates a random Date within the given range.
     *
     * @param low the lower bound
     * @param high the upper bound
     * @return random Date within the given range
     */
    private static Date randomDateWithin(Date low, Date high) {
        long randomLong = RandomUtils.nextLong(low.getTime(), high.getTime());
        return new Date(randomLong);
    }

    /**
     * Prints out the given Array of Date objects.
     *
     * @param list of the dates to print
     */
    private static void printDateList(List<Date> list) {
        System.out.println(list.toString());
    }
}
