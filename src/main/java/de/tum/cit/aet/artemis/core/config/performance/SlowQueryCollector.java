package de.tum.cit.aet.artemis.core.config.performance;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_E2E_PERFORMANCE;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Thread-safe in-memory store for the slow-query detector.
 * <p>
 * Collects two categories of findings:
 * <ul>
 * <li><b>Slow queries</b> – individual statements whose execution time exceeded the configured
 * threshold ({@link SlowQueryProperties#getSlowQueryThresholdMs()}).</li>
 * <li><b>N+1 suspects</b> – normalised SQL templates that were repeated more than
 * {@link SlowQueryProperties#getN1DetectionThreshold()} times within a single HTTP
 * request context, indicating a classic N+1 query problem.</li>
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
public class SlowQueryCollector {

    private static final Logger log = LoggerFactory.getLogger(SlowQueryCollector.class);

    private final SlowQueryProperties properties;

    /** Captured slow queries, ordered by insertion time. */
    private final CopyOnWriteArrayList<SlowQueryRecord> slowQueries = new CopyOnWriteArrayList<>();

    /**
     * Per-request frequency map: key = "httpMethod::httpEndpoint::testName::normalizedSql",
     * value = occurrence count within that request.
     * <p>
     * Scoped via {@link ThreadLocal} rather than a single shared map: Tomcat/Spring MVC handles
     * one HTTP request per thread (this collector is never used with async/reactive request
     * handling), so a thread-confined map gives each request its own counters without any
     * cross-request synchronization. This matters because a shared map cleared by
     * {@link #resetRequestState()} in a servlet {@link jakarta.servlet.Filter}'s {@code finally}
     * block would be reset by *any* request completing — under parallel E2E test workers, one
     * request finishing could wipe another still-in-flight request's counts mid-count, corrupting
     * both the occurrence totals and (since the promoted {@link N1Suspect} takes whichever
     * request's data happened to trigger promotion) the recorded {@code phase}.
     */
    private final ThreadLocal<Map<String, Integer>> requestFrequencies = ThreadLocal.withInitial(HashMap::new);

    /**
     * Detected N+1 suspects, accumulated over the lifetime of the E2E run.
     * A suspect is promoted from {@code requestFrequencies} once its count crosses the threshold.
     */
    private final CopyOnWriteArrayList<N1Suspect> n1Suspects = new CopyOnWriteArrayList<>();

    /**
     * Per-request query breakdown: key = normalized SQL, value = {@code [count, totalDurationMs]}.
     * Unlike {@link #requestFrequencies} (used only to decide when to promote an N+1 suspect),
     * this accumulates EVERY query -- not just repeated ones -- so {@link #recordEndpointTiming}
     * can report the complete query mix for a request, not just its outliers. Thread-confined for
     * the same reason as {@link #requestFrequencies}: one HTTP request per thread under Spring MVC.
     */
    private final ThreadLocal<Map<String, long[]>> requestQueryBreakdown = ThreadLocal.withInitial(HashMap::new);

    /** One entry per completed HTTP request, capped like {@link #slowQueries} below. */
    private final CopyOnWriteArrayList<EndpointTimingRecord> endpointTimings = new CopyOnWriteArrayList<>();

    public SlowQueryCollector(SlowQueryProperties properties) {
        this.properties = properties;
    }

    /**
     * Records a completed query execution. Called by {@link SlowQueryListener} after every
     * JDBC statement.
     *
     * @param normalizedSql   SQL with literals stripped (produced by {@link SlowQueryListener}).
     * @param executionTimeMs Measured wall-clock execution time in milliseconds.
     * @param joinCount       Number of SQL {@code join} keywords in the raw query text -- see
     *                            {@link SlowQueryRecord#joinCount()}.
     * @param httpMethod      HTTP verb of the triggering request; may be {@code null}.
     * @param httpEndpoint    URI path of the triggering request; may be {@code null}.
     * @param testName        Playwright test name from the {@code X-Playwright-Test-Name} header;
     *                            may be {@code null}.
     * @param phase           Playwright phase from the {@code X-Playwright-Phase} header — {@code "action"}
     *                            for requests the browser page itself issued, {@code "setup"} for
     *                            {@code page.request}/{@code context.request} traffic; may be {@code null}.
     * @param threadName      Name of the executing thread, for attributing background/async queries
     *                            ({@code httpEndpoint == null}) to a subsystem without any request context.
     */
    public void record(String normalizedSql, long executionTimeMs, int joinCount, String httpMethod, String httpEndpoint, String testName, String phase, String threadName) {

        // --- Per-request query breakdown (feeds recordEndpointTiming; every query, not just
        // outliers) ---
        if (httpEndpoint != null) {
            long[] entry = requestQueryBreakdown.get().computeIfAbsent(normalizedSql, k -> new long[3]);
            entry[0]++;
            entry[1] += executionTimeMs;
            entry[2] = joinCount; // same template -> same join count every time; plain assignment is fine
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

        // --- N+1 detection ---
        if (httpEndpoint != null) {
            String key = buildFrequencyKey(httpMethod, httpEndpoint, testName, normalizedSql);
            int count = requestFrequencies.get().merge(key, 1, Integer::sum);

            if (count == properties.getN1DetectionThreshold() + 1) {
                // Promote to suspect the first time the threshold is crossed (avoid duplicates)
                n1Suspects.add(new N1Suspect(normalizedSql, count, httpMethod, httpEndpoint, testName, phase));
                log.warn("[N+1 Suspect] {} occurrences of same query on {} {} | test='{}' | phase='{}' | sql={}", count, httpMethod, httpEndpoint, testName, phase,
                        abbreviate(normalizedSql));
            }
            else if (count > properties.getN1DetectionThreshold() + 1) {
                // Update the occurrence count in the existing suspect entry
                updateN1SuspectCount(normalizedSql, httpMethod, httpEndpoint, testName, phase, count);
            }
        }
    }

    /**
     * Clears the calling thread's frequency map. Should be called at the end of each HTTP request
     * (via a {@link jakarta.servlet.Filter}, on the same request-handling thread) so that
     * frequency counts don't bleed into whichever request this thread handles next.
     */
    public void resetRequestState() {
        requestFrequencies.remove();
    }

    /**
     * Assembles and records the calling thread's accumulated query breakdown as one
     * {@link EndpointTimingRecord}, then clears it. Should be called once per HTTP request, on
     * the same request-handling thread, after {@link #record} has been called for every query the
     * request triggered but before {@link #resetRequestState()} discards the (different) frequency
     * map -- order between the two doesn't matter, they're independent thread-locals.
     *
     * @param httpMethod      HTTP verb of the request.
     * @param httpEndpoint    URI path of the request.
     * @param testName        Playwright test name from the {@code X-Playwright-Test-Name} header; may be {@code null}.
     * @param phase           Playwright phase from the {@code X-Playwright-Phase} header; may be {@code null}.
     * @param totalDurationMs Wall-clock time for the whole request, measured by the caller.
     */
    public void recordEndpointTiming(String httpMethod, String httpEndpoint, String testName, String phase, long totalDurationMs) {
        Map<String, long[]> breakdown = requestQueryBreakdown.get();
        requestQueryBreakdown.remove();
        if (breakdown.isEmpty()) {
            // No queries at all (e.g. a static asset request never reached a repository) -- not
            // interesting enough to record a row for.
            return;
        }

        List<QueryCountEntry> queries = new ArrayList<>();
        long dbTimeMs = 0;
        int queryCount = 0;
        for (Map.Entry<String, long[]> e : breakdown.entrySet()) {
            int count = (int) e.getValue()[0];
            long totalMs = e.getValue()[1];
            int joinCount = (int) e.getValue()[2];
            queries.add(new QueryCountEntry(e.getKey(), count, totalMs, joinCount));
            dbTimeMs += totalMs;
            queryCount += count;
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
        List<N1Suspect> n1Snapshot = new ArrayList<>(n1Suspects);
        List<EndpointTimingRecord> endpointTimingSnapshot = new ArrayList<>(endpointTimings);
        return new SlowQueryReportDTO(Instant.now(), properties.getSlowQueryThresholdMs(), properties.getN1DetectionThreshold(), slowSnapshot.size(), n1Snapshot.size(),
                slowSnapshot, n1Snapshot, endpointTimingSnapshot);
    }

    /**
     * Resets all collected data. Called via the {@code POST .../reset} endpoint to allow
     * re-running the report collection mid-test-run if needed.
     * <p>
     * Does not touch {@link #requestFrequencies} or {@link #requestQueryBreakdown}: both are
     * thread-confined, self-clear via {@link #resetRequestState()}/{@link #recordEndpointTiming}
     * at the end of every request regardless of this call, and (being {@link ThreadLocal}s)
     * can't be cleared for other threads from here anyway.
     */
    public void reset() {
        slowQueries.clear();
        n1Suspects.clear();
        endpointTimings.clear();
        log.info("[SlowQuery] Collector reset");
    }

    // --------------------------------------------------
    // Private helpers
    // --------------------------------------------------

    private String buildFrequencyKey(String httpMethod, String httpEndpoint, String testName, String sql) {
        return (httpMethod != null ? httpMethod : "?") + "::" + httpEndpoint + "::" + (testName != null ? testName : "?") + "::" + sql;
    }

    private void updateN1SuspectCount(String sql, String httpMethod, String httpEndpoint, String testName, String phase, int newCount) {
        // CopyOnWriteArrayList doesn't support in-place mutation; rebuild the entry
        for (int i = 0; i < n1Suspects.size(); i++) {
            N1Suspect s = n1Suspects.get(i);
            if (s.normalizedSql().equals(sql) && objectsEqual(s.httpMethod(), httpMethod) && objectsEqual(s.httpEndpoint(), httpEndpoint) && objectsEqual(s.testName(), testName)) {
                n1Suspects.set(i, new N1Suspect(sql, newCount, httpMethod, httpEndpoint, testName, phase));
                return;
            }
        }
    }

    private static boolean objectsEqual(Object a, Object b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }

    private static String abbreviate(String s) {
        return s != null && s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }

    // Exposed for testing
    Map<String, Integer> getRequestFrequencies() {
        return requestFrequencies.get();
    }
}
