package ${packageName};

import org.junit.jupiter.api.*;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FunctionalTest extends BehaviorTest {

    private Context context;
    private Policy policy;
    private Method methodPolicyConfigure;

    @BeforeEach
    public void setup() {
        context = (Context) newInstance(Context.class.getName());
        policy = (Policy) newInstance(Policy.class.getName(), context);
        methodPolicyConfigure = getMethod(Policy.class, "configure", boolean.class, boolean.class);
    }

    @Timeout(1)
    @Test
    public void testMergeSort() {
        invokeMethod(policy, methodPolicyConfigure, true, false);
        Object sortAlgorithm = invokeMethod(context, "getSortAlgorithm");
        assertTrue(sortAlgorithm instanceof MergeSort, "Expected MergeSort when time is important and space is not");
    }

    @Timeout(1)
    @Test
    public void testQuickSort() {
        invokeMethod(policy, methodPolicyConfigure, true, true);
        Object sortAlgorithm = invokeMethod(context, "getSortAlgorithm");
        assertTrue(sortAlgorithm instanceof QuickSort, "Expected QuickSort when time and space are important");
    }

    @Timeout(1)
    @Test
    public void testSimulateRuntimeStrategyChoice() {
        Client.INSTANCE.simulateRuntimeConfigurationChange(policy);
        Object sortAlgorithm = invokeMethod(context, "getSortAlgorithm");
        assertNotNull(sortAlgorithm, "Expected Client to simulate runtime configuration change");
    }
}
