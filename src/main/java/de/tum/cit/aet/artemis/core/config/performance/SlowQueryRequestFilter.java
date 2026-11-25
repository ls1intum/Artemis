package de.tum.cit.aet.artemis.core.config.performance;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_E2E_PERFORMANCE;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Servlet filter that turns the queries of each HTTP request into repeated-query findings and then
 * clears the per-request counters of the {@link SlowQueryCollector}, so that a query run once per
 * request is never counted as repeated across requests.
 * <p>
 * Runs with the highest precedence, so it wraps every other filter: the findings cover the whole
 * request, and the reset also runs when an earlier filter (e.g. security) rejects the request.
 * Only active when the {@code e2e-performance} Spring profile is enabled.
 */
@Component
@Profile(SPRING_PROFILE_E2E_PERFORMANCE)
@Lazy
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SlowQueryRequestFilter implements Filter {

    /** Request header set by the Playwright {@code baseFixtures.ts} fixture: the test title, percent-encoded because header values must be ASCII. */
    static final String PLAYWRIGHT_TEST_HEADER = "X-Playwright-Test-Name";

    /** Request header set by the Playwright fixture: {@code "action"} for what the browser page issued, {@code "setup"} for the test's own API calls. */
    static final String PLAYWRIGHT_PHASE_HEADER = "X-Playwright-Phase";

    private final SlowQueryCollector collector;

    public SlowQueryRequestFilter(SlowQueryCollector collector) {
        this.collector = collector;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        }
        finally {
            if (request instanceof HttpServletRequest httpRequest) {
                // The resolved route template ("/api/admin/courses/{courseId}"), not the raw URI, or
                // every path-variable value would be an endpoint of its own. It is set by the handler
                // mapping, so it is only available once the request has completed; a request that
                // never resolved to a handler (e.g. a 404) falls back to the raw URI.
                Object routeTemplate = httpRequest.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                String httpEndpoint = routeTemplate != null ? routeTemplate.toString() : httpRequest.getRequestURI();
                collector.finishRequest(httpRequest.getMethod(), httpEndpoint, testName(httpRequest), httpRequest.getHeader(PLAYWRIGHT_PHASE_HEADER));
            }
            collector.resetRequestState();
        }
    }

    static String testName(HttpServletRequest request) {
        String header = request.getHeader(PLAYWRIGHT_TEST_HEADER);
        if (header == null) {
            return null;
        }
        try {
            return URLDecoder.decode(header, StandardCharsets.UTF_8);
        }
        catch (IllegalArgumentException _) {
            return header;
        }
    }
}
