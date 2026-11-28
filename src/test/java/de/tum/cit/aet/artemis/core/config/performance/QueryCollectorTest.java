package de.tum.cit.aet.artemis.core.config.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.core.config.performance.RepeatedQueryFinding.Type;
import net.ttddyy.dsproxy.proxy.ParameterSetOperation;

/**
 * Covers the repeated-query shapes the collector derives per request: N+1 (same template,
 * different parameters) and duplicate (same statement, same parameters).
 */
class QueryCollectorTest {

    private static final String SQL = "select * from participation where exercise_id=?";

    private static final String ENDPOINT = "/api/courses/{courseId}/stats";

    private static final QueryCallSite SITE = new QueryCallSite("ParticipationRepository.findByExerciseId", "CourseStatsService.calculate");

    private QueryCollector collector;

    @BeforeEach
    void setUp() {
        collector = new QueryCollector(new QueryProperties());
    }

    private void query(String sql, long signature, Supplier<QueryCallSite> site) {
        collector.record(sql, signature, site);
    }

    private void query(String sql, long signature) {
        query(sql, signature, () -> SITE);
    }

    private List<RepeatedQueryFinding> finishRequest() {
        collector.finishRequest("GET", ENDPOINT, "CourseStats.spec", "action");
        collector.resetRequestState();
        return collector.getReport(Map.of()).repeatedQueries();
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
    void findingCarriesTheSourceLocationOfItsCallSite() {
        QueryCallSite located = new QueryCallSite("ParticipationRepository.findByExerciseId", "CourseStatsService.calculate", "src/main/java/CourseStatsService.java", 121);
        for (long id = 1; id <= 3; id++) {
            query(SQL, id, () -> located);
        }

        assertThat(finishRequest()).singleElement().satisfies(finding -> {
            assertThat(finding.sourceFile()).isEqualTo("src/main/java/CourseStatsService.java");
            assertThat(finding.line()).isEqualTo(121);
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
    void repeatsInsideAnNPlusOneAreCountedByIt() {
        query(SQL, 1);
        query(SQL, 1);
        query(SQL, 2);

        assertThat(finishRequest()).singleElement().satisfies(finding -> {
            assertThat(finding.type()).isEqualTo(Type.N_PLUS_ONE);
            assertThat(finding.executions()).isEqualTo(3);
            assertThat(finding.distinctParameterSets()).isEqualTo(2);
        });
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

        List<RepeatedQueryFinding> findings = finishRequest();
        assertThat(findings).filteredOn(finding -> finding.type() == Type.N_PLUS_ONE).hasSize(2).allSatisfy(finding -> {
            assertThat(finding.executions()).isEqualTo(3);
            assertThat(finding.distinctParameterSets()).isEqualTo(3);
        }).extracting(RepeatedQueryFinding::callerMethod).containsExactlyInAnyOrder(SITE.callerMethod(), later.callerMethod());
        assertThat(findings).filteredOn(finding -> finding.type() == Type.DUPLICATE).singleElement().satisfies(finding -> {
            assertThat(finding.executions()).isEqualTo(6);
            assertThat(finding.distinctParameterSets()).isEqualTo(3);
            assertThat(finding.callSites()).containsExactly(SITE, later);
        });
    }

    @Test
    void duplicateAcrossCallSitesCountsOnlyTheStatementsSeveralOfThemRan() {
        QueryCallSite later = new QueryCallSite("UserRepository.findByIdElseThrow", "ExamDeletionService.delete");
        query(SQL, 1);
        query(SQL, 2);
        query(SQL, 2, () -> later);

        List<RepeatedQueryFinding> findings = finishRequest();
        assertThat(findings).extracting(RepeatedQueryFinding::type).containsExactlyInAnyOrder(Type.N_PLUS_ONE, Type.DUPLICATE);
        assertThat(findings).filteredOn(finding -> finding.type() == Type.DUPLICATE).singleElement().satisfies(finding -> {
            assertThat(finding.executions()).isEqualTo(2);
            assertThat(finding.distinctParameterSets()).isEqualTo(1);
            assertThat(finding.repositoryMethod()).isNull();
            assertThat(finding.callerMethod()).isNull();
            assertThat(finding.callSites()).containsExactly(SITE, later);
        });
    }

    @Test
    void differentStatementsFromDifferentCodeAreNoDuplicate() {
        query(SQL, 1);
        query(SQL, 2, () -> new QueryCallSite("UserRepository.findByIdElseThrow", "ExamDeletionService.delete"));

        assertThat(finishRequest()).isEmpty();
    }

    @Test
    void callSiteIsResolvedForEveryQuery() {
        AtomicInteger resolutions = new AtomicInteger();
        Supplier<QueryCallSite> countingSite = () -> {
            resolutions.incrementAndGet();
            return SITE;
        };
        for (long id = 1; id <= 3; id++) {
            query(SQL, id, countingSite);
        }

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

    /** One bound parameter, as the proxy records a {@code PreparedStatement.setXxx(index, value)} call. */
    private static List<List<ParameterSetOperation>> parameter(Object value) {
        return List.of(List.of(new ParameterSetOperation(null, new Object[] { 1, value })));
    }

    @Test
    void parameterSignatureSeparatesValuesAndMatchesEqualOnes() {
        String rawSql = "select * from participation where exercise_id=?";
        long first = QueryListener.parameterSignature(rawSql, parameter(7L));

        assertThat(QueryListener.parameterSignature(rawSql, parameter(7L))).isEqualTo(first);
        assertThat(QueryListener.parameterSignature(rawSql, parameter(8L))).isNotEqualTo(first);
        // array values are compared by content, not identity
        assertThat(QueryListener.parameterSignature(rawSql, parameter(new byte[] { 1, 2 }))).isEqualTo(QueryListener.parameterSignature(rawSql, parameter(new byte[] { 1, 2 })));
        // literals inlined by Hibernate are part of the raw SQL, so they distinguish statements too
        assertThat(QueryListener.parameterSignature("select * from course where id=1", List.of()))
                .isNotEqualTo(QueryListener.parameterSignature("select * from course where id=2", List.of()));
    }

    @Test
    void parameterSignatureSeparatesSwappedValuesButNotCallOrder() {
        String rawSql = "select * from participation where exercise_id=? and student_id=?";
        ParameterSetOperation firstIsOne = new ParameterSetOperation(null, new Object[] { 1, 1L });
        ParameterSetOperation secondIsTwo = new ParameterSetOperation(null, new Object[] { 2, 2L });
        ParameterSetOperation firstIsTwo = new ParameterSetOperation(null, new Object[] { 1, 2L });
        ParameterSetOperation secondIsOne = new ParameterSetOperation(null, new Object[] { 2, 1L });
        long bound = QueryListener.parameterSignature(rawSql, List.of(List.of(firstIsOne, secondIsTwo)));

        assertThat(QueryListener.parameterSignature(rawSql, List.of(List.of(firstIsTwo, secondIsOne)))).isNotEqualTo(bound);
        assertThat(QueryListener.parameterSignature(rawSql, List.of(List.of(secondIsTwo, firstIsOne)))).isEqualTo(bound);
    }

    /** The parameters of one statement with two {@code setLong(index, value)} calls. */
    private static List<List<ParameterSetOperation>> twoLongs(long first, long second) {
        return List.of(List.of(new ParameterSetOperation(null, new Object[] { 1, first }), new ParameterSetOperation(null, new Object[] { 2, second })));
    }

    @Test
    void parameterSignatureSeparatesNeighbouringIdsThatAWeightedSumWouldMerge() {
        String rawSql = "select * from participation where exercise_id=? and student_id=?";

        // 31 * 1 + 32 == 31 * 2 + 1, and 31 * 100 + 200 == 31 * 101 + 169
        assertThat(QueryListener.parameterSignature(rawSql, twoLongs(1, 32))).isNotEqualTo(QueryListener.parameterSignature(rawSql, twoLongs(2, 1)));
        assertThat(QueryListener.parameterSignature(rawSql, twoLongs(100, 200))).isNotEqualTo(QueryListener.parameterSignature(rawSql, twoLongs(101, 169)));

        long[] signatures = LongStream.range(0, 200).flatMap(first -> LongStream.range(0, 200).map(second -> QueryListener.parameterSignature(rawSql, twoLongs(first, second))))
                .distinct().toArray();
        assertThat(signatures).hasSize(200 * 200);
    }

    @Test
    void parameterSignatureTellsTypesSettersAndBatchesApart() throws NoSuchMethodException {
        String rawSql = "update participation set due_date=? where id=?";
        Method setInt = PreparedStatement.class.getMethod("setInt", int.class, int.class);
        Method setNull = PreparedStatement.class.getMethod("setNull", int.class, int.class);

        assertThat(QueryListener.parameterSignature(rawSql, parameter(1))).isNotEqualTo(QueryListener.parameterSignature(rawSql, parameter(1L)));
        assertThat(QueryListener.parameterSignature(rawSql, parameter("1"))).isNotEqualTo(QueryListener.parameterSignature(rawSql, parameter(1)));
        assertThat(QueryListener.parameterSignature(rawSql, List.of(List.of(new ParameterSetOperation(setInt, new Object[] { 1, 4 })))))
                .isNotEqualTo(QueryListener.parameterSignature(rawSql, List.of(List.of(new ParameterSetOperation(setNull, new Object[] { 1, 4 })))));
        List<ParameterSetOperation> first = parameter(1L).getFirst();
        List<ParameterSetOperation> second = parameter(2L).getFirst();
        assertThat(QueryListener.parameterSignature(rawSql, List.of(first, second))).isNotEqualTo(QueryListener.parameterSignature(rawSql, List.of(second, first)))
                .isNotEqualTo(QueryListener.parameterSignature(rawSql, List.of(first)));
    }

    @Test
    void parameterSignatureUsesOnlyTheTimeZoneOfACalendar() {
        String rawSql = "select * from exam where start_date=?";
        Calendar earlier = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        earlier.setTimeInMillis(1_000);
        Calendar later = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        later.setTimeInMillis(2_000);

        assertThat(QueryListener.parameterSignature(rawSql, parameter(earlier))).isEqualTo(QueryListener.parameterSignature(rawSql, parameter(later)))
                .isNotEqualTo(QueryListener.parameterSignature(rawSql, parameter(new GregorianCalendar(TimeZone.getTimeZone("Europe/Berlin")))));
    }

    @Test
    void normalizationCollapsesValueListsButKeepsSubqueries() {
        assertThat(QueryListener.normalizeSql("select id from t where id in (1, 2, 3)")).isEqualTo("select id from t where id IN (?)");
        assertThat(QueryListener.normalizeSql("select id from t where id in (?, ?)")).isEqualTo("select id from t where id IN (?)");

        String fromA = QueryListener.normalizeSql("select id from t where id in (select id from a)");
        String fromB = QueryListener.normalizeSql("select id from t where id in (select id from b)");
        assertThat(fromA).isEqualTo("select id from t where id in (select id from a)");
        assertThat(fromA).isNotEqualTo(fromB);
        assertThat(QueryListener.normalizeSql("select id from t where id in (select max(id) from a where x in (4, 5))"))
                .isEqualTo("select id from t where id in (select max(id) from a where x IN (?))");
    }

    @Test
    void sameFindingOfSeveralRequestsIsKeptOnceWithItsWorstRequest() {
        for (int request = 2; request <= 4; request++) {
            for (long id = 1; id <= request; id++) {
                query(SQL, id);
            }
            collector.finishRequest("GET", ENDPOINT, "CourseStats.spec", "action");
            collector.resetRequestState();
        }

        assertThat(collector.getReport(Map.of()).repeatedQueries()).singleElement().satisfies(finding -> assertThat(finding.executions()).isEqualTo(4));
        assertThat(collector.getReport(Map.of()).truncated()).isFalse();
    }

    @Test
    void reportIsOnlyTruncatedAfterAFindingWasDropped() {
        QueryProperties properties = new QueryProperties();
        properties.setMaxRecordedQueries(1);
        collector = new QueryCollector(properties);

        query(SQL, 1);
        query(SQL, 2);
        assertThat(finishRequest()).hasSize(1);
        assertThat(collector.getReport(Map.of()).truncated()).isFalse();

        // the same finding again uses no further room
        query(SQL, 3);
        query(SQL, 4);
        assertThat(finishRequest()).hasSize(1);
        assertThat(collector.getReport(Map.of()).truncated()).isFalse();

        // the one allowed finding is used up, so this other one is dropped
        String other = "select * from lecture where course_id=?";
        query(other, 5);
        query(other, 6);
        assertThat(finishRequest()).hasSize(1);
        assertThat(collector.getReport(Map.of()).truncated()).isTrue();
    }

    @Test
    void reachingTheLimitIsLoggedOnce() {
        QueryProperties properties = new QueryProperties();
        properties.setMaxRecordedQueries(1);
        collector = new QueryCollector(properties);
        Logger logger = (Logger) LoggerFactory.getLogger(QueryCollector.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            for (String table : List.of("lecture", "exam", "exercise")) {
                String sql = "select * from " + table + " where course_id=?";
                query(sql, 1);
                query(sql, 2);
                finishRequest();
            }
        }
        finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).singleElement().satisfies(event -> assertThat(event.getFormattedMessage()).contains("max 1 entries reached"));
    }

    @Test
    void testNameHeaderIsPercentDecoded() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(QueryRequestFilter.testName(request)).isNull();

        request.addHeader(QueryRequestFilter.PLAYWRIGHT_TEST_HEADER, "Dismiss%20reasoning%20%E2%9C%95%20closes%20card%20%2B%20more");
        assertThat(QueryRequestFilter.testName(request)).isEqualTo("Dismiss reasoning ✕ closes card + more");

        MockHttpServletRequest malformed = new MockHttpServletRequest();
        malformed.addHeader(QueryRequestFilter.PLAYWRIGHT_TEST_HEADER, "100%");
        assertThat(QueryRequestFilter.testName(malformed)).isEqualTo("100%");
    }
}
