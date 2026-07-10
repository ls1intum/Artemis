package de.tum.cit.aet.artemis.core.config.performance;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_E2E_PERFORMANCE;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Servlet filter that clears the per-request N+1 frequency counters in
 * {@link SlowQueryCollector} after each HTTP request completes.
 * <p>
 * Without this reset, frequency counts would accumulate across requests and produce
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
        try {
            chain.doFilter(request, response);
        }
        finally {
            collector.resetRequestState();
        }
    }
}
