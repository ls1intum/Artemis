package de.tum.cit.aet.artemis.core.config.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import de.tum.cit.aet.artemis.core.config.performance.RepeatedQueryFinding.Type;
import net.ttddyy.dsproxy.proxy.ParameterSetOperation;

/**
 * Covers the repeated-query shapes the collector derives per request: N+1 (same template,
 * different parameters) and duplicate (same statement, same parameters).
 */
class SlowQueryCollectorTest {

    private static final String SQL = "select * from participation where exercise_id=?";

    private static final String ENDPOINT = "/api/courses/{courseId}/stats";

    private static final QueryCallSite SITE = new QueryCallSite("ParticipationRepository.findByExerciseId", "CourseStatsService.calculate");

    private SlowQueryCollector collector;

    @BeforeEach
    void setUp() {
        collector = new SlowQueryCollector(new SlowQueryProperties());
    }

    private void query(String sql, long signature, Supplier<QueryCallSite> site) {
        collector.record(sql, signature, 1, 0, site, "GET", ENDPOINT, "CourseStats.spec", "action", "tomcat-1");
    }

    private void query(String sql, long signature) {
        query(sql, signature, () -> SITE);
    }

    private List<RepeatedQueryFinding> finishRequest() {
        collector.recordEndpointTiming("GET", ENDPOINT, "CourseStats.spec", "action", 10);
        collector.resetRequestState();
        return collector.getReport().repeatedQueries();
    }

    @Test
    void sameTemplateWithDifferentParametersIsNPlusOne() {
        for (long id = 1; id <= 3; id++) {
            query(SQL, id);
        }

        assertThat(finishRequest()).singleElement().satisfies(finding -> {
            assertThat(finding.type()).isEqualTo(Type.N_PLUS_ONE);
            assertThat(finding.executions()).isEqualTo(3);
            assertThat(finding.distinctParameterSets()).isEqualTo(3);
            assertThat(finding.repositoryMethod()).isEqualTo("ParticipationRepository.findByExerciseId");
            assertThat(finding.callerMethod()).isEqualTo("CourseStatsService.calculate");
            assertThat(finding.httpEndpoint()).isEqualTo(ENDPOINT);
            assertThat(finding.testName()).isEqualTo("CourseStats.spec");
        });
    }

    @Test
    void sameStatementWithSameParametersIsDuplicate() {
        query(SQL, 42);
        query(SQL, 42);

        assertThat(finishRequest()).singleElement().satisfies(finding -> {
            assertThat(finding.type()).isEqualTo(Type.DUPLICATE);
            assertThat(finding.executions()).isEqualTo(2);
            assertThat(finding.distinctParameterSets()).isEqualTo(1);
        });
    }

    @Test
    void templateCanShowBothShapes() {
        query(SQL, 1);
        query(SQL, 1);
        query(SQL, 2);

        assertThat(finishRequest()).extracting(RepeatedQueryFinding::type).containsExactlyInAnyOrder(Type.N_PLUS_ONE, Type.DUPLICATE);
    }

    @Test
    void singleExecutionIsNoFinding() {
        query(SQL, 1);
        query("select * from course where id=?", 1);

        assertThat(finishRequest()).isEmpty();
    }

    @Test
    void repetitionIsCountedPerRequestNotAcrossRequests() {
        query(SQL, 1);
        finishRequest();
        query(SQL, 2);

        assertThat(finishRequest()).isEmpty();
    }

    @Test
    void callSiteIsResolvedOnlyOncePerTemplateAndRequest() {
        AtomicInteger resolutions = new AtomicInteger();
        Supplier<QueryCallSite> countingSite = () -> {
            resolutions.incrementAndGet();
            return SITE;
        };
        for (long id = 1; id <= 5; id++) {
            query(SQL, id, countingSite);
        }
        query("select * from course where id=?", 1, countingSite);

        finishRequest();
        assertThat(resolutions).hasValue(1);
    }

    @Test
    void backgroundQueriesWithoutRequestAreNotCountedAsRepeated() {
        for (long id = 1; id <= 3; id++) {
            collector.record(SQL, id, 1, 0, () -> SITE, null, null, null, null, "scheduling-1");
        }

        assertThat(collector.getReport().repeatedQueries()).isEmpty();
    }

    /** One bound parameter, as the proxy records a {@code PreparedStatement.setXxx(index, value)} call. */
    private static List<List<ParameterSetOperation>> parameter(Object value) {
        return List.of(List.of(new ParameterSetOperation(null, new Object[] { 1, value })));
    }

    @Test
    void parameterSignatureSeparatesValuesAndMatchesEqualOnes() {
        String rawSql = "select * from participation where exercise_id=?";
        long first = SlowQueryListener.parameterSignature(rawSql, parameter(7L));

        assertThat(SlowQueryListener.parameterSignature(rawSql, parameter(7L))).isEqualTo(first);
        assertThat(SlowQueryListener.parameterSignature(rawSql, parameter(8L))).isNotEqualTo(first);
        // array values are compared by content, not identity
        assertThat(SlowQueryListener.parameterSignature(rawSql, parameter(new byte[] { 1, 2 })))
                .isEqualTo(SlowQueryListener.parameterSignature(rawSql, parameter(new byte[] { 1, 2 })));
        // literals inlined by Hibernate are part of the raw SQL, so they distinguish statements too
        assertThat(SlowQueryListener.parameterSignature("select * from course where id=1", List.of()))
                .isNotEqualTo(SlowQueryListener.parameterSignature("select * from course where id=2", List.of()));
    }

    @Test
    void testNameHeaderIsPercentDecoded() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(SlowQueryListener.testName(request)).isNull();

        request.addHeader(SlowQueryListener.PLAYWRIGHT_TEST_HEADER, "Dismiss%20reasoning%20%E2%9C%95%20closes%20card%20%2B%20more");
        assertThat(SlowQueryListener.testName(request)).isEqualTo("Dismiss reasoning ✕ closes card + more");

        MockHttpServletRequest malformed = new MockHttpServletRequest();
        malformed.addHeader(SlowQueryListener.PLAYWRIGHT_TEST_HEADER, "100%");
        assertThat(SlowQueryListener.testName(malformed)).isEqualTo("100%");
    }
}
