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

    /** Runs the same statement twice, which is a duplicate if the listener counted both under a request. */
    private List<RepeatedQueryFinding> runTwiceAndFinishRequest() {
        listener.afterQuery(new ExecutionInfo(), query(SQL));
        listener.afterQuery(new ExecutionInfo(), query(SQL));
        collector.finishRequest("GET", "/api/courses/{id}", null, null);
        return collector.getReport(Map.of()).repeatedQueries();
    }

    @Test
    void literalsAndValueListsAreNormalizedAwayButJoinsAndSubqueriesAreNot() {
        assertThat(SlowQueryListener.normalizeSql("select * from t where a = 'x' and b = 12.5 and c in (1, 2, 3)")).isEqualTo("select * from t where a = ? and b = ? and c IN (?)");
        assertThat(SlowQueryListener.normalizeSql("select * from t  join u on t.id = u.t_id where t.id in (select id from v)"))
                .isEqualTo("select * from t join u on t.id = u.t_id where t.id in (select id from v)");
        assertThat(SlowQueryListener.normalizeSql(null)).isNull();
    }

    @Test
    void anExecutionWithoutStatementsIsIgnored() {
        bindRequest();

        listener.afterQuery(new ExecutionInfo(), List.of());
        listener.beforeQuery(new ExecutionInfo(), query(SQL));
        listener.afterQuery(new ExecutionInfo(), query(SQL));

        collector.finishRequest("GET", "/api/courses/{id}", null, null);
        assertThat(collector.getReport(Map.of()).repeatedQueries()).isEmpty();
    }

    @Test
    void theQueriesOfARequestAreCountedTogether() {
        MockHttpServletRequest request = bindRequest();
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/courses/{courseId}");

        assertThat(runTwiceAndFinishRequest()).singleElement().satisfies(finding -> {
            assertThat(finding.type()).isEqualTo(RepeatedQueryFinding.Type.DUPLICATE);
            assertThat(finding.executions()).isEqualTo(2);
        });
    }

    @Test
    void aRequestWithoutAResolvedRouteIsStillCounted() {
        bindRequest();

        assertThat(runTwiceAndFinishRequest()).hasSize(1);
    }

    @Test
    void aQueryOutsideARequestIsNotCounted() {
        assertThat(runTwiceAndFinishRequest()).isEmpty();
    }

    @Test
    void aRecycledRequestIsTreatedAsNoRequest() {
        // a servlet container throws IllegalStateException for a request that was already recycled
        HttpServletRequest recycled = mock(HttpServletRequest.class);
        when(recycled.getMethod()).thenThrow(new IllegalStateException("request recycled"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(recycled));

        assertThat(runTwiceAndFinishRequest()).isEmpty();
    }
}
