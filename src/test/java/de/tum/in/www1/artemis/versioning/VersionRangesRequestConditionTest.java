package de.tum.in.www1.artemis.versioning;

import static de.tum.in.www1.artemis.versioning.VersionRangeFactory.getInstanceOfVersionRange;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class VersionRangesRequestConditionTest {

    private final List<Integer> testApiVersions = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);

    private static final DummyRequest DUMMY_REQUEST = new DummyRequest();

    @Test
    void testInitEdgeCases() {
        VersionRangesRequestCondition emptyCondition = new VersionRangesRequestCondition(testApiVersions);
        assertThat(emptyCondition.getRanges()).isEmpty();
    }

    @Test
    void testGetMatchingCondition_emptyList() {
        VersionRangesRequestCondition condition = new VersionRangesRequestCondition(testApiVersions);
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
    }

    @Test
    void testGetMatchingCondition_limits() {
        List<VersionRange> list = Arrays.asList(getInstanceOfVersionRange(2), getInstanceOfVersionRange(3), getInstanceOfVersionRange(4));
        VersionRangesRequestCondition condition = new VersionRangesRequestCondition(testApiVersions, list);

        DUMMY_REQUEST.setRequestURI("/api/v1/test");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isNull();
        DUMMY_REQUEST.setRequestURI("/api/v2/test");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
        DUMMY_REQUEST.setRequestURI("/api/v3/test");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
        DUMMY_REQUEST.setRequestURI("/api/v4/test");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
        DUMMY_REQUEST.setRequestURI("/api/test");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
    }

    @Test
    void testGetMatchingConditionRanges() {
        List<VersionRange> list = Arrays.asList(getInstanceOfVersionRange(2, 2), getInstanceOfVersionRange(4, 4));
        VersionRangesRequestCondition condition = new VersionRangesRequestCondition(testApiVersions, list);

        DUMMY_REQUEST.setRequestURI("/api/v1/test");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isNull();
        DUMMY_REQUEST.setRequestURI("/api/v2/test");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
        DUMMY_REQUEST.setRequestURI("/api/v3/test");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isNull();
        DUMMY_REQUEST.setRequestURI("/api/v4/test");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
        DUMMY_REQUEST.setRequestURI("/api/v5/test");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isNull();
        DUMMY_REQUEST.setRequestURI("/api/test");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isNull();
    }

    @Test
    void testGetMatchingConditionLimitAndRange() {
        List<VersionRange> list = Arrays.asList(getInstanceOfVersionRange(2), getInstanceOfVersionRange(4, 4));
        VersionRangesRequestCondition condition = new VersionRangesRequestCondition(testApiVersions, list);

        DUMMY_REQUEST.setRequestURI("/api/v1/test");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isNull();
        DUMMY_REQUEST.setRequestURI("/api/v2/test");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
        DUMMY_REQUEST.setRequestURI("/api/v3/test");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
        DUMMY_REQUEST.setRequestURI("/api/v4/test");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
        DUMMY_REQUEST.setRequestURI("/api/v5/test");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
        DUMMY_REQUEST.setRequestURI("/api/test");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);

        list = Arrays.asList(getInstanceOfVersionRange(2, 2), getInstanceOfVersionRange(4));
        condition = new VersionRangesRequestCondition(testApiVersions, list);

        DUMMY_REQUEST.setRequestURI("/api/v1/test");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isNull();
        DUMMY_REQUEST.setRequestURI("/api/v2/test");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
        DUMMY_REQUEST.setRequestURI("/api/v3/test");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isNull();
        DUMMY_REQUEST.setRequestURI("/api/v4/test");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
        DUMMY_REQUEST.setRequestURI("/api/v5/test");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
        DUMMY_REQUEST.setRequestURI("/api/test");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
    }

    @Test
    void testGetApplicableVersions_empty() {
        var condition = new VersionRangesRequestCondition(testApiVersions);
        assertThat(condition.getApplicableVersions()).isEqualTo(testApiVersions);
    }

    @Test
    void testGetApplicableVersions_singleLimit() {
        var condition = new VersionRangesRequestCondition(testApiVersions, getInstanceOfVersionRange(5));
        assertThat(condition.getApplicableVersions()).containsExactly(5, 6, 7, 8, 9, 10, 11, 12);
    }

    @Test
    void testGetApplicableVersions_singleRange() {
        var condition = new VersionRangesRequestCondition(testApiVersions, getInstanceOfVersionRange(5, 7));
        assertThat(condition.getApplicableVersions()).containsExactly(5, 6, 7);
    }

    @Test
    void testGetApplicableVersions_multipleRanges() {
        var condition = new VersionRangesRequestCondition(testApiVersions, getInstanceOfVersionRange(5, 7), getInstanceOfVersionRange(9, 11));
        assertThat(condition.getApplicableVersions()).containsExactly(5, 6, 7, 9, 10, 11);
    }

    @Test
    void testGetApplicableVersions_multipleLimits() {
        var condition = new VersionRangesRequestCondition(testApiVersions, getInstanceOfVersionRange(5), getInstanceOfVersionRange(9));
        assertThat(condition.getApplicableVersions()).containsExactly(5, 6, 7, 8, 9, 10, 11, 12);
    }

    @Test
    void testGetApplicableVersions_rangeAndLimit() {
        var condition = new VersionRangesRequestCondition(testApiVersions, getInstanceOfVersionRange(5, 7), getInstanceOfVersionRange(9));
        assertThat(condition.getApplicableVersions()).containsExactly(5, 6, 7, 9, 10, 11, 12);
    }

    @Test
    void testCombine_empty() {
        var condition1 = new VersionRangesRequestCondition(testApiVersions, getInstanceOfVersionRange(5, 7), getInstanceOfVersionRange(9, 10));
        var condition2 = new VersionRangesRequestCondition(testApiVersions);
        assertThat(condition1.combine(condition2).getApplicableVersions()).isEqualTo(testApiVersions);
    }

    @Test
    void testCombine_limits() {
        var limit1 = getInstanceOfVersionRange(5);
        var limit2 = getInstanceOfVersionRange(9);
        var condition1 = new VersionRangesRequestCondition(testApiVersions, limit1);
        var condition2 = new VersionRangesRequestCondition(testApiVersions, limit2);

        assertThat(condition1.combine(condition2)).isEqualTo(new VersionRangesRequestCondition(testApiVersions, limit1));
        assertThat(condition2.combine(condition1)).isEqualTo(new VersionRangesRequestCondition(testApiVersions, limit1));
    }

    @Test
    void testCombine_ranges() {
        var range1 = getInstanceOfVersionRange(5, 7);
        var range2 = getInstanceOfVersionRange(9, 11);
        var condition1 = new VersionRangesRequestCondition(testApiVersions, range1);
        var condition2 = new VersionRangesRequestCondition(testApiVersions, range2);

        assertThat(condition1.combine(condition2)).isEqualTo(new VersionRangesRequestCondition(testApiVersions, range1, range2));
        assertThat(condition2.combine(condition1)).isEqualTo(new VersionRangesRequestCondition(testApiVersions, range1, range2));
    }

    @Test
    void testCombine_limitAndRange_dominantLimit() {
        var limit = getInstanceOfVersionRange(5);
        var range = getInstanceOfVersionRange(9, 11);
        var condition1 = new VersionRangesRequestCondition(testApiVersions, limit);
        var condition2 = new VersionRangesRequestCondition(testApiVersions, range);

        assertThat(condition1.combine(condition2)).isEqualTo(new VersionRangesRequestCondition(testApiVersions, limit));
        assertThat(condition2.combine(condition1)).isEqualTo(new VersionRangesRequestCondition(testApiVersions, limit));
    }

    @Test
    void testCombine_limitAndRange_dominantRange() {
        var limit = getInstanceOfVersionRange(9);
        var range = getInstanceOfVersionRange(5, 11);
        var condition1 = new VersionRangesRequestCondition(testApiVersions, limit);
        var condition2 = new VersionRangesRequestCondition(testApiVersions, range);

        var expectedLimit = getInstanceOfVersionRange(5);

        assertThat(condition1.combine(condition2)).isEqualTo(new VersionRangesRequestCondition(testApiVersions, expectedLimit));
        assertThat(condition2.combine(condition1)).isEqualTo(new VersionRangesRequestCondition(testApiVersions, expectedLimit));
    }

    @Test
    void testCombine_limitAndRange_separateRange() {
        var limit = getInstanceOfVersionRange(9);
        var range = getInstanceOfVersionRange(5, 7);
        var condition1 = new VersionRangesRequestCondition(testApiVersions, limit);
        var condition2 = new VersionRangesRequestCondition(testApiVersions, range);

        assertThat(condition1.combine(condition2)).isEqualTo(new VersionRangesRequestCondition(testApiVersions, range, limit));
        assertThat(condition2.combine(condition1)).isEqualTo(new VersionRangesRequestCondition(testApiVersions, range, limit));
    }

    @Test
    void testCollide_emptyRanges() {
        var range = getInstanceOfVersionRange(5, 7);
        var condition1 = new VersionRangesRequestCondition(testApiVersions, range);
        var condition2 = new VersionRangesRequestCondition(testApiVersions);
        assertThat(condition1.collide(condition2)).isTrue();
        assertThat(condition2.collide(condition1)).isTrue();
    }

    @Test
    void testCollide_noCollision() {
        var range1 = getInstanceOfVersionRange(5, 7);
        var range2 = getInstanceOfVersionRange(9, 11);
        var condition1 = new VersionRangesRequestCondition(testApiVersions, range1);
        var condition2 = new VersionRangesRequestCondition(testApiVersions, range2);
        assertThat(condition1.collide(condition2)).isFalse();
        assertThat(condition2.collide(condition1)).isFalse();
    }

    @Test
    void testCollide_collision() {
        var range1 = getInstanceOfVersionRange(5, 7);
        var range2 = getInstanceOfVersionRange(6, 11);
        var condition1 = new VersionRangesRequestCondition(testApiVersions, range1);
        var condition2 = new VersionRangesRequestCondition(testApiVersions, range2);
        assertThat(condition1.collide(condition2)).isTrue();
        assertThat(condition2.collide(condition1)).isTrue();
    }
}
