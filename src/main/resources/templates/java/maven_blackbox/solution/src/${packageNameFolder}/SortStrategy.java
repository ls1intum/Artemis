package ${packageName};

import java.util.Date;
import java.util.List;

public interface SortStrategy {

    /**
     * Sorts a list of Dates.
     *
     * @param input The list of Dates to be sorted.
     */
    void performSort(List<Date> input);
}
