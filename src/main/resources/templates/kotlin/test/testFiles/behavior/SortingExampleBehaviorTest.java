package ${packageName};

import org.junit.jupiter.api.*;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static de.tum.cit.ase.ares.api.util.ReflectionTestUtils.*;

import de.tum.cit.ase.ares.api.StrictTimeout;
import de.tum.cit.ase.ares.api.jupiter.Public;

/**
 * @author Stephan Krusche
 * @version 5.1 (11.06.2021)
 */
@Public
// The fully qualified name is required, because the exercise itself contains a class named Policy
@de.tum.cit.ase.ares.api.Policy(value = "SecurityPolicy.yaml")
class SortingExampleBehaviorTest {

    private Context context;
    private Policy policy;
    private Method methodPolicyConfigure;

    /**
     * Creates the student objects. This is deliberately called from each test and not from a {@code @BeforeEach} method:
     * Ares only enforces the security policy while a test runs, so the constructors of the student code would run
     * unrestricted in a {@code @BeforeEach} method.
     */
    private void setup() {
        context = (Context) newInstance(Context.class.getName());
        policy = (Policy) newInstance(Policy.class.getName(), context);
        methodPolicyConfigure = getMethod(Policy.class, "configure", boolean.class, boolean.class);
    }

    @Test
    @StrictTimeout(1)
    void testMergeSort() {
        setup();
        invokeMethod(policy, methodPolicyConfigure, true, false);
        Object sortAlgorithm = invokeMethod(context, "getSortAlgorithm");
        assertTrue(sortAlgorithm instanceof MergeSort, "Expected MergeSort when time is important and space is not");
    }

    @Test
    @StrictTimeout(1)
    void testQuickSort() {
        setup();
        invokeMethod(policy, methodPolicyConfigure, true, true);
        Object sortAlgorithm = invokeMethod(context, "getSortAlgorithm");
        assertTrue(sortAlgorithm instanceof QuickSort, "Expected QuickSort when time and space are important");
    }

    @Test
    @StrictTimeout(1)
    void testSimulateRuntimeStrategyChoice() {
        setup();
        Client.INSTANCE.simulateRuntimeConfigurationChange(policy);
        Object sortAlgorithm = invokeMethod(context, "getSortAlgorithm");
        assertNotNull(sortAlgorithm, "Expected Client to simulate runtime configuration change");
    }
}
