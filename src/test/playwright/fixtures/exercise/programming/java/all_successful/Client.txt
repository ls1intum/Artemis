package de.test;

import java.text.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class Client {
  
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
    
    Context sortingContext = new Context();
    Policy policy = new Policy(sortingContext);
    
    for (int i = 0; i < ITERATIONS; i++) {
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
  * Generates a List of random Date objects with random List size between
  * {@link #RANDOM_FLOOR} and {@link #RANDOM_CEILING}.
  *
  * @return a List of random Date objects
  * @throws ParserException if date string cannot be parsed
  */
  private static List<Date> createRandomDatesList() throws ParseException {
    int listLength = randomIntegerWithin(RANDOM_FLOOR, RANDOM_CEILING);
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
    return ThreadLocalRandom.current().nextLong(low, high + 1);
  }
  
  /**
  * Creates a random int within the given range.
  *
  * @param low the lower bound
  * @param high the upper bound
  * @return random int within the given range
  */
  private static int randomIntegerWithin(int low, int high) {
    return ThreadLocalRandom.current().nextInt(low, high + 1);
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
