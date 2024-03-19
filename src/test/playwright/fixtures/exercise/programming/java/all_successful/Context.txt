package de.test;

import java.util.*;

public class Context {
  private SortStrategy sortAlgorithm;
  
  private List<Date> dates;
  
  public List<Date> getDates() {
    return dates;
  }
  
  public void setDates(List<Date> dates) {
    this.dates = dates;
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
