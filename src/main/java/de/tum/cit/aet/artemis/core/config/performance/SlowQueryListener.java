package de.tum.cit.aet.artemis.core.config.performance;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_E2E_PERFORMANCE;

import java.util.List;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;

/**
 * JDBC-level query execution listener, plugged into the datasource-proxy wrapper.
 * <p>
 * For every SQL statement that completes, this listener:
 * <ol>
 * <li>Normalises the SQL (strips literal values, collapses IN-lists).</li>
 * <li>Extracts the current HTTP request context from Spring's {@code RequestContextHolder}
 * (method, URI path, and the {@code X-Playwright-Test-Name}/{@code X-Playwright-Phase} headers
 * injected by the Playwright test fixture).</li>
 * <li>Delegates to {@link SlowQueryCollector#record} for threshold evaluation and storage.</li>
 * </ol>
 * <p>
 * Only active when the {@code e2e-performance} Spring profile is enabled.
 */
@Component
@Profile(SPRING_PROFILE_E2E_PERFORMANCE)
public class SlowQueryListener implements QueryExecutionListener {

    /** HTTP header injected by the Playwright {@code baseFixtures.ts} fixture. */
    static final String PLAYWRIGHT_TEST_HEADER = "X-Playwright-Test-Name";

    /**
     * HTTP header injected by the Playwright {@code baseFixtures.ts} fixture. {@code "setup"} by
     * default (baked in via {@code contextOptions}, so it covers {@code page.request}/
     * {@code context.request} traffic used by test fixtures); overridden to {@code "action"} for
     * requests the browser page itself issues, via a {@code page.route()} handler — see the
     * comment on {@code autoTestFixture} in {@code baseFixtures.ts} for why that split reliably
     * separates test setup from the action a test is actually verifying.
     */
    static final String PLAYWRIGHT_PHASE_HEADER = "X-Playwright-Phase";

    // Regex patterns used for SQL normalisation
    private static final Pattern LITERAL_STRINGS = Pattern.compile("'[^']*'");

    private static final Pattern LITERAL_NUMBERS = Pattern.compile("\\b\\d+(\\.\\d+)?\\b");

    private static final Pattern IN_LIST_VALUES = Pattern.compile("IN\\s*\\([^)]+\\)", Pattern.CASE_INSENSITIVE);

    private static final Pattern WHITESPACE_RUNS = Pattern.compile("\\s+");

    private final SlowQueryCollector collector;

    public SlowQueryListener(SlowQueryCollector collector) {
        this.collector = collector;
    }

    @Override
    public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        // Nothing to do before execution
    }

    @Override
    public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        if (queryInfoList.isEmpty()) {
            return;
        }

        long executionTimeMs = execInfo.getElapsedTime();
        String rawSql = queryInfoList.get(0).getQuery();
        String normalizedSql = normalizeSql(rawSql);

        // Extract HTTP context — may be null for async/background queries
        String httpMethod = null;
        String httpEndpoint = null;
        String testName = null;
        String phase = null;

        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                httpMethod = req.getMethod();
                httpEndpoint = req.getRequestURI();
                testName = req.getHeader(PLAYWRIGHT_TEST_HEADER);
                phase = req.getHeader(PLAYWRIGHT_PHASE_HEADER);
            }
        }
        catch (IllegalStateException ignored) {
            // No request context bound (e.g. startup, scheduled tasks)
        }

        collector.record(normalizedSql, executionTimeMs, httpMethod, httpEndpoint, testName, phase);
    }

    /**
     * Produces a canonical, parameter-free representation of a SQL statement.
     * This allows counting how many times the "same" query shape is executed
     * regardless of which specific IDs or strings were used.
     */
    static String normalizeSql(String rawSql) {
        if (rawSql == null) {
            return null;
        }
        String sql = rawSql;
        sql = LITERAL_STRINGS.matcher(sql).replaceAll("?");
        sql = IN_LIST_VALUES.matcher(sql).replaceAll("IN (?)");
        sql = LITERAL_NUMBERS.matcher(sql).replaceAll("?");
        sql = WHITESPACE_RUNS.matcher(sql).replaceAll(" ").trim();
        return sql;
    }
}
