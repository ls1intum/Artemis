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
 * Covers the filter that times a request and clears its per-request state.
 */
class SlowQueryRequestFilterTest {

    private static final String SQL = "select * from participation where exercise_id=?";

    private SlowQueryCollector collector;

    private SlowQueryRequestFilter filter;

    @BeforeEach
    void setUp() {
        collector = new SlowQueryCollector(new SlowQueryProperties());
        filter = new SlowQueryRequestFilter(collector);
    }

    private void queryOnce(long signature) {
        collector.record(SQL, signature, 1, 0, () -> QueryCallSite.UNKNOWN, "GET", "/api/x", null, null, "tomcat-1");
    }

    @Test
    void aRequestThatRanQueriesIsTimedUnderItsRouteTemplate() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/courses/7");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/courses/{courseId}");
        request.addHeader(SlowQueryListener.PLAYWRIGHT_TEST_HEADER, "A%20test");
        request.addHeader(SlowQueryListener.PLAYWRIGHT_PHASE_HEADER, "setup");
        FilterChain chain = (req, res) -> queryOnce(1);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(collector.getReport(Map.of()).endpointTimings()).singleElement().satisfies(timing -> {
            assertThat(timing.httpEndpoint()).isEqualTo("/api/courses/{courseId}");
            assertThat(timing.testName()).isEqualTo("A test");
            assertThat(timing.phase()).isEqualTo("setup");
            assertThat(timing.queryCount()).isEqualTo(1);
        });
    }

    @Test
    void theRawUriIsUsedWhenNoRouteWasResolved() throws ServletException, IOException {
        FilterChain chain = (req, res) -> queryOnce(1);

        filter.doFilter(new MockHttpServletRequest("GET", "/api/missing"), new MockHttpServletResponse(), chain);

        assertThat(collector.getReport(Map.of()).endpointTimings()).singleElement().satisfies(timing -> assertThat(timing.httpEndpoint()).isEqualTo("/api/missing"));
    }

    @Test
    void theStateIsClearedAfterEachRequestAlsoWhenTheChainFails() {
        FilterChain failing = (req, res) -> {
            queryOnce(1);
            throw new ServletException("rejected");
        };

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/api/x"), new MockHttpServletResponse(), failing)).isInstanceOf(ServletException.class);

        // the failed request was timed, and nothing of it is left to be counted into the next request of this thread
        assertThat(collector.getReport(Map.of()).endpointTimings()).singleElement().satisfies(timing -> assertThat(timing.httpEndpoint()).isEqualTo("/api/x"));
        collector.recordEndpointTiming("GET", "/api/y", null, null, 1);
        assertThat(collector.getReport(Map.of()).endpointTimings()).hasSize(1);
    }
}
