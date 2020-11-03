package ${packageName};

import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.lang.reflect.InvocationTargetException;
import java.text.*;
import java.util.*;

import org.junit.jupiter.api.*;

/**
 * @author Stephan Krusche (krusche@in.tum.de)
 * @version 4.0 (27.10.2020)
 */
public class SortingExampleBehaviorTest extends BehaviorTest {

    private List<Date> dates;
    private List<Date> datesWithCorrectOrder;

    @BeforeEach
    public void setup() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
        Date date1 = dateFormat.parse("08.11.2018");
        Date date2 = dateFormat.parse("15.04.2017");
        Date date3 = dateFormat.parse("15.02.2016");
        Date date4 = dateFormat.parse("15.09.2017");

        this.dates = Arrays.asList(date1, date2, date3, date4);
        this.datesWithCorrectOrder = Arrays.asList(date3, date2, date4, date1);
    }

    @Timeout(1)
    @Test
    public void testBubbleSort() {
        BubbleSort bubbleSort = new BubbleSort();
        bubbleSort.performSort(dates);
        if (!datesWithCorrectOrder.equals(dates)) {
            fail("BubbleSort does not sort correctly");
        }
    }

    @Timeout(1)
    @Test
    public void testMergeSort() {
        MergeSort mergeSort = new MergeSort();
        mergeSort.performSort(dates);
        if (!datesWithCorrectOrder.equals(dates)) {
            fail("MergeSort does not sort correctly");
        }
    }

    @Timeout(1)
    @Test
    public void testUseMergeSortForBigList() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, InstantiationException, ClassNotFoundException {
        List<Date> bigList = new ArrayList<Date>();
        for (int i = 0; i < 11; i++) {
            bigList.add(new Date());
        }
        Object chosenSortStrategy = configurePolicyAndContext(bigList);
        if (!(chosenSortStrategy instanceof MergeSort)) {
            fail("The sort algorithm of Context was not MergeSort for a list with more than 10 dates.");
        }
    }

    @Timeout(1)
    @Test
    public void testUseBubbleSortForSmallList()  throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, InstantiationException, ClassNotFoundException {
        List<Date> smallList = new ArrayList<Date>();
        for (int i = 0; i < 3; i++) {
            smallList.add(new Date());
        }
        Object chosenSortStrategy = configurePolicyAndContext(smallList);
        if (!(chosenSortStrategy instanceof BubbleSort)) {
            fail("The sort algorithm of Context was not BubbleSort for a list with less or equal than 10 dates.");
        }
    }

    private Object configurePolicyAndContext(List<Date> dates) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, InstantiationException, ClassNotFoundException {

        Object context = newInstance("${packageName}.Context");
        invokeMethod(context, getMethod(context, "setDates", List.class), dates);

        Object policy = newInstance("${packageName}.Policy", context);
        invokeMethod(policy, getMethod(policy, "configure"));

        Object chosenSortStrategy = invokeMethod(context, getMethod(context, "getSortAlgorithm"));
        return chosenSortStrategy;
    }
}
