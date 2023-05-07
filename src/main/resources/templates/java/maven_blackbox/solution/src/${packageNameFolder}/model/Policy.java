package ${packageName}.model;

public class Policy {

    /**
     * @oracleIgnore
     */
    private static final int VALUES_SIZE_THRESHOLD = 10;

    private final Context context;

    public Policy(Context context) {
        this.context = context;
    }

    /**
     * Chooses a strategy depending on the number of Dates.
     */
    public void configure() {
        if (this.context.getDates().size() > VALUES_SIZE_THRESHOLD) {
            System.out.println("More than " + VALUES_SIZE_THRESHOLD
                    + " double values, choosing merge sort!");
            this.context.setSortAlgorithm(new MergeSort());
        } else {
            System.out.println("Less or equal than " + VALUES_SIZE_THRESHOLD
                    + " double values, choosing quick sort!");
            this.context.setSortAlgorithm(new BubbleSort());
        }
    }
}
