package de.tum.cit.aet.artemis.core.config.performance;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_E2E_PERFORMANCE;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Servlet filter that times each HTTP request end-to-end and clears the per-request N+1
 * frequency counters in {@link SlowQueryCollector} after each HTTP request completes.
 * <p>
 * Without the reset, frequency counts would accumulate across requests and produce
 * false N+1 positives (e.g. a query executed once per request over 10 requests would
 * appear as if it was executed 10 times within one request).
 * <p>
 * Runs with the lowest precedence so all application logic completes first.
 * Only active when the {@code e2e-performance} Spring profile is enabled.
 */
@Component
@Profile(SPRING_PROFILE_E2E_PERFORMANCE)
@Order(Ordered.LOWEST_PRECEDENCE)
public class SlowQueryRequestFilter implements Filter {

    private final SlowQueryCollector collector;

    public SlowQueryRequestFilter(SlowQueryCollector collector) {
        this.collector = collector;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        long startNanos = System.nanoTime();
        try {
            chain.doFilter(request, response);
        }
        finally {
            if (request instanceof HttpServletRequest httpRequest) {
                long totalDurationMs = (System.nanoTime() - startNanos) / 1_000_000;
                collector.recordEndpointTiming(httpRequest.getMethod(), httpRequest.getRequestURI(), httpRequest.getHeader(SlowQueryListener.PLAYWRIGHT_TEST_HEADER),
                        httpRequest.getHeader(SlowQueryListener.PLAYWRIGHT_PHASE_HEADER), totalDurationMs);
            }
            collector.resetRequestState();
        }
    }
}
