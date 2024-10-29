package ${packageName};

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.text.*;
import java.util.*;

import static de.tum.in.test.api.util.ReflectionTestUtils.*;

import de.tum.in.test.api.BlacklistPath;
import de.tum.in.test.api.PathType;
import de.tum.in.test.api.StrictTimeout;
import de.tum.in.test.api.WhitelistPath;
import de.tum.in.test.api.jupiter.Public;

/**
 * @author Stephan Krusche (krusche@in.tum.de)
 * @version 5.1 (11.06.2021)
 */
@Public
@WhitelistPath("target") // mainly for Artemis
@BlacklistPath("target/test-classes") // prevent access to test-related classes and resources
class SortingExampleBehaviorTest {

    private List<Date> dates;
    private List<Date> datesWithCorrectOrder;

    @BeforeEach
    void setup() throws ParseException {
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
    void testBubbleSort() {
        BubbleSort bubbleSort = new BubbleSort();
        bubbleSort.performSort(dates);
        if (!datesWithCorrectOrder.equals(dates)) {
            fail("BubbleSort does not sort correctly");
        }
    }

    @Test
    @StrictTimeout(1)
    void testMergeSort() {
        MergeSort mergeSort = new MergeSort();
        mergeSort.performSort(dates);
        if (!datesWithCorrectOrder.equals(dates)) {
            fail("MergeSort does not sort correctly");
        }
    }

    @Test
    @StrictTimeout(1)
    void testUseMergeSortForBigList() throws ReflectiveOperationException {
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
    void testUseBubbleSortForSmallList()  throws ReflectiveOperationException {
        List<Date> smallList = new ArrayList<Date>();
        for (int i = 0; i < 3; i++) {
            smallList.add(new Date());
        }
        Object chosenSortStrategy = configurePolicyAndContext(smallList);
        if (!(chosenSortStrategy instanceof BubbleSort)) {
            fail("The sort algorithm of Context was not BubbleSort for a list with less or equal than 10 dates.");
        }
    }

    private Object configurePolicyAndContext(List<Date> dates) throws ReflectiveOperationException {
        Object context = newInstance("${packageName}.Context");
        invokeMethod(context, getMethod(context, "setDates", List.class), dates);

        Object policy = newInstance("${packageName}.Policy", context);
        invokeMethod(policy, getMethod(policy, "configure"));

        Object chosenSortStrategy = invokeMethod(context, getMethod(context, "getSortAlgorithm"));
        return chosenSortStrategy;
    }
}
