package ${packageName};

import java.util.Date;
import java.util.List;

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

	public void sort() {
		sortAlgorithm.performSort(this.dates);
	}
}
