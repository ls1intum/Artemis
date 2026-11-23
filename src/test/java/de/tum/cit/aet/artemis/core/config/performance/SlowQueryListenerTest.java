package de.tum.cit.aet.artemis.core.config.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;

/**
 * Covers how the listener turns an executed statement and the current request into a finding.
 */
class SlowQueryListenerTest {

    private static final String SQL = "select * from participation where exercise_id = 42";

    private SlowQueryCollector collector;

    private SlowQueryListener listener;

    @BeforeEach
    void setUp() {
        collector = new SlowQueryCollector(new SlowQueryProperties());
        listener = new SlowQueryListener(collector);
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
        collector.resetRequestState();
    }

    private static ExecutionInfo executionTakingMs(long elapsedMs) {
        ExecutionInfo info = new ExecutionInfo();
        info.setElapsedTime(elapsedMs);
        return info;
    }

    private static List<QueryInfo> query(String sql) {
        QueryInfo queryInfo = new QueryInfo();
        queryInfo.setQuery(sql);
        return List.of(queryInfo);
    }

    private MockHttpServletRequest bindRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/courses/7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    @Test
    void literalsAndValueListsAreNormalizedAwayButJoinsAndSubqueriesAreNot() {
        assertThat(SlowQueryListener.normalizeSql("select * from t where a = 'x' and b = 12.5 and c in (1, 2, 3)")).isEqualTo("select * from t where a = ? and b = ? and c IN (?)");
        assertThat(SlowQueryListener.normalizeSql("select * from t  join u on t.id = u.t_id where t.id in (select id from v)"))
                .isEqualTo("select * from t join u on t.id = u.t_id where t.id in (select id from v)");
        assertThat(SlowQueryListener.normalizeSql(null)).isNull();
    }

    @Test
    void joinsAreCountedAcrossAllVariants() {
        assertThat(SlowQueryListener.countJoins("select * from a join b on 1=1 left join c on 1=1 INNER JOIN d on 1=1")).isEqualTo(3);
        assertThat(SlowQueryListener.countJoins("select * from a")).isZero();
        assertThat(SlowQueryListener.countJoins(null)).isZero();
    }

    @Test
    void anExecutionWithoutStatementsIsIgnored() {
        bindRequest();

        listener.afterQuery(executionTakingMs(500), List.of());
        listener.beforeQuery(executionTakingMs(0), query(SQL));

        collector.recordEndpointTiming("GET", "/api/courses/{id}", null, null, 1);
        assertThat(collector.getReport(Map.of()).slowQueryCount()).isZero();
        assertThat(collector.getReport(Map.of()).endpointTimings()).isEmpty();
    }

    @Test
    void aSlowQueryOfARequestIsRecordedWithTheRoutePlaywrightTestAndPhase() {
        MockHttpServletRequest request = bindRequest();
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/courses/{courseId}");
        request.addHeader(SlowQueryListener.PLAYWRIGHT_TEST_HEADER, "My%20test");
        request.addHeader(SlowQueryListener.PLAYWRIGHT_PHASE_HEADER, "action");

        listener.afterQuery(executionTakingMs(250), query(SQL));

        assertThat(collector.getReport(Map.of()).slowQueries()).singleElement().satisfies(slow -> {
            assertThat(slow.httpMethod()).isEqualTo("GET");
            assertThat(slow.httpEndpoint()).isEqualTo("/api/courses/{courseId}");
            assertThat(slow.testName()).isEqualTo("My test");
            assertThat(slow.phase()).isEqualTo("action");
            assertThat(slow.executionTimeMs()).isEqualTo(250);
        });
    }

    @Test
    void aRequestWithoutAResolvedRouteFallsBackToItsUri() {
        bindRequest();

        listener.afterQuery(executionTakingMs(250), query(SQL));

        assertThat(collector.getReport(Map.of()).slowQueries()).singleElement().satisfies(slow -> assertThat(slow.httpEndpoint()).isEqualTo("/api/courses/7"));
    }

    @Test
    void aQueryOutsideARequestIsRecordedWithoutRequestContext() {
        listener.afterQuery(executionTakingMs(250), query(SQL));

        assertThat(collector.getReport(Map.of()).slowQueries()).singleElement().satisfies(slow -> {
            assertThat(slow.httpMethod()).isNull();
            assertThat(slow.httpEndpoint()).isNull();
            assertThat(slow.threadName()).isEqualTo(Thread.currentThread().getName());
        });
    }

    @Test
    void aRecycledRequestIsTreatedAsNoRequest() {
        // a servlet container throws IllegalStateException for a request that was already recycled
        HttpServletRequest recycled = mock(HttpServletRequest.class);
        when(recycled.getMethod()).thenThrow(new IllegalStateException("request recycled"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(recycled));

        listener.afterQuery(executionTakingMs(250), query(SQL));

        assertThat(collector.getReport(Map.of()).slowQueries()).singleElement().satisfies(slow -> assertThat(slow.httpMethod()).isNull());
    }
}
