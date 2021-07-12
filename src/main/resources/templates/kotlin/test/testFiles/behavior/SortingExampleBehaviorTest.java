package ${packageName};

import org.junit.jupiter.api.*;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static de.tum.in.test.api.util.ReflectionTestUtils.*;

import de.tum.in.test.api.AddTrustedPackage;
import de.tum.in.test.api.BlacklistPath;
import de.tum.in.test.api.AddTrustedPackage;
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
@AddTrustedPackage("kotlin.**")
class SortingExampleBehaviorTest {

    private Context context;
    private Policy policy;
    private Method methodPolicyConfigure;

    @BeforeEach
    void setup() {
        context = (Context) newInstance(Context.class.getName());
        policy = (Policy) newInstance(Policy.class.getName(), context);
        methodPolicyConfigure = getMethod(Policy.class, "configure", boolean.class, boolean.class);
    }

    @Test
    @StrictTimeout(1)
    void testMergeSort() {
        invokeMethod(policy, methodPolicyConfigure, true, false);
        Object sortAlgorithm = invokeMethod(context, "getSortAlgorithm");
        assertTrue(sortAlgorithm instanceof MergeSort, "Expected MergeSort when time is important and space is not");
    }

    @Test
    @StrictTimeout(1)
    void testQuickSort() {
        invokeMethod(policy, methodPolicyConfigure, true, true);
        Object sortAlgorithm = invokeMethod(context, "getSortAlgorithm");
        assertTrue(sortAlgorithm instanceof QuickSort, "Expected QuickSort when time and space are important");
    }

    @Test
    @StrictTimeout(1)
    void testSimulateRuntimeStrategyChoice() {
        Client.INSTANCE.simulateRuntimeConfigurationChange(policy);
        Object sortAlgorithm = invokeMethod(context, "getSortAlgorithm");
        assertNotNull(sortAlgorithm, "Expected Client to simulate runtime configuration change");
    }
}
