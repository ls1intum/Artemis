package de.tum.cit.aet.artemis.core.config.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Covers the filter that turns a request's queries into findings and clears its per-request state.
 */
class QueryRequestFilterTest {

    private static final String SQL = "select * from participation where exercise_id=?";

    private QueryCollector collector;

    private QueryRequestFilter filter;

    @BeforeEach
    void setUp() {
        collector = new QueryCollector(new QueryProperties());
        filter = new QueryRequestFilter(collector);
    }

    private void queryOnce(long signature) {
        collector.record(SQL, signature, () -> QueryCallSite.UNKNOWN);
    }

    @Test
    void aRequestThatRepeatedAQueryIsReportedUnderItsRouteTemplate() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/courses/7");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/courses/{courseId}");
        request.addHeader(QueryRequestFilter.PLAYWRIGHT_TEST_HEADER, "A%20test");
        request.addHeader(QueryRequestFilter.PLAYWRIGHT_PHASE_HEADER, "setup");
        FilterChain chain = (req, res) -> {
            queryOnce(1);
            queryOnce(2);
        };

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(collector.getReport(Map.of()).repeatedQueries()).singleElement().satisfies(finding -> {
            assertThat(finding.httpMethod()).isEqualTo("GET");
            assertThat(finding.httpEndpoint()).isEqualTo("/api/courses/{courseId}");
            assertThat(finding.testName()).isEqualTo("A test");
            assertThat(finding.phase()).isEqualTo("setup");
            assertThat(finding.executions()).isEqualTo(2);
        });
    }

    @Test
    void theRawUriIsUsedWhenNoRouteWasResolved() throws ServletException, IOException {
        FilterChain chain = (req, res) -> {
            queryOnce(1);
            queryOnce(2);
        };

        filter.doFilter(new MockHttpServletRequest("GET", "/api/missing"), new MockHttpServletResponse(), chain);

        assertThat(collector.getReport(Map.of()).repeatedQueries()).singleElement().satisfies(finding -> assertThat(finding.httpEndpoint()).isEqualTo("/api/missing"));
    }

    @Test
    void theStateIsClearedAfterEachRequestAlsoWhenTheChainFails() {
        FilterChain failing = (req, res) -> {
            queryOnce(1);
            queryOnce(2);
            throw new ServletException("rejected");
        };

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/api/x"), new MockHttpServletResponse(), failing)).isInstanceOf(ServletException.class);

        // the failed request was reported, and nothing of it is left to be counted into the next request of this thread
        assertThat(collector.getReport(Map.of()).repeatedQueries()).singleElement().satisfies(finding -> assertThat(finding.httpEndpoint()).isEqualTo("/api/x"));
        collector.finishRequest("GET", "/api/y", null, null);
        assertThat(collector.getReport(Map.of()).repeatedQueries()).hasSize(1);
    }
}
