package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class VariantGroupCappedSumTest {

    private static final double PRECISION = 1e-9;

    @Test
    void shouldSumContributionsOfExercisesWithoutAVariantGroup() {
        var sum = new VariantGroupCappedSum();

        sum.add(null, null, 5.0);
        sum.add(null, null, 3.0);

        assertThat(sum.total()).isCloseTo(8.0, within(PRECISION));
        assertThat(sum.uncappedTotal()).isCloseTo(8.0, within(PRECISION));
    }

    @Test
    void shouldCapAGroupWhoseVariantsExceedItsMaxPoints() {
        var sum = new VariantGroupCappedSum();

        // Three variants of the same group worth 30 points together, but the group is capped at 10.
        sum.add(1L, 10.0, 10.0);
        sum.add(1L, 10.0, 10.0);
        sum.add(1L, 10.0, 10.0);

        assertThat(sum.total()).isCloseTo(10.0, within(PRECISION));
    }

    @Test
    void shouldNotInflateAGroupThatStaysBelowItsCap() {
        var sum = new VariantGroupCappedSum();

        // The cap is a ceiling, not a floor: a group under it contributes what it actually earned.
        sum.add(1L, 10.0, 4.0);

        assertThat(sum.total()).isCloseTo(4.0, within(PRECISION));
    }

    @Test
    void shouldCapEachGroupIndependentlyAndAddUngroupedPointsOnTop() {
        var sum = new VariantGroupCappedSum();

        sum.add(1L, 10.0, 8.0);
        sum.add(1L, 10.0, 8.0);  // group 1: 16 -> capped to 10
        sum.add(2L, 5.0, 2.0);
        sum.add(2L, 5.0, 1.0);   // group 2: 3 -> under its cap of 5
        sum.add(null, null, 7.0); // ungrouped

        assertThat(sum.total()).isCloseTo(10.0 + 3.0 + 7.0, within(PRECISION));
    }

    @Test
    void shouldTreatAGroupWithoutACapAsUngrouped() {
        var sum = new VariantGroupCappedSum();

        // A group that configures no maxPoints must not cap anything — its variants count in full.
        sum.add(1L, null, 12.0);
        sum.add(1L, null, 9.0);

        assertThat(sum.total()).isCloseTo(21.0, within(PRECISION));
        assertThat(sum.uncappedTotal()).isCloseTo(21.0, within(PRECISION));
    }

    @Test
    void shouldIgnoreCapsInTheUncappedTotal() {
        var sum = new VariantGroupCappedSum();

        sum.add(1L, 10.0, 10.0);
        sum.add(1L, 10.0, 10.0);
        sum.add(null, null, 4.0);

        assertThat(sum.total()).isCloseTo(10.0 + 4.0, within(PRECISION));
        assertThat(sum.uncappedTotal()).isCloseTo(20.0 + 4.0, within(PRECISION));
    }

    @Test
    void shouldReturnZeroWhenNothingWasAdded() {
        var sum = new VariantGroupCappedSum();

        assertThat(sum.total()).isCloseTo(0.0, within(PRECISION));
        assertThat(sum.uncappedTotal()).isCloseTo(0.0, within(PRECISION));
    }
}
