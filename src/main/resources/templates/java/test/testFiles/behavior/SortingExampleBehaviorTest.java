package ${packageName};

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.text.*;
import java.util.*;

import de.tum.in.test.api.BlacklistPath;
import de.tum.in.test.api.PathType;
import de.tum.in.test.api.StrictTimeout;
import de.tum.in.test.api.WhitelistPath;
import de.tum.in.test.api.behavior.BehaviorTest;
import de.tum.in.test.api.jupiter.Public;

/**
 * @author Stephan Krusche (krusche@in.tum.de)
 * @version 5.0 (11.11.2020)
 */
@WhitelistPath("target")
@BlacklistPath(value = "**Test*.{java,class}", type = PathType.GLOB)
@Public
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

    @Test
    @StrictTimeout(1)
    public void testBubbleSort() {
        BubbleSort bubbleSort = new BubbleSort();
        bubbleSort.performSort(dates);
        if (!datesWithCorrectOrder.equals(dates)) {
            fail("BubbleSort does not sort correctly");
        }
    }

    @Test
    @StrictTimeout(1)
    public void testMergeSort() {
        MergeSort mergeSort = new MergeSort();
        mergeSort.performSort(dates);
        if (!datesWithCorrectOrder.equals(dates)) {
            fail("MergeSort does not sort correctly");
        }
    }

    @Test
    @StrictTimeout(1)
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

    @Test
    @StrictTimeout(1)
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
