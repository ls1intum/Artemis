package de.tum.cit.aet.artemis.core.config.performance;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_E2E_PERFORMANCE;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.proxy.ParameterSetOperation;

/**
 * JDBC-level query execution listener, plugged into the datasource-proxy wrapper.
 * <p>
 * For every SQL statement that completes, this listener:
 * <ol>
 * <li>Normalises the SQL (strips literal values, collapses IN-lists).</li>
 * <li>Extracts the current HTTP request context from Spring's {@code RequestContextHolder}
 * (method, URI path, and the {@code X-Playwright-Test-Name}/{@code X-Playwright-Phase} headers
 * injected by the Playwright test fixture).</li>
 * <li>Computes a parameter signature (raw SQL + bound values), which tells an N+1 (same template,
 * different values) apart from a duplicate (same template, same values).</li>
 * <li>Delegates to {@link SlowQueryCollector#record} for threshold evaluation and storage.</li>
 * </ol>
 * <p>
 * Only active when the {@code e2e-performance} Spring profile is enabled.
 */
@Component
@Profile(SPRING_PROFILE_E2E_PERFORMANCE)
@Lazy
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

    // \b before IN is required -- without it this also matches the "IN" inside "JOIN" whenever a
    // subquery follows (e.g. "join (select ...) alias"), corrupting it to "joIN (?)" and, since
    // [^)]+ can't cross into the subquery's own nested parens, often leaving a stray trailing ')'
    // behind too (stops at the first ')' found, e.g. an inner count(*)'s close).
    private static final Pattern IN_LIST_VALUES = Pattern.compile("\\bIN\\s*\\([^)]+\\)", Pattern.CASE_INSENSITIVE);

    private static final Pattern WHITESPACE_RUNS = Pattern.compile("\\s+");

    /**
     * Matches the SQL {@code join} keyword (any variant -- {@code inner}/{@code left}/{@code right}
     * join all end in the literal word "join"), used to count how many tables a query touches. A
     * purely structural signal, unlike duration: the same query joins the same number of tables
     * regardless of how fast or slow the environment it runs in happens to be.
     */
    private static final Pattern JOIN_KEYWORD = Pattern.compile("\\bjoin\\b", Pattern.CASE_INSENSITIVE);

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
        QueryInfo queryInfo = queryInfoList.get(0);
        String rawSql = queryInfo.getQuery();
        String normalizedSql = normalizeSql(rawSql);
        long parameterSignature = parameterSignature(rawSql, queryInfo.getParametersList());
        int joinCount = countJoins(rawSql);

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
                // The resolved route template (e.g. "/api/admin/courses/{courseId}"), not the raw
                // URI ("/api/admin/courses/9003") -- otherwise every distinct path-variable value
                // (course ID, exam ID, ...) would look like a different endpoint, splitting what
                // is actually one recurring pattern into dozens of near-duplicate ones. Same
                // attribute Micrometer's web MVC metrics use, and for the same cardinality reason.
                // Set by Spring's handler mapping before the controller method runs, so it's
                // already populated by the time any query fires; falls back to the raw URI for
                // requests that never resolved to a handler (e.g. a 404).
                Object routeTemplate = req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                httpEndpoint = routeTemplate != null ? routeTemplate.toString() : req.getRequestURI();
                testName = testName(req);
                phase = req.getHeader(PLAYWRIGHT_PHASE_HEADER);
            }
        }
        catch (IllegalStateException ignored) {
            // No request context bound (e.g. startup, scheduled tasks)
        }

        collector.record(normalizedSql, parameterSignature, executionTimeMs, joinCount, QueryCallSite::resolve, httpMethod, httpEndpoint, testName, phase,
                Thread.currentThread().getName());
    }

    /**
     * A hash of the raw SQL text and every bound parameter value: two executions get the same
     * signature exactly when they ran the same statement with the same values. The raw SQL is
     * included (not just the parameters) because Hibernate inlines some values as literals, which
     * normalisation strips from the template but which still make two statements different. For
     * a JDBC batch, all of its parameter sets are hashed together, so one batch counts as one
     * execution -- batching is the fix for one-statement-per-item, not an instance of it.
     * <p>
     * A 64-bit hash rather than the values themselves, to keep per-request memory flat for large
     * parameter lists; a collision would at worst merge two parameter sets of the same template
     * within one request, which is negligible at 64 bits.
     */
    static long parameterSignature(String rawSql, List<List<ParameterSetOperation>> parametersList) {
        long hash = rawSql != null ? rawSql.hashCode() : 0;
        if (parametersList != null) {
            for (List<ParameterSetOperation> parameterSet : parametersList) {
                // each operation is one setXxx(index, value, ...) call; summed, so the order of the
                // calls does not matter, and hashed by content (deepHashCode also covers byte[] values,
                // whose plain hashCode is their identity)
                long setHash = 0;
                for (ParameterSetOperation operation : parameterSet) {
                    setHash += Arrays.deepHashCode(operation.getArgs());
                }
                hash = hash * 1_000_003L + setHash;
            }
        }
        return hash;
    }

    /**
     * The Playwright test title of the request, which the fixture percent-encodes because HTTP
     * header values must be ASCII and test titles may contain characters such as {@code ✕}.
     */
    static String testName(HttpServletRequest request) {
        String header = request.getHeader(PLAYWRIGHT_TEST_HEADER);
        if (header == null) {
            return null;
        }
        try {
            return URLDecoder.decode(header, StandardCharsets.UTF_8);
        }
        catch (IllegalArgumentException malformed) {
            return header;
        }
    }

    /** Counts SQL {@code join} keywords in the raw (un-normalised) query text. */
    static int countJoins(String rawSql) {
        if (rawSql == null) {
            return 0;
        }
        Matcher matcher = JOIN_KEYWORD.matcher(rawSql);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
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
