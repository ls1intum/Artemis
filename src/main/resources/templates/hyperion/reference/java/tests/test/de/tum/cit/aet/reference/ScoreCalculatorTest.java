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
class ScoreCalculatorTest {

    @Test
    @StrictTimeout(1)
    void testRepresentativeScores() {
        assertEquals(3, ScoreCalculator.countPassing(new int[] { 73, 18, 91, 50, 42 }), "representative scores should count every value at or above 50");
    }

    @Test
    @StrictTimeout(1)
    void testBoundaryScores() {
        assertEquals(2, ScoreCalculator.countPassing(new int[] { 49, 50, 50 }), "a score of exactly 50 should pass");
    }

    @Test
    @StrictTimeout(1)
    void testEmptyInput() {
        assertEquals(0, ScoreCalculator.countPassing(new int[0]), "an empty score list should have no passing scores");
    }
}
