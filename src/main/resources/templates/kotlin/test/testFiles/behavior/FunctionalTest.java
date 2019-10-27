package ${packageName};

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FunctionalTest extends BehaviorTest {

    private Context context;
    private Policy policy;
    private Method methodPolicyConfigure;

    @Before
    public void setup() {
        context = (Context) newInstance(Context.class.getName());
        policy = (Policy) newInstance(Policy.class.getName(), context);
        methodPolicyConfigure = getMethod(Policy.class, "configure", boolean.class, boolean.class);
    }

    @Test(timeout = 1000)
    public void testMergeSort() {
        invokeMethod(policy, methodPolicyConfigure, true, false);
        Object sortAlgorithm = invokeMethod(context, "getSortAlgorithm");
        assertTrue("Expected MergeSort when time is important and space is not",
                   sortAlgorithm instanceof MergeSort);
    }

    @Test(timeout = 1000)
    public void testQuickSort() {
        invokeMethod(policy, methodPolicyConfigure, true, true);
        Object sortAlgorithm = invokeMethod(context, "getSortAlgorithm");
        assertTrue("Expected QuickSort when time and space are important",
                   sortAlgorithm instanceof QuickSort);
    }

    @Test(timeout = 1000)
    public void testSimulateRuntimeStrategyChoice() {
        Client.INSTANCE.simulateRuntimeConfigurationChange(policy);
        Object sortAlgorithm = invokeMethod(context, "getSortAlgorithm");
        assertNotNull("Expected Client to simulate runtime configuration change",
                      sortAlgorithm);
    }

}
