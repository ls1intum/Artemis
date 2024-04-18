package de.tum.in.www1.artemis.versioning;

import static de.tum.in.www1.artemis.versioning.VersionRangeFactory.getInstanceOfVersionRange;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.exception.ApiVersionRangeNotValidException;

class VersionRangeFactoryTest {

    private final VersionRange range1_5 = getInstanceOfVersionRange(1, 5);

    private final VersionRange range3_5 = getInstanceOfVersionRange(3, 5);

    private final VersionRange range3_9 = getInstanceOfVersionRange(3, 9);

    private final VersionRange range4_9 = getInstanceOfVersionRange(4, 9);

    private final VersionRange range5_9 = getInstanceOfVersionRange(5, 9);

    private final VersionRange range6_9 = getInstanceOfVersionRange(6, 9);

    private final VersionRange range7_9 = getInstanceOfVersionRange(7, 9);

    private final VersionRange limit1 = getInstanceOfVersionRange(1);

    private final VersionRange limit2 = getInstanceOfVersionRange(2);

    private final VersionRange limit3 = getInstanceOfVersionRange(3);

    private final VersionRange limit5 = getInstanceOfVersionRange(5);

    private final VersionRange limit6 = getInstanceOfVersionRange(6);

    private final VersionRange limit7 = getInstanceOfVersionRange(7);

    @Test
    void testCombine_separateRanges() {
        assertThatThrownBy(() -> VersionRangeFactory.combine(range7_9, range3_5)).isInstanceOf(ApiVersionRangeNotValidException.class);
        assertThatThrownBy(() -> VersionRangeFactory.combine(range3_5, range7_9)).isInstanceOf(ApiVersionRangeNotValidException.class);
    }

    @Test
    void testCombine_equalRanges() {
        VersionRange range = VersionRangeFactory.combine(range3_5, range3_5);
        assertThatVersionRangesEqual(range, range3_5);
    }

    @Test
    void testCombine_neighboringRanges() {
        var actual1 = VersionRangeFactory.combine(range6_9, range3_5);
        var actual2 = VersionRangeFactory.combine(range3_5, range6_9);
        assertThatVersionRangesEqual(actual1, range3_9);
        assertThatVersionRangesEqual(actual2, range3_9);
    }

    @Test
    void testCombine_neighboringRanges_startEndSame() {
        var actual1 = VersionRangeFactory.combine(range5_9, range3_5);
        var actual2 = VersionRangeFactory.combine(range3_5, range5_9);
        assertThatVersionRangesEqual(actual1, range3_9);
        assertThatVersionRangesEqual(actual2, range3_9);
    }

    @Test
    void testCombine_overlappingRanges() {
        var actual1 = VersionRangeFactory.combine(range4_9, range3_5);
        var actual2 = VersionRangeFactory.combine(range3_5, range4_9);
        assertThatVersionRangesEqual(actual1, range3_9);
        assertThatVersionRangesEqual(actual2, range3_9);
    }

    @Test
    void testCombine_ranges_sameStart_differentEnd() {
        var actual1 = VersionRangeFactory.combine(range3_9, range3_5);
        var actual2 = VersionRangeFactory.combine(range3_5, range3_9);
        assertThatVersionRangesEqual(actual1, range3_9);
        assertThatVersionRangesEqual(actual2, range3_9);
    }

    @Test
    void testCombine_ranges_differentStart_sameEnd() {
        var actual1 = VersionRangeFactory.combine(range1_5, range3_5);
        var actual2 = VersionRangeFactory.combine(range3_5, range1_5);
        assertThatVersionRangesEqual(actual1, range1_5);
        assertThatVersionRangesEqual(actual2, range1_5);
    }

    @Test
    void testCombine_limitNeighboringRangeStart() {
        var actual1 = VersionRangeFactory.combine(limit2, range3_5);
        var actual2 = VersionRangeFactory.combine(range3_5, limit2);
        assertThatVersionRangesEqual(actual1, limit2);
        assertThatVersionRangesEqual(actual2, limit2);
    }

    @Test
    void testCombine_limitOnStartOfRange() {
        var actual1 = VersionRangeFactory.combine(limit3, range3_5);
        var actual2 = VersionRangeFactory.combine(range3_5, limit3);
        assertThatVersionRangesEqual(actual1, limit3);
        assertThatVersionRangesEqual(actual2, limit3);
    }

    @Test
    void testCombine_limitOnEndOfRange() {
        var actual1 = VersionRangeFactory.combine(limit5, range3_5);
        var actual2 = VersionRangeFactory.combine(range3_5, limit5);
        assertThatVersionRangesEqual(actual1, limit3);
        assertThatVersionRangesEqual(actual2, limit3);
    }

    @Test
    void testCombine_limitNeighboringRangeEnd() {
        var actual1 = VersionRangeFactory.combine(limit6, range3_5);
        var actual2 = VersionRangeFactory.combine(range3_5, limit6);
        assertThatVersionRangesEqual(actual1, limit3);
        assertThatVersionRangesEqual(actual2, limit3);
    }

    @Test
    void testCombine_limitBeforeRange() {
        var actual1 = VersionRangeFactory.combine(limit1, range3_5);
        var actual2 = VersionRangeFactory.combine(range3_5, limit1);
        assertThatVersionRangesEqual(actual1, limit1);
        assertThatVersionRangesEqual(actual2, limit1);
    }

    @Test
    void testCombine_limitAfterRange() {
        assertThatThrownBy(() -> VersionRangeFactory.combine(limit7, range3_5)).isInstanceOf(ApiVersionRangeNotValidException.class);
        assertThatThrownBy(() -> VersionRangeFactory.combine(range3_5, limit7)).isInstanceOf(ApiVersionRangeNotValidException.class);
    }

    @Test
    void testCombine_sameLimits() {
        var actual = VersionRangeFactory.combine(limit1, limit1);
        assertThatVersionRangesEqual(actual, limit1);
    }

    @Test
    void testCombine_differentLimits() {
        var actual1 = VersionRangeFactory.combine(limit1, limit2);
        var actual2 = VersionRangeFactory.combine(limit2, limit1);
        assertThatVersionRangesEqual(actual1, limit1);
        assertThatVersionRangesEqual(actual2, limit1);
    }

    private void assertThatVersionRangesEqual(VersionRange a, VersionRange b) {
        assertThat(a.start()).as("Check starting versions").isEqualTo(b.start());
        assertThat(a.end()).as("Check ending versions").isEqualTo(b.end());
    }
}
