package de.tum.in.www1.artemis.versioning;

import static de.tum.in.www1.artemis.versioning.VersionRangeFactory.getInstanceOfVersionRange;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

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
        assertThrows(ApiVersionRangeNotValidException.class, () -> VersionRangeFactory.combine(range7_9, range3_5));
        assertThrows(ApiVersionRangeNotValidException.class, () -> VersionRangeFactory.combine(range3_5, range7_9));
    }

    @Test
    void testCombine_equalRanges() {
        List<Integer> actualRangesSameVersions = VersionRangeService.versionRangeToIntegerList(VersionRangeFactory.combine(range3_5, range3_5));
        assertThat(actualRangesSameVersions).isEqualTo(List.of(3, 5));
    }

    @Test
    void testCombine_neighboringRanges() {
        var actual1 = VersionRangeFactory.combine(range6_9, range3_5);
        var actual2 = VersionRangeFactory.combine(range3_5, range6_9);
        assertThat(actual1.value()).isEqualTo(range3_9.value());
        assertThat(actual2.value()).isEqualTo(range3_9.value());
    }

    @Test
    void testCombine_neighboringRanges_startEndSame() {
        var actual1 = VersionRangeFactory.combine(range5_9, range3_5);
        var actual2 = VersionRangeFactory.combine(range3_5, range5_9);
        assertThat(actual1.value()).isEqualTo(range3_9.value());
        assertThat(actual2.value()).isEqualTo(range3_9.value());
    }

    @Test
    void testCombine_overlappingRanges() {
        var actual1 = VersionRangeFactory.combine(range4_9, range3_5);
        var actual2 = VersionRangeFactory.combine(range3_5, range4_9);
        assertThat(actual1.value()).isEqualTo(range3_9.value());
        assertThat(actual2.value()).isEqualTo(range3_9.value());
    }

    @Test
    void testCombine_ranges_sameStart_differentEnd() {
        var actual1 = VersionRangeFactory.combine(range3_9, range3_5);
        var actual2 = VersionRangeFactory.combine(range3_5, range3_9);
        assertThat(actual1.value()).isEqualTo(range3_9.value());
        assertThat(actual2.value()).isEqualTo(range3_9.value());
    }

    @Test
    void testCombine_ranges_differentStart_sameEnd() {
        var actual1 = VersionRangeFactory.combine(range1_5, range3_5);
        var actual2 = VersionRangeFactory.combine(range3_5, range1_5);
        assertThat(actual1.value()).isEqualTo(range1_5.value());
        assertThat(actual2.value()).isEqualTo(range1_5.value());
    }

    @Test
    void testCombine_limitNeighboringRangeStart() {
        var actual1 = VersionRangeFactory.combine(limit2, range3_5);
        var actual2 = VersionRangeFactory.combine(range3_5, limit2);
        assertThat(actual1.value()).isEqualTo(limit2.value());
        assertThat(actual2.value()).isEqualTo(limit2.value());
    }

    @Test
    void testCombine_limitOnStartOfRange() {
        var actual1 = VersionRangeFactory.combine(limit3, range3_5);
        var actual2 = VersionRangeFactory.combine(range3_5, limit3);
        assertThat(actual1.value()).isEqualTo(limit3.value());
        assertThat(actual2.value()).isEqualTo(limit3.value());
    }

    @Test
    void testCombine_limitOnEndOfRange() {
        var actual1 = VersionRangeFactory.combine(limit5, range3_5);
        var actual2 = VersionRangeFactory.combine(range3_5, limit5);
        assertThat(actual1.value()).isEqualTo(limit3.value());
        assertThat(actual2.value()).isEqualTo(limit3.value());
    }

    @Test
    void testCombine_limitNeighboringRangeEnd() {
        var actual1 = VersionRangeFactory.combine(limit6, range3_5);
        var actual2 = VersionRangeFactory.combine(range3_5, limit6);
        assertThat(actual1.value()).isEqualTo(limit3.value());
        assertThat(actual2.value()).isEqualTo(limit3.value());
    }

    @Test
    void testCombine_limitBeforeRange() {
        var actual1 = VersionRangeFactory.combine(limit1, range3_5);
        var actual2 = VersionRangeFactory.combine(range3_5, limit1);
        assertThat(actual1.value()).isEqualTo(limit1.value());
        assertThat(actual2.value()).isEqualTo(limit1.value());
    }

    @Test
    void testCombine_limitAfterRange() {
        assertThrows(ApiVersionRangeNotValidException.class, () -> VersionRangeFactory.combine(limit7, range3_5));
        assertThrows(ApiVersionRangeNotValidException.class, () -> VersionRangeFactory.combine(range3_5, limit7));
    }

    @Test
    void testCombine_sameLimits() {
        var actual = VersionRangeFactory.combine(limit1, limit1);
        assertThat(actual.value()).isEqualTo(limit1.value());

    }

    @Test
    void testCombine_differentLimits() {
        var actual1 = VersionRangeFactory.combine(limit1, limit2);
        var actual2 = VersionRangeFactory.combine(limit2, limit1);
        assertThat(actual1.value()).isEqualTo(limit1.value());
        assertThat(actual2.value()).isEqualTo(limit1.value());
    }
}
