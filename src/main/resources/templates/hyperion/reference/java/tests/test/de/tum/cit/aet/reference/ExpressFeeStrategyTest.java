package de.tum.cit.aet.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.tum.in.test.api.BlacklistPath;
import de.tum.in.test.api.StrictTimeout;
import de.tum.in.test.api.WhitelistPath;
import de.tum.in.test.api.jupiter.Public;

@Public
@WhitelistPath("target")
@BlacklistPath("target/test-classes")
class ExpressFeeStrategyTest {

    @Test
    @StrictTimeout(1)
    void testExpressFeeTypical() {
        assertEquals(47.0, new ExpressFeeStrategy().calculateFee(12.0), 1e-9, "a 12kg package should be charged the express rate plus surcharge");
    }

    @Test
    @StrictTimeout(1)
    void testExpressFeeMinimumSurcharge() {
        assertEquals(5.0, new ExpressFeeStrategy().calculateFee(0.0), 1e-9, "a weightless express package should still be charged the flat surcharge");
    }
}
