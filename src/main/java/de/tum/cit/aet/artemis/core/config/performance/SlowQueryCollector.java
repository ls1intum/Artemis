package de.tum.cit.aet.artemis.core.config.performance;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_E2E_PERFORMANCE;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

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
     * We use a {@link ConcurrentHashMap} of {@link AtomicInteger} so that concurrent queries
     * within the same request (e.g. async DB calls) can increment safely.
     * Entries are created lazily and cleared when the request finishes (via {@link #resetRequestState}).
     */
    private final ConcurrentHashMap<String, AtomicInteger> requestFrequencies = new ConcurrentHashMap<>();

    /**
     * Detected N+1 suspects, accumulated over the lifetime of the E2E run.
     * A suspect is promoted from {@code requestFrequencies} once its count crosses the threshold.
     */
    private final CopyOnWriteArrayList<N1Suspect> n1Suspects = new CopyOnWriteArrayList<>();

    public SlowQueryCollector(SlowQueryProperties properties) {
        this.properties = properties;
    }

    /**
     * Records a completed query execution. Called by {@link SlowQueryListener} after every
     * JDBC statement.
     *
     * @param normalizedSql   SQL with literals stripped (produced by {@link SlowQueryListener}).
     * @param executionTimeMs Measured wall-clock execution time in milliseconds.
     * @param httpMethod      HTTP verb of the triggering request; may be {@code null}.
     * @param httpEndpoint    URI path of the triggering request; may be {@code null}.
     * @param testName        Playwright test name from the {@code X-Playwright-Test-Name} header;
     *                            may be {@code null}.
     */
    public void record(String normalizedSql, long executionTimeMs, String httpMethod, String httpEndpoint, String testName) {

        // --- Slow-query detection ---
        if (executionTimeMs >= properties.getSlowQueryThresholdMs()) {
            if (slowQueries.size() < properties.getMaxRecordedQueries()) {
                slowQueries.add(new SlowQueryRecord(normalizedSql, executionTimeMs, httpMethod, httpEndpoint, testName, Instant.now()));
                log.debug("[SlowQuery] {}ms | {} {} | test='{}' | sql={}", executionTimeMs, httpMethod, httpEndpoint, testName, abbreviate(normalizedSql));
            }
            else {
                log.warn("[SlowQuery] Circuit-breaker: max {} entries reached, ignoring further slow queries", properties.getMaxRecordedQueries());
            }
        }

        // --- N+1 detection ---
        if (httpEndpoint != null) {
            String key = buildFrequencyKey(httpMethod, httpEndpoint, testName, normalizedSql);
            int count = requestFrequencies.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();

            if (count == properties.getN1DetectionThreshold() + 1) {
                // Promote to suspect the first time the threshold is crossed (avoid duplicates)
                n1Suspects.add(new N1Suspect(normalizedSql, count, httpMethod, httpEndpoint, testName));
                log.warn("[N+1 Suspect] {} occurrences of same query on {} {} | test='{}' | sql={}", count, httpMethod, httpEndpoint, testName, abbreviate(normalizedSql));
            }
            else if (count > properties.getN1DetectionThreshold() + 1) {
                // Update the occurrence count in the existing suspect entry
                updateN1SuspectCount(normalizedSql, httpMethod, httpEndpoint, testName, count);
            }
        }
    }

    /**
     * Clears the per-request frequency map. Should be called at the end of each HTTP request
     * (via a {@link jakarta.servlet.Filter}) so that frequency counts don't bleed across requests.
     */
    public void resetRequestState() {
        requestFrequencies.clear();
    }

    /**
     * Assembles and returns the current report.
     *
     * @return an immutable snapshot of all collected findings.
     */
    public SlowQueryReportDTO getReport() {
        List<SlowQueryRecord> slowSnapshot = new ArrayList<>(slowQueries);
        List<N1Suspect> n1Snapshot = new ArrayList<>(n1Suspects);
        return new SlowQueryReportDTO(Instant.now(), properties.getSlowQueryThresholdMs(), properties.getN1DetectionThreshold(), slowSnapshot.size(), n1Snapshot.size(),
                slowSnapshot, n1Snapshot);
    }

    /**
     * Resets all collected data. Called via the {@code POST .../reset} endpoint to allow
     * re-running the report collection mid-test-run if needed.
     */
    public void reset() {
        slowQueries.clear();
        n1Suspects.clear();
        requestFrequencies.clear();
        log.info("[SlowQuery] Collector reset");
    }

    // --------------------------------------------------
    // Private helpers
    // --------------------------------------------------

    private String buildFrequencyKey(String httpMethod, String httpEndpoint, String testName, String sql) {
        return (httpMethod != null ? httpMethod : "?") + "::" + httpEndpoint + "::" + (testName != null ? testName : "?") + "::" + sql;
    }

    private void updateN1SuspectCount(String sql, String httpMethod, String httpEndpoint, String testName, int newCount) {
        // CopyOnWriteArrayList doesn't support in-place mutation; rebuild the entry
        for (int i = 0; i < n1Suspects.size(); i++) {
            N1Suspect s = n1Suspects.get(i);
            if (s.normalizedSql().equals(sql) && objectsEqual(s.httpMethod(), httpMethod) && objectsEqual(s.httpEndpoint(), httpEndpoint) && objectsEqual(s.testName(), testName)) {
                n1Suspects.set(i, new N1Suspect(sql, newCount, httpMethod, httpEndpoint, testName));
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
    Map<String, AtomicInteger> getRequestFrequencies() {
        return requestFrequencies;
    }
}
