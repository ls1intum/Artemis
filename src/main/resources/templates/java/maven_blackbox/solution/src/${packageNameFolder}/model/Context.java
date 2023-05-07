package ${packageName}.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Context {
    private SortStrategy sortAlgorithm = new MergeSort();

    private final List<Date> dates = new ArrayList<>();

    public List<Date> getDates() {
        return dates;
    }

    public void addValues(List<Date> values) {
        this.dates.addAll(values);
    }

    public void setSortAlgorithm(SortStrategy sa) {
        sortAlgorithm = sa;
    }

    public SortStrategy getSortAlgorithm() {
        return sortAlgorithm;
    }

    public void clearValues() {
        this.dates.clear();
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
