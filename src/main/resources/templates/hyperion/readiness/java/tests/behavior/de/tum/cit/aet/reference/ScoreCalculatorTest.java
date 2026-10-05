package de.tum.cit.aet.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.tum.cit.ase.ares.api.StrictTimeout;
import de.tum.cit.ase.ares.api.Policy;
import de.tum.cit.ase.ares.api.jupiter.Public;

@Public
@Policy(value = "SecurityPolicy.yaml")
class ScoreCalculatorTest {

    @Test
    @StrictTimeout(1)
    void testRepresentativeScores() {
        assertEquals(3, ScoreCalculator.countPassing(new int[] { 73, 18, 91, 50, 42 }));
    }

    @Test
    @StrictTimeout(1)
    void testBoundaryScores() {
        assertEquals(2, ScoreCalculator.countPassing(new int[] { 49, 50, 50 }));
    }

    @Test
    @StrictTimeout(1)
    void testEmptyInput() {
        assertEquals(0, ScoreCalculator.countPassing(new int[0]));
    }
}
