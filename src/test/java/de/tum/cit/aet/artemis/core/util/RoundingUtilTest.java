package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RoundingUtil#equalsWithinEpsilon(double, double, double)}, which replaced the previously used
 * {@code org.apache.commons.math3.util.Precision#equals(double, double, double)}. These guard against a behavioral
 * regression in the inlined tolerance comparison used for grade-step and grading-weight matching.
 */
class RoundingUtilTest {

    @Test
    void exactlyEqualValuesAreEqual() {
        assertThat(RoundingUtil.equalsWithinEpsilon(1.5, 1.5, 0.01)).isTrue();
    }

    @Test
    void valuesWithinToleranceAreEqual() {
        assertThat(RoundingUtil.equalsWithinEpsilon(1.500, 1.505, 0.01)).isTrue();
        // symmetric
        assertThat(RoundingUtil.equalsWithinEpsilon(1.505, 1.500, 0.01)).isTrue();
    }

    @Test
    void valuesOutsideToleranceAreNotEqual() {
        assertThat(RoundingUtil.equalsWithinEpsilon(1.5, 1.52, 0.01)).isFalse();
    }

    @Test
    void zeroEpsilonRequiresExactEquality() {
        assertThat(RoundingUtil.equalsWithinEpsilon(2.0, 2.0, 0.0)).isTrue();
        assertThat(RoundingUtil.equalsWithinEpsilon(2.0, 2.0000001, 0.0)).isFalse();
    }

    @Test
    void worksWithVerySmallEpsilonLikeGradingWeights() {
        // mirrors the 1E-8 tolerance used in ProgrammingExerciseGradingService
        assertThat(RoundingUtil.equalsWithinEpsilon(0.0, 0.0, 1E-8)).isTrue();
        assertThat(RoundingUtil.equalsWithinEpsilon(0.0, 1E-9, 1E-8)).isTrue();
        assertThat(RoundingUtil.equalsWithinEpsilon(0.0, 1E-6, 1E-8)).isFalse();
    }

    @Test
    void handlesNegativeValues() {
        assertThat(RoundingUtil.equalsWithinEpsilon(-1.0, -1.005, 0.01)).isTrue();
        assertThat(RoundingUtil.equalsWithinEpsilon(-1.0, -1.05, 0.01)).isFalse();
    }

    @Test
    void handlesNaNAndInfinityLikePrecisionEquals() {
        // NaN is never equal to anything, matching Precision.equals
        assertThat(RoundingUtil.equalsWithinEpsilon(Double.NaN, Double.NaN, 0.01)).isFalse();
        assertThat(RoundingUtil.equalsWithinEpsilon(Double.NaN, 1.0, 0.01)).isFalse();
        // identical infinities compare equal via the exact-equality short-circuit
        assertThat(RoundingUtil.equalsWithinEpsilon(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0.01)).isTrue();
        assertThat(RoundingUtil.equalsWithinEpsilon(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.01)).isFalse();
    }
}
