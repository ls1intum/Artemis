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
    void sameTemplateFromDifferentCodeIsCountedPerCallSite() {
        // one place loads three users one by one, a later one loads the same three again
        QueryCallSite later = new QueryCallSite("UserRepository.findByIdElseThrow", "ExamDeletionService.delete");
        for (long id = 1; id <= 3; id++) {
            query(SQL, id);
        }
        for (long id = 1; id <= 3; id++) {
            query(SQL, id, () -> later);
        }

        assertThat(finishRequest()).hasSize(2).allSatisfy(finding -> {
            assertThat(finding.type()).isEqualTo(Type.N_PLUS_ONE);
            assertThat(finding.executions()).isEqualTo(3);
            assertThat(finding.distinctParameterSets()).isEqualTo(3);
        }).extracting(RepeatedQueryFinding::callerMethod).containsExactlyInAnyOrder(SITE.callerMethod(), later.callerMethod());
    }

    @Test
    void callSiteIsResolvedForEveryRequestQueryButNotForBackgroundQueries() {
        AtomicInteger resolutions = new AtomicInteger();
        Supplier<QueryCallSite> countingSite = () -> {
            resolutions.incrementAndGet();
            return SITE;
        };
        for (long id = 1; id <= 3; id++) {
            query(SQL, id, countingSite);
        }
        collector.record(SQL, 4, 1, 0, countingSite, null, null, null, null, "scheduling-1");

        finishRequest();
        assertThat(resolutions).hasValue(3);
    }

    @Test
    void failingCallSiteResolutionStillCountsTheQuery() {
        Supplier<QueryCallSite> failing = () -> {
            throw new IllegalStateException("stack walk failed");
        };
        query(SQL, 1, failing);
        query(SQL, 2, failing);

        assertThat(finishRequest()).singleElement().satisfies(finding -> {
            assertThat(finding.executions()).isEqualTo(2);
            assertThat(finding.repositoryMethod()).isNull();
            assertThat(finding.callerMethod()).isNull();
        });
    }

    @Test
    void endpointTimingSumsATemplateOverItsCallSites() {
        query(SQL, 1);
        query(SQL, 2, () -> new QueryCallSite("UserRepository.findByIdElseThrow", "ExamDeletionService.delete"));
        finishRequest();

        assertThat(collector.getReport().endpointTimings()).singleElement().satisfies(timing -> {
            assertThat(timing.queryCount()).isEqualTo(2);
            assertThat(timing.queries()).singleElement().satisfies(entry -> assertThat(entry.count()).isEqualTo(2));
        });
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
    void parameterSignatureSeparatesSwappedValuesButNotCallOrder() {
        String rawSql = "select * from participation where exercise_id=? and student_id=?";
        ParameterSetOperation firstIsOne = new ParameterSetOperation(null, new Object[] { 1, 1L });
        ParameterSetOperation secondIsTwo = new ParameterSetOperation(null, new Object[] { 2, 2L });
        ParameterSetOperation firstIsTwo = new ParameterSetOperation(null, new Object[] { 1, 2L });
        ParameterSetOperation secondIsOne = new ParameterSetOperation(null, new Object[] { 2, 1L });
        long bound = SlowQueryListener.parameterSignature(rawSql, List.of(List.of(firstIsOne, secondIsTwo)));

        assertThat(SlowQueryListener.parameterSignature(rawSql, List.of(List.of(firstIsTwo, secondIsOne)))).isNotEqualTo(bound);
        assertThat(SlowQueryListener.parameterSignature(rawSql, List.of(List.of(secondIsTwo, firstIsOne)))).isEqualTo(bound);
    }

    @Test
    void normalizationCollapsesValueListsButKeepsSubqueries() {
        assertThat(SlowQueryListener.normalizeSql("select id from t where id in (1, 2, 3)")).isEqualTo("select id from t where id IN (?)");
        assertThat(SlowQueryListener.normalizeSql("select id from t where id in (?, ?)")).isEqualTo("select id from t where id IN (?)");

        String fromA = SlowQueryListener.normalizeSql("select id from t where id in (select id from a)");
        String fromB = SlowQueryListener.normalizeSql("select id from t where id in (select id from b)");
        assertThat(fromA).isEqualTo("select id from t where id in (select id from a)");
        assertThat(fromA).isNotEqualTo(fromB);
        assertThat(SlowQueryListener.normalizeSql("select id from t where id in (select max(id) from a where x in (4, 5))"))
                .isEqualTo("select id from t where id in (select max(id) from a where x IN (?))");
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
