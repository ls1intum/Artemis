package ${packageName};

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;

public class MethodFunctionalTest extends FunctionalTest {

    private List<Date> dates;
    private List<Date> datesWithCorrectOrder;

    @Before
    public void setup() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
        Date date1 = dateFormat.parse("08.11.2018");
        Date date2 = dateFormat.parse("15.04.2017");
        Date date3 = dateFormat.parse("15.02.2016");
        Date date4 = dateFormat.parse("15.09.2017");

        this.dates = Arrays.asList(date1, date2, date3, date4);
        this.datesWithCorrectOrder = Arrays.asList(date3, date2, date4, date1);
    }

    @Test(timeout = 1000)
    public void testBubbleSort() {
        BubbleSort bubbleSort = (BubbleSort) newInstance("${packageName}.BubbleSort");
        bubbleSort.performSort(dates);
        assertEquals("Problem: BubbleSort does not sort correctly", datesWithCorrectOrder, dates);
    }

    @Test(timeout = 1000)
    public void testMergeSort() {
        MergeSort mergeSort = (MergeSort) newInstance("${packageName}.MergeSort");
        mergeSort.performSort(dates);
        assertEquals("Problem: MergeSort does not sort correctly", datesWithCorrectOrder, dates);
    }

    @Test(timeout = 1000)
    public void testUseMergeSortForBigList() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, InstantiationException, ClassNotFoundException {
        Date[] bigArray = new Date[] {new Date(), new Date(), new Date(), new Date(), new Date(), new Date(), new Date(), new Date(), new Date(), new Date(), new Date()};
        Object chosenSortStrategy = configurePolicyAndContext(Arrays.asList(bigArray));
        assertTrue("Problem: The sort algorithm of Context was not MergeSort for a list with more than 10 dates.", chosenSortStrategy instanceof MergeSort);
    }

    @Test(timeout = 1000)
    public void testUseBubbleSortForSmallList()  throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, InstantiationException, ClassNotFoundException {
        Date[] smallArray = new Date[] {new Date(), new Date(), new Date()};
        Object chosenSortStrategy = configurePolicyAndContext(Arrays.asList(smallArray));
        assertTrue("Problem: The sort algorithm of Context was not BubbleSort for a list with less or equal than 10 dates.", chosenSortStrategy instanceof BubbleSort);
    }

    private Object configurePolicyAndContext(List<Date> dates) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, InstantiationException, ClassNotFoundException {
        Class<?> contextClass = getClass("${packageName}.Context");
        assertNotNull("Problem: Context class is not created yet", contextClass);
                
        Object sortingContext = newInstance("${packageName}.Context");
        invokeMethod(sortingContext, getMethod(contextClass, "setDates", List.class), dates);

        Class<?> policyClass = getClass("${packageName}.Policy");
        assertNotNull("Problem: Policy class is not created yet", policyClass);
        
        Object policy = newInstance("${packageName}.Policy", sortingContext);
        invokeMethod(policy, getMethod(policyClass, "configure"));
        
        Object chosenSortStrategy = invokeMethod(sortingContext, getMethod(contextClass, "getSortAlgorithm"));

        return chosenSortStrategy;
    }
}
