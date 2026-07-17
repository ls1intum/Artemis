package de.tum.cit.aet.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static de.tum.in.test.api.util.ReflectionTestUtils.getMethod;
import static de.tum.in.test.api.util.ReflectionTestUtils.invokeMethod;
import static de.tum.in.test.api.util.ReflectionTestUtils.newInstance;

import org.junit.jupiter.api.Test;

import de.tum.in.test.api.BlacklistPath;
import de.tum.in.test.api.StrictTimeout;
import de.tum.in.test.api.WhitelistPath;
import de.tum.in.test.api.jupiter.Public;

/**
 * {@code ShippingCalculator} is introduced entirely by the solution (it is absent from the template), so these tests reach it only through reflection: that way the same test
 * class still compiles against the template, where it fails at runtime instead of at compile time.
 */
@Public
@WhitelistPath("target")
@BlacklistPath("target/test-classes")
class ShippingCalculatorTest {

    @Test
    @StrictTimeout(1)
    void testSelectsExpressForHeavyPackages() throws ReflectiveOperationException {
        Object calculator = newInstance("de.tum.cit.aet.reference.ShippingCalculator");
        invokeMethod(calculator, getMethod(calculator, "selectStrategy", double.class), 12.0);
        Object strategy = invokeMethod(calculator, getMethod(calculator, "getStrategy"));
        assertTrue(strategy instanceof ExpressFeeStrategy, "a 12kg package should select the express strategy");
    }

    @Test
    @StrictTimeout(1)
    void testSelectsStandardForLightPackages() throws ReflectiveOperationException {
        Object calculator = newInstance("de.tum.cit.aet.reference.ShippingCalculator");
        invokeMethod(calculator, getMethod(calculator, "selectStrategy", double.class), 4.0);
        Object strategy = invokeMethod(calculator, getMethod(calculator, "getStrategy"));
        assertTrue(strategy instanceof StandardFeeStrategy, "a 4kg package should select the standard strategy");
    }

    @Test
    @StrictTimeout(1)
    void testComputeFeeDelegatesToChosenStrategy() throws ReflectiveOperationException {
        Object calculator = newInstance("de.tum.cit.aet.reference.ShippingCalculator");
        Object fee = invokeMethod(calculator, getMethod(calculator, "computeFee", double.class), 12.0);
        assertEquals(47.0, (double) fee, 1e-9, "a 12kg package should be charged the express rate plus surcharge");
    }
}
