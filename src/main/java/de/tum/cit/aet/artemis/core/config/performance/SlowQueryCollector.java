package de.tum.cit.aet.artemis.core.config.performance;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_E2E_PERFORMANCE;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Thread-safe in-memory store for the slow-query detector.
 * <p>
 * Collects three categories of findings:
 * <ul>
 * <li><b>Slow queries</b> – individual statements whose execution time exceeded the configured
 * threshold ({@link SlowQueryProperties#getSlowQueryThresholdMs()}).</li>
 * <li><b>Repeated queries</b> – query templates that ran more than once within a single HTTP
 * request, either with different parameters (N+1) or with identical ones (duplicate); see
 * {@link RepeatedQueryFinding}.</li>
 * <li><b>Endpoint timings</b> – one entry per HTTP request, regardless of whether any individual
 * query was slow: total query count, DB time, and a per-query breakdown, so an endpoint whose
 * total DB engagement is high across many individually-unremarkable queries is still visible.
 * Deliberately unfiltered (no threshold of its own yet) -- see {@link EndpointTimingRecord}.</li>
 * </ul>
 * <p>
 * Only active when the {@code e2e-performance} Spring profile is enabled.
 */
@Component
@Profile(SPRING_PROFILE_E2E_PERFORMANCE)
@Lazy
public class SlowQueryCollector {

    private static final Logger log = LoggerFactory.getLogger(SlowQueryCollector.class);

    private final SlowQueryProperties properties;

    /** Captured slow queries, ordered by insertion time. */
    private final CopyOnWriteArrayList<SlowQueryRecord> slowQueries = new CopyOnWriteArrayList<>();

    /**
     * Per-request statistics, keyed by normalized SQL template; every query of the request, not
     * just outliers.
     * <p>
     * Scoped via {@link ThreadLocal} rather than a single shared map: Tomcat/Spring MVC handles
     * one HTTP request per thread (this collector is never used with async/reactive request
     * handling), so a thread-confined map gives each request its own counters without any
     * cross-request synchronization. A shared map cleared at the end of *any* request would, under
     * parallel E2E test workers, wipe another still-in-flight request's counts mid-count.
     */
    private final ThreadLocal<Map<String, TemplateStats>> requestTemplates = ThreadLocal.withInitial(HashMap::new);

    /** Repeated-query findings, one per (request, template, shape), capped like {@link #slowQueries}. */
    private final CopyOnWriteArrayList<RepeatedQueryFinding> repeatedQueries = new CopyOnWriteArrayList<>();

    /** One entry per completed HTTP request, capped like {@link #slowQueries}. */
    private final CopyOnWriteArrayList<EndpointTimingRecord> endpointTimings = new CopyOnWriteArrayList<>();

    public SlowQueryCollector(SlowQueryProperties properties) {
        this.properties = properties;
    }

    /**
     * Records a completed query execution. Called by {@link SlowQueryListener} after every
     * JDBC statement.
     *
     * @param normalizedSql      SQL with literals stripped (produced by {@link SlowQueryListener}).
     * @param parameterSignature hash of the raw SQL and its bound parameter values: equal for two
     *                               executions of the exact same statement with the exact same
     *                               values, different otherwise -- see {@link SlowQueryListener#parameterSignature}.
     * @param executionTimeMs    Measured wall-clock execution time in milliseconds.
     * @param joinCount          Number of SQL {@code join} keywords in the raw query text -- see
     *                               {@link SlowQueryRecord#joinCount()}.
     * @param callSiteResolver   resolves where the query was issued from; only invoked on the
     *                               second execution of a template within a request (the first
     *                               point at which it can become a finding), so the stack walk
     *                               is not paid for every query. Must be invoked on the query's
     *                               own thread, which this method guarantees by calling it inline.
     * @param httpMethod         HTTP verb of the triggering request; may be {@code null}.
     * @param httpEndpoint       route template of the triggering request; {@code null} for
     *                               background/async queries without a request.
     * @param testName           Playwright test name from the {@code X-Playwright-Test-Name} header;
     *                               may be {@code null}.
     * @param phase              Playwright phase from the {@code X-Playwright-Phase} header — {@code "action"}
     *                               for requests the browser page itself issued, {@code "setup"} for
     *                               {@code page.request}/{@code context.request} traffic; may be {@code null}.
     * @param threadName         Name of the executing thread, for attributing background/async queries
     *                               ({@code httpEndpoint == null}) to a subsystem without any request context.
     */
    public void record(String normalizedSql, long parameterSignature, long executionTimeMs, int joinCount, Supplier<QueryCallSite> callSiteResolver, String httpMethod,
            String httpEndpoint, String testName, String phase, String threadName) {

        // --- Per-request statistics (feed both repeated-query detection and the endpoint timing) ---
        if (httpEndpoint != null) {
            TemplateStats stats = requestTemplates.get().computeIfAbsent(normalizedSql, k -> new TemplateStats(joinCount));
            stats.executions++;
            stats.totalDurationMs += executionTimeMs;
            stats.parameterSignatures.merge(parameterSignature, 1, Integer::sum);
            if (stats.executions == 2) {
                stats.callSite = callSiteResolver.get();
            }
        }

        // --- Slow-query detection ---
        if (executionTimeMs >= properties.getSlowQueryThresholdMs()) {
            if (slowQueries.size() < properties.getMaxRecordedQueries()) {
                slowQueries.add(new SlowQueryRecord(normalizedSql, executionTimeMs, joinCount, httpMethod, httpEndpoint, testName, phase, threadName, Instant.now()));
                log.debug("[SlowQuery] {}ms | {} {} | test='{}' | phase='{}' | thread='{}' | sql={}", executionTimeMs, httpMethod, httpEndpoint, testName, phase, threadName,
                        abbreviate(normalizedSql));
            }
            else {
                log.warn("[SlowQuery] Circuit-breaker: max {} entries reached, ignoring further slow queries", properties.getMaxRecordedQueries());
            }
        }
    }

    /**
     * Discards the calling thread's per-request statistics. Called at the end of each HTTP
     * request (via a {@link jakarta.servlet.Filter}, on the same request-handling thread), after
     * {@link #recordEndpointTiming}, so that nothing bleeds into whichever request this thread
     * handles next -- also when {@link #recordEndpointTiming} was never reached.
     */
    public void resetRequestState() {
        requestTemplates.remove();
    }

    /**
     * Turns the calling thread's accumulated per-request statistics into one
     * {@link EndpointTimingRecord} and its {@link RepeatedQueryFinding}s, then clears them. Called
     * once per HTTP request, on the same request-handling thread, after {@link #record} has been
     * called for every query the request triggered.
     *
     * @param httpMethod      HTTP verb of the request.
     * @param httpEndpoint    route template of the request.
     * @param testName        Playwright test name from the {@code X-Playwright-Test-Name} header; may be {@code null}.
     * @param phase           Playwright phase from the {@code X-Playwright-Phase} header; may be {@code null}.
     * @param totalDurationMs Wall-clock time for the whole request, measured by the caller.
     */
    public void recordEndpointTiming(String httpMethod, String httpEndpoint, String testName, String phase, long totalDurationMs) {
        Map<String, TemplateStats> templates = requestTemplates.get();
        requestTemplates.remove();
        if (templates.isEmpty()) {
            // No queries at all (e.g. a static asset request never reached a repository) -- not
            // interesting enough to record a row for.
            return;
        }

        List<QueryCountEntry> queries = new ArrayList<>();
        long dbTimeMs = 0;
        int queryCount = 0;
        for (Map.Entry<String, TemplateStats> e : templates.entrySet()) {
            TemplateStats stats = e.getValue();
            queries.add(new QueryCountEntry(e.getKey(), stats.executions, stats.totalDurationMs, stats.joinCount));
            dbTimeMs += stats.totalDurationMs;
            queryCount += stats.executions;
            addRepeatedQueryFindings(e.getKey(), stats, httpMethod, httpEndpoint, testName, phase);
        }

        if (endpointTimings.size() < properties.getMaxRecordedQueries()) {
            endpointTimings.add(new EndpointTimingRecord(httpMethod, httpEndpoint, testName, phase, totalDurationMs, dbTimeMs, queryCount, queries, Instant.now()));
        }
        else {
            log.warn("[EndpointTiming] Circuit-breaker: max {} entries reached, ignoring further endpoint timings", properties.getMaxRecordedQueries());
        }
    }

    /**
     * Assembles and returns the current report.
     *
     * @return an immutable snapshot of all collected findings.
     */
    public SlowQueryReportDTO getReport() {
        List<SlowQueryRecord> slowSnapshot = new ArrayList<>(slowQueries);
        List<RepeatedQueryFinding> repeatedSnapshot = new ArrayList<>(repeatedQueries);
        List<EndpointTimingRecord> endpointTimingSnapshot = new ArrayList<>(endpointTimings);
        return new SlowQueryReportDTO(Instant.now(), properties.getSlowQueryThresholdMs(), slowSnapshot.size(), repeatedSnapshot.size(), slowSnapshot, repeatedSnapshot,
                endpointTimingSnapshot);
    }

    /**
     * Resets all collected data. Called via the {@code POST .../reset} endpoint to allow
     * re-running the report collection mid-test-run if needed.
     * <p>
     * Does not touch {@link #requestTemplates}: it is thread-confined, self-clears at the end of
     * every request regardless of this call, and (being a {@link ThreadLocal}) can't be cleared
     * for other threads from here anyway.
     */
    public void reset() {
        slowQueries.clear();
        repeatedQueries.clear();
        endpointTimings.clear();
        log.info("[SlowQuery] Collector reset");
    }

    // --------------------------------------------------
    // Private helpers
    // --------------------------------------------------

    private void addRepeatedQueryFindings(String sql, TemplateStats stats, String httpMethod, String httpEndpoint, String testName, String phase) {
        if (stats.executions < 2) {
            return;
        }
        int distinct = stats.parameterSignatures.size();
        QueryCallSite site = stats.callSite != null ? stats.callSite : QueryCallSite.UNKNOWN;
        if (distinct >= 2) {
            addRepeatedQueryFinding(new RepeatedQueryFinding(RepeatedQueryFinding.Type.N_PLUS_ONE, sql, stats.executions, distinct, site.repositoryMethod(), site.callerMethod(),
                    httpMethod, httpEndpoint, testName, phase));
        }
        if (distinct < stats.executions) {
            addRepeatedQueryFinding(new RepeatedQueryFinding(RepeatedQueryFinding.Type.DUPLICATE, sql, stats.executions, distinct, site.repositoryMethod(), site.callerMethod(),
                    httpMethod, httpEndpoint, testName, phase));
        }
    }

    private void addRepeatedQueryFinding(RepeatedQueryFinding finding) {
        if (repeatedQueries.size() < properties.getMaxRecordedQueries()) {
            repeatedQueries.add(finding);
        }
        else {
            log.warn("[RepeatedQuery] Circuit-breaker: max {} entries reached, ignoring further repeated-query findings", properties.getMaxRecordedQueries());
        }
    }

    private static String abbreviate(String s) {
        return s != null && s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }

    /** Mutable per-request accumulator for one SQL template; only ever touched by the request's own thread. */
    private static final class TemplateStats {

        private final int joinCount; // same template -> same join count every time

        private int executions;

        private long totalDurationMs;

        /** parameter signature -> how many times exactly that statement ran. */
        private final Map<Long, Integer> parameterSignatures = new HashMap<>();

        private QueryCallSite callSite;

        private TemplateStats(int joinCount) {
            this.joinCount = joinCount;
        }
    }
}
