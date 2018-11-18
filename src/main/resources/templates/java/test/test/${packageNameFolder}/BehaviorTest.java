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

public class BehaviorTest {

    private List<Date> dates;
    private List<Date> datesWithCorrectOrder;

    @Before
    public void setup() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
        Date date1 =  dateFormat.parse("08.11.2018");
        Date date2 =  dateFormat.parse("15.04.2017");
        Date date3 =  dateFormat.parse("15.02.2016");
        Date date4 =  dateFormat.parse("15.09.2017");

        this.dates = Arrays.asList(date1, date2, date3, date4);
        this.datesWithCorrectOrder = Arrays.asList(date3, date2, date4, date1);
    }

    @Test(timeout = 1000)
    public void testBubbleSort() {
        BubbleSort bubbleSort = new BubbleSort();
        bubbleSort.performSort(dates);
        assertEquals("Problem: BubbleSort does not sort correctly", datesWithCorrectOrder, dates);
    }

    @Test(timeout = 1000)
    public void testMergeSort() {
        MergeSort mergeSort = new MergeSort();
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
        Class contextClass = Class.forName("${packageName}.Context");
        assertNotNull("Problem: Context class is not created yet", contextClass);
        Object sortingContext = contextClass.newInstance();
        Method setDatesMethod = contextClass.getMethod("setDates", List.class);
        assertNotNull("Problem: Context.setDates() not created yet", setDatesMethod);
        setDatesMethod.invoke(sortingContext, dates);

        Class policyClass = Class.forName("${packageName}.Policy");
        assertNotNull("Problem: Policy class is not created yet", policyClass);
        Constructor policyConstructor = policyClass.getConstructor(contextClass);
        assertNotNull("Problem: Policy class has not the right constructor", policyConstructor);

        Object policy = policyConstructor.newInstance(sortingContext);
        Method configureMethod = policyClass.getMethod("configure");
        assertNotNull("Problem: Policy.configure() not created yet", configureMethod);
        configureMethod.invoke(policy);

        Method getSortAlgorithmMethod = contextClass.getMethod("getSortAlgorithm");
        assertNotNull("Problem: Context.getSortAlgorithm() not created yet", getSortAlgorithmMethod);
        Object chosenSortStrategy = getSortAlgorithmMethod.invoke(sortingContext);

        return chosenSortStrategy;
    }
}
