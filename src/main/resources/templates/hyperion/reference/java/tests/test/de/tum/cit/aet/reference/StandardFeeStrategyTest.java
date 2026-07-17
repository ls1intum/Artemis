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
class StandardFeeStrategyTest {

    @Test
    @StrictTimeout(1)
    void testStandardFeeTypical() {
        assertEquals(10.0, new StandardFeeStrategy().calculateFee(5.0), 1e-9, "a 5kg package should cost 5 times the per-kilogram rate");
    }

    @Test
    @StrictTimeout(1)
    void testStandardFeeZeroWeight() {
        assertEquals(0.0, new StandardFeeStrategy().calculateFee(0.0), 1e-9, "a weightless package should cost nothing");
    }
}
