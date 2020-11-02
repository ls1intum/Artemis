package ${packageName};

import java.security.SecureRandom;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class Client {

    private static final int ITERATIONS = 10;

    private static final int RANDOM_FLOOR = 5;

    private static final int RANDOM_CEILING = 15;

    private static final SecureRandom RANDOM = new SecureRandom();

    private Client() {
    }

    // TODO: Implement BubbleSort
    // TODO: Implement MergeSort

    // TODO: Create a SortStrategy interface according to the UML class diagram
    // TODO: Make the sorting algorithms implement this interface.

    // TODO: Create and implement a Context class according to the UML class diagram
    // TODO: Create and implement a Policy class as described in the problem statement

    /**
     * Main method.
     * Add code to demonstrate your implementation here.
     *
     * @param args command line arguments
     */
    public static void main(final String[] args) throws ParseException {

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
     * {@link #ANDOM_FLOOR} and {@link #RANDOM_CEILING}.
     *
     * @return a List of random Date objects
     * @throws ParserException if date string cannot be parsed
     */
    private static List<Date> createRandomDatesList() throws ParseException {
        int listLength = randomIntegerWithin(RANDOM_FLOOR, RANDOM_CEILING);
        List<Date> list = new ArrayList<>();

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
        Date lowestDate = dateFormat.parse("08.11.2016");
        Date highestDate = dateFormat.parse("26.10.2020");

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
        long randomLong = randomLongWithin(low.getTime(), high.getTime());
        return new Date(randomLong);
    }

    /**
     * Creates a random long within the given range.
     *
     * @param low the lower bound
     * @param high the upper bound
     * @return random long within the given range
     */
    private static long randomLongWithin(long low, long high) {
        return low + RANDOM.nextLong() * (high - low);
    }

    /**
     * Creates a random int within the given range.
     *
     * @param low the lower bound
     * @param high the upper bound
     * @return random int within the given range
     */
    private static int randomIntegerWithin(int low, int high) {
        return low + RANDOM.nextInt() * (high - low);
    }

    /**
     * Prints out the given Array of Date objects.
     *
     * @param list the dates to print
     */
    private static void printDateList(List<Date> list) {
        System.out.println(list.toString());
    }
}
