package ${packageName};

import java.util.Date;
import java.util.List;
import java.util.ArrayList;

public class Context {
    private SortStrategy sortAlgorithm = new MergeSort();

    private List<Date> dates = new ArrayList<>();

    public List<Date> getDates() {
        return dates;
    }

    public void setDates(List<Date> dates) {
        this.dates = dates;
    }

    /**
     * Adds the given dates to the internal list of dates.
     *
     * @param datesToAdd The dates that are added.
     */
    public void addDates(List<Date> datesToAdd) {
        this.dates.addAll(datesToAdd);
    }

    /**
     * Removes all dates from the list of dates.
     */
    public void clearDates() {
        this.dates = new ArrayList<>();
    }

    public void setSortAlgorithm(SortStrategy sa) {
        sortAlgorithm = sa;
    }

    public SortStrategy getSortAlgorithm() {
        return sortAlgorithm;
    }

    /**
     * Runs the configured sort algorithm.
     */
    public void sort() {
        if (sortAlgorithm != null) {
            sortAlgorithm.performSort(this.dates);
        }
    }
}
