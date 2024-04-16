package de.tum.in.www1.artemis.versioning;

import static de.tum.in.www1.artemis.versioning.VersionRangeComparisonType.*;
import static de.tum.in.www1.artemis.versioning.VersionRangeFactory.getInstanceOfVersionRange;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VersioningComparatorTest {

    private final VersionRange range1_5 = getInstanceOfVersionRange(1, 5);

    private final VersionRange range2_5 = getInstanceOfVersionRange(2, 5);

    private final VersionRange range5_9 = getInstanceOfVersionRange(5, 9);

    private final VersionRange range1_6 = getInstanceOfVersionRange(1, 6);

    private final VersionRange range2_6 = getInstanceOfVersionRange(2, 6);

    private final VersionRange range6_10 = getInstanceOfVersionRange(6, 10);

    private final VersionRange range7_11 = getInstanceOfVersionRange(7, 11);

    private final VersionRange limit1 = getInstanceOfVersionRange(1);

    private final VersionRange limit5 = getInstanceOfVersionRange(5);

    private final VersionRange limit6 = getInstanceOfVersionRange(6);

    private final VersionRange limit7 = getInstanceOfVersionRange(7);

    @Test
    void testCompare_rangesEqual() {
        assertThat(VersionRangeComparator.compare(range1_5, range1_5)).isEqualTo(EQUALS);
        assertThat(VersionRangeComparator.compare(limit1, limit1)).isEqualTo(EQUALS);
    }

    @Test
    void testCompare_firstRangeIncludesSecond() {
        assertThat(VersionRangeComparator.compare(range1_6, range1_5)).isEqualTo(A_INCLUDES_B);
        assertThat(VersionRangeComparator.compare(range1_5, range2_5)).isEqualTo(A_INCLUDES_B);
        assertThat(VersionRangeComparator.compare(limit1, range1_5)).isEqualTo(A_INCLUDES_B);
        assertThat(VersionRangeComparator.compare(limit1, range2_5)).isEqualTo(A_INCLUDES_B);
        assertThat(VersionRangeComparator.compare(limit1, limit7)).isEqualTo(A_INCLUDES_B);
    }

    @Test
    void testCompare_secondRangeIncludesFirst() {
        assertThat(VersionRangeComparator.compare(range1_5, range1_6)).isEqualTo(B_INCLUDES_A);
        assertThat(VersionRangeComparator.compare(range2_5, range1_5)).isEqualTo(B_INCLUDES_A);
        assertThat(VersionRangeComparator.compare(range1_5, limit1)).isEqualTo(B_INCLUDES_A);
        assertThat(VersionRangeComparator.compare(range2_5, limit1)).isEqualTo(B_INCLUDES_A);
        assertThat(VersionRangeComparator.compare(limit7, limit1)).isEqualTo(B_INCLUDES_A);
    }

    @Test
    void testCompare_noIntersectSecondRangeStartsFirst() {
        assertThat(VersionRangeComparator.compare(range7_11, range1_5)).isEqualTo(FIRST_B_NO_INTERSECT);
        assertThat(VersionRangeComparator.compare(limit7, range1_5)).isEqualTo(FIRST_B_NO_INTERSECT);
    }

    @Test
    void testCompare_shiftSecondRangeStartsFirst() {
        assertThat(VersionRangeComparator.compare(range2_6, range1_5)).isEqualTo(B_CUT_A);
        assertThat(VersionRangeComparator.compare(range5_9, range1_5)).isEqualTo(B_CUT_A);
        assertThat(VersionRangeComparator.compare(limit5, range2_6)).isEqualTo(B_CUT_A);
        assertThat(VersionRangeComparator.compare(limit6, range2_6)).isEqualTo(B_CUT_A);
    }

    @Test
    void testCompare_shiftFirstRangeStartsFirst() {
        assertThat(VersionRangeComparator.compare(range1_5, range2_6)).isEqualTo(A_CUT_B);
        assertThat(VersionRangeComparator.compare(range1_5, range5_9)).isEqualTo(A_CUT_B);
        assertThat(VersionRangeComparator.compare(range2_6, limit5)).isEqualTo(A_CUT_B);
        assertThat(VersionRangeComparator.compare(range2_6, limit6)).isEqualTo(A_CUT_B);
    }

    @Test
    void testCompare_RangesFollowEachOther() {
        assertThat(VersionRangeComparator.compare(range1_5, range6_10)).isEqualTo(A_THEN_B);
        assertThat(VersionRangeComparator.compare(range6_10, range1_5)).isEqualTo(B_THEN_A);
    }

    @Test
    void testCompare_LimitFollowsRange() {
        assertThat(VersionRangeComparator.compare(range1_5, limit6)).isEqualTo(A_THEN_B);
        assertThat(VersionRangeComparator.compare(limit6, range1_5)).isEqualTo(B_THEN_A);
    }

    @Test
    void testCompare_noIntersectFirstRangeStartsFirst() {
        assertThat(VersionRangeComparator.compare(range1_5, range7_11)).isEqualTo(FIRST_A_NO_INTERSECT);
        assertThat(VersionRangeComparator.compare(range1_5, limit7)).isEqualTo(FIRST_A_NO_INTERSECT);
    }
}
