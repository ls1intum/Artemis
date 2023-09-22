package de.test;

import java.util.Date;
import java.util.List;

public interface SortStrategy {
  
  /**
  * Sorts a list of Dates.
  *
  * @param input list of Dates
  */
  void performSort(List<Date> input);
}
