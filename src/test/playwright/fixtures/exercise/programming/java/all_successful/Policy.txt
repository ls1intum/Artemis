package de.test;

public class Policy {

  private static final int DATES_SIZE_THRESHOLD = 10;

  private Context context;

  public Policy(Context context) {
    this.context = context;
  }

  /**
  * Chooses a strategy depending on the number of date objects.
  */
  public void configure() {
    if (this.context.getDates().size() > DATES_SIZE_THRESHOLD) {
      System.out.println("More than " + DATES_SIZE_THRESHOLD + " dates, choosing merge sort!");
      this.context.setSortAlgorithm(new MergeSort());
    } else {
      System.out.println("Less or equal than " + DATES_SIZE_THRESHOLD + " dates. choosing quick sort!");
      this.context.setSortAlgorithm(new BubbleSort());
    }
  }
}
