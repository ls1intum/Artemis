package $

{packageName};

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Client {

    /**
     * Main method.
     * Add code to demonstrate your implementation here.
     */
    public static void main(String[] args) throws ParseException {

        // Init Context and Policy

        Context sortingContext = new Context();
        Policy policy = new Policy(sortingContext);

        // Run 10 times to simulate different sorting strategies
        for (int i = 0; i < 10; i++) {
            List<Date> dates = createRandomDatesList();

            sortingContext.setDates(dates);
            policy.configure();

            System.out.print("Unsorted Array of course dates = ");
            printDateList(dates);

            sortingContext.sort();

            System.out.print("Sorted Array of course dates = ");
            printDateList(dates);
        }
    }

    /**
     * Generates an Array of random Date objects with random Array size between 5 and 15.
     */
    private static List<Date> createRandomDatesList() throws ParseException {
        int listLength = randomIntegerWithin(5, 15);
        List<Date> list = new ArrayList<>();

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
        Date lowestDate = dateFormat.parse("08.11.2016");
        Date highestDate = dateFormat.parse("15.04.2017");

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
