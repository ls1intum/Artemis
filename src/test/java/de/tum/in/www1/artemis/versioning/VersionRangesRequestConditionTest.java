package de.tum.in.www1.artemis.versioning;

import static de.tum.in.www1.artemis.versioning.VersionRangeFactory.getInstanceOfVersionRange;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.config.VersioningConfiguration;

class VersionRangesRequestConditionTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final DummyRequest DUMMY_REQUEST = new DummyRequest();

    @Test
    void testInitEdgeCases() {
        VersionRangesRequestCondition nullCondition = new VersionRangesRequestCondition((VersionRange) null);
        assertThat(nullCondition.getRanges()).isEmpty();

        VersionRangesRequestCondition emptyCondition = new VersionRangesRequestCondition();
        assertThat(emptyCondition.getRanges()).isEmpty();
    }

    @Test
    void testGetMatchingCondition_emptyList() {
        VersionRangesRequestCondition condition = new VersionRangesRequestCondition();
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
    }

    @Test
    void testGetMatchingCondition_limits() {
        List<VersionRange> list = Arrays.asList(getInstanceOfVersionRange(2), getInstanceOfVersionRange(3), getInstanceOfVersionRange(4));
        VersionRangesRequestCondition condition = new VersionRangesRequestCondition(list);

        DUMMY_REQUEST.setRequestURI("/api/v1");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isNull();
        DUMMY_REQUEST.setRequestURI("/api/v2");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
        DUMMY_REQUEST.setRequestURI("/api/v3");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
        DUMMY_REQUEST.setRequestURI("/api/v4");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
        DUMMY_REQUEST.setRequestURI("/api/start");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
    }

    @Test
    void testGetMatchingConditionRanges() {
        List<VersionRange> list = Arrays.asList(getInstanceOfVersionRange(2, 2), getInstanceOfVersionRange(4, 4));
        VersionRangesRequestCondition condition = new VersionRangesRequestCondition(list);

        DUMMY_REQUEST.setRequestURI("/api/v1");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isNull();
        DUMMY_REQUEST.setRequestURI("/api/v2");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
        DUMMY_REQUEST.setRequestURI("/api/v3");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isNull();
        DUMMY_REQUEST.setRequestURI("/api/v4");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
        DUMMY_REQUEST.setRequestURI("/api/v5");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isNull();
        DUMMY_REQUEST.setRequestURI("/api/start");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isNull();
    }

    @Test
    void testGetMatchingConditionLimitAndRange() {
        List<VersionRange> list = Arrays.asList(getInstanceOfVersionRange(2), getInstanceOfVersionRange(4, 4));
        VersionRangesRequestCondition condition = new VersionRangesRequestCondition(list);

        DUMMY_REQUEST.setRequestURI("/api/v1");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isNull();
        DUMMY_REQUEST.setRequestURI("/api/v2");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
        DUMMY_REQUEST.setRequestURI("/api/v3");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
        DUMMY_REQUEST.setRequestURI("/api/v4");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
        DUMMY_REQUEST.setRequestURI("/api/v5");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
        DUMMY_REQUEST.setRequestURI("/api/start");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);

        list = Arrays.asList(getInstanceOfVersionRange(2, 2), getInstanceOfVersionRange(4));
        condition = new VersionRangesRequestCondition(list);

        DUMMY_REQUEST.setRequestURI("/api/v1");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isNull();
        DUMMY_REQUEST.setRequestURI("/api/v2");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
        DUMMY_REQUEST.setRequestURI("/api/v3");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isNull();
        DUMMY_REQUEST.setRequestURI("/api/v4");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
        DUMMY_REQUEST.setRequestURI("/api/v5");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
        DUMMY_REQUEST.setRequestURI("/api/start");
        assertThat(condition.getMatchingCondition(DUMMY_REQUEST)).isSameAs(condition);
    }

    @Test
    void testGetApplicableVersions_null() {
        var condition = new VersionRangesRequestCondition((VersionRange) null);
        assertThat(condition.getApplicableVersions()).isEqualTo(VersioningConfiguration.API_VERSIONS);
    }

    @Test
    void testGetApplicableVersions_empty() {
        var condition = new VersionRangesRequestCondition();
        assertThat(condition.getApplicableVersions()).isEqualTo(VersioningConfiguration.API_VERSIONS);
    }

    @Test
    void testGetApplicableVersions_singleLimit() {
        var condition = new VersionRangesRequestCondition(getInstanceOfVersionRange(5));
        assertThat(condition.getApplicableVersions()).containsExactly(5, 6, 7, 8, 9, 10, 11, 12);
    }

    @Test
    void testGetApplicableVersions_singleRange() {
        var condition = new VersionRangesRequestCondition(getInstanceOfVersionRange(5, 7));
        assertThat(condition.getApplicableVersions()).containsExactly(5, 6, 7);
    }

    @Test
    void testGetApplicableVersions_multipleRanges() {
        var condition = new VersionRangesRequestCondition(getInstanceOfVersionRange(5, 7), getInstanceOfVersionRange(9, 11));
        assertThat(condition.getApplicableVersions()).containsExactly(5, 6, 7, 9, 10, 11);
    }

    @Test
    void testGetApplicableVersions_multipleLimits() {
        var condition = new VersionRangesRequestCondition(getInstanceOfVersionRange(5), getInstanceOfVersionRange(9));
        assertThat(condition.getApplicableVersions()).containsExactly(5, 6, 7, 8, 9, 10, 11, 12);
    }

    @Test
    void testGetApplicableVersions_rangeAndLimit() {
        var condition = new VersionRangesRequestCondition(getInstanceOfVersionRange(5, 7), getInstanceOfVersionRange(9));
        assertThat(condition.getApplicableVersions()).containsExactly(5, 6, 7, 9, 10, 11, 12);
    }

    @Test
    void testCombine_nullAbsorption() {
        var condition1 = new VersionRangesRequestCondition(getInstanceOfVersionRange(5, 7), getInstanceOfVersionRange(9));
        var condition2 = new VersionRangesRequestCondition((VersionRange) null);
        assertThat(condition1.combine(condition2).getApplicableVersions()).isEqualTo(VersioningConfiguration.API_VERSIONS);
    }

    @Test
    void testCombine_empty() {
        var condition1 = new VersionRangesRequestCondition(getInstanceOfVersionRange(5, 7), getInstanceOfVersionRange(9, 10));
        var condition2 = new VersionRangesRequestCondition();
        assertThat(condition1.combine(condition2).getApplicableVersions()).isEqualTo(VersioningConfiguration.API_VERSIONS);
    }

    @Test
    void testCombine_limits() {
        var limit1 = getInstanceOfVersionRange(5);
        var limit2 = getInstanceOfVersionRange(9);
        var condition1 = new VersionRangesRequestCondition(limit1);
        var condition2 = new VersionRangesRequestCondition(limit2);
        assertThat(condition1.combine(condition2)).isEqualTo(new VersionRangesRequestCondition(limit1));
        assertThat(condition2.combine(condition1)).isEqualTo(new VersionRangesRequestCondition(limit1));
    }

    @Test
    void testCombine_ranges() {
        var range1 = getInstanceOfVersionRange(5, 7);
        var range2 = getInstanceOfVersionRange(9, 11);
        var condition1 = new VersionRangesRequestCondition(range1);
        var condition2 = new VersionRangesRequestCondition(range2);
        assertThat(condition1.combine(condition2)).isEqualTo(new VersionRangesRequestCondition(range1, range2));
        assertThat(condition2.combine(condition1)).isEqualTo(new VersionRangesRequestCondition(range1, range2));
    }
}
