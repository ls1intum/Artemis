package ${packageName};

import java.util.Collections;
import java.util.Date;
import java.util.List;

public class BubbleSort implements SortStrategy {

	public void performSort(List<Date> input) {

		for (int i = input.size() - 1; i >= 0; i--) {
			for (int j = 0; j < i; j++) {
				if (input.get(j).compareTo(input.get(j + 1)) > 0) {
					Date temp = input.get(j);
					input.set(j, input.get(j + 1));
					input.set(j + 1, temp);
				}
			}
		}
	}
}
