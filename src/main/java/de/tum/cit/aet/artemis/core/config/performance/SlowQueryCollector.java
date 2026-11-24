package de.tum.cit.aet.artemis.core.config.performance;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_E2E_PERFORMANCE;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Thread-safe in-memory store for the repeated-query detector.
 * <p>
 * Collects the query templates that ran more than once within a single HTTP request, either with
 * different parameters (N+1) or with identical ones (duplicate); see {@link RepeatedQueryFinding}.
 * <p>
 * Only active when the {@code e2e-performance} Spring profile is enabled.
 */
@Component
@Profile(SPRING_PROFILE_E2E_PERFORMANCE)
@Lazy
public class SlowQueryCollector {

    private static final Logger log = LoggerFactory.getLogger(SlowQueryCollector.class);

    private static final Comparator<QueryCallSite> CALL_SITE_ORDER = Comparator.comparing(QueryCallSite::callerMethod, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(QueryCallSite::repositoryMethod, Comparator.nullsLast(Comparator.naturalOrder()));

    private final SlowQueryProperties properties;

    /**
     * Per-request statistics, keyed by normalized SQL template and the code that issued it; every
     * query of the request, not just outliers. The issuing code is part of the key because one
     * template (e.g. loading a user by id) is run by many places in the same request, and each of
     * them is its own finding with its own count.
     * <p>
     * Scoped via {@link ThreadLocal} rather than a single shared map: Tomcat/Spring MVC handles
     * one HTTP request per thread (this collector is never used with async/reactive request
     * handling), so a thread-confined map gives each request its own counters without any
     * cross-request synchronization. A shared map cleared at the end of *any* request would, under
     * parallel E2E test workers, wipe another still-in-flight request's counts mid-count.
     */
    private final ThreadLocal<Map<TemplateKey, TemplateStats>> requestTemplates = ThreadLocal.withInitial(HashMap::new);

    /**
     * Repeated-query findings, see {@link #addRepeatedQueryFindings}: one per {@link FindingKey}, the request that
     * could have saved the most executions. Requests of the same test repeating the same finding therefore use no
     * further room, and the cap counts distinct findings. Guarded by its own monitor.
     */
    private final Map<FindingKey, RepeatedQueryFinding> repeatedQueries = new LinkedHashMap<>();

    /** Set once a repeated-query finding was dropped, which makes the set of findings of the run incomplete. Cleared by {@link #reset()}. */
    private volatile boolean truncated;

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
     * @param callSiteResolver   resolves where the query was issued from; invoked for every
     *                               query of a request, since each execution is counted under
     *                               the code that issued it, and not for background queries.
     *                               Must be invoked on the query's own thread, which this method
     *                               guarantees by calling it inline.
     * @param httpMethod         HTTP verb of the triggering request; may be {@code null}.
     * @param httpEndpoint       route template of the triggering request; {@code null} for
     *                               background/async queries without a request.
     * @param testName           Playwright test name from the {@code X-Playwright-Test-Name} header;
     *                               may be {@code null}.
     * @param phase              Playwright phase from the {@code X-Playwright-Phase} header — {@code "action"}
     *                               for requests the browser page itself issued, {@code "setup"} for
     *                               {@code page.request}/{@code context.request} traffic; may be {@code null}.
     */
    public void record(String normalizedSql, long parameterSignature, Supplier<QueryCallSite> callSiteResolver, String httpMethod, String httpEndpoint, String testName,
            String phase) {
        if (httpEndpoint != null) {
            TemplateKey key = new TemplateKey(normalizedSql, resolveCallSite(callSiteResolver));
            TemplateStats stats = requestTemplates.get().computeIfAbsent(key, k -> new TemplateStats());
            stats.executions++;
            stats.parameterSignatures.merge(parameterSignature, 1, Integer::sum);
        }
    }

    /**
     * Discards the calling thread's per-request statistics. Called at the end of each HTTP
     * request (via a {@link jakarta.servlet.Filter}, on the same request-handling thread), after
     * {@link #finishRequest}, so that nothing bleeds into whichever request this thread
     * handles next -- also when {@link #finishRequest} was never reached.
     */
    public void resetRequestState() {
        requestTemplates.remove();
    }

    /**
     * Turns the calling thread's accumulated per-request statistics into the request's
     * {@link RepeatedQueryFinding}s, then clears them. Called once per HTTP request,
     * on the same request-handling thread, after {@link #record} has been called for every query the request triggered.
     *
     * @param httpMethod   HTTP verb of the request.
     * @param httpEndpoint route template of the request.
     * @param testName     Playwright test name from the {@code X-Playwright-Test-Name} header; may be {@code null}.
     * @param phase        Playwright phase from the {@code X-Playwright-Phase} header; may be {@code null}.
     */
    public void finishRequest(String httpMethod, String httpEndpoint, String testName, String phase) {
        Map<TemplateKey, TemplateStats> templates = requestTemplates.get();
        requestTemplates.remove();
        if (templates.isEmpty()) {
            return;
        }
        addRepeatedQueryFindings(templates, httpMethod, httpEndpoint, testName, phase);
    }

    /**
     * Assembles and returns the current report.
     *
     * @param indexes the indexes of every table, passed through to the report (see {@link IndexCatalog}).
     * @return an immutable snapshot of all collected findings.
     */
    public SlowQueryReportDTO getReport(Map<String, List<TableIndex>> indexes) {
        List<RepeatedQueryFinding> repeatedSnapshot;
        synchronized (repeatedQueries) {
            repeatedSnapshot = new ArrayList<>(repeatedQueries.values());
        }
        return new SlowQueryReportDTO(Instant.now(), repeatedSnapshot.size(), repeatedSnapshot, indexes, truncated);
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
        synchronized (repeatedQueries) {
            repeatedQueries.clear();
        }
        truncated = false;
        log.info("[SlowQuery] Collector reset");
    }

    // --------------------------------------------------
    // Private helpers
    // --------------------------------------------------

    /**
     * Derives the request's repeated-query findings. Each piece of code that ran a template more
     * than once is one finding: N+1 if it used several parameter sets (its executions then include
     * any repeats of the same set), duplicate if it used only one. A statement that several pieces
     * of code ran with the same parameters is one more duplicate, of the request as a whole.
     */
    private void addRepeatedQueryFindings(Map<TemplateKey, TemplateStats> templates, String httpMethod, String httpEndpoint, String testName, String phase) {
        Map<String, Map<QueryCallSite, TemplateStats>> callSitesBySql = new LinkedHashMap<>();
        for (Map.Entry<TemplateKey, TemplateStats> e : templates.entrySet()) {
            String sql = e.getKey().sql();
            QueryCallSite site = e.getKey().callSite();
            TemplateStats stats = e.getValue();
            callSitesBySql.computeIfAbsent(sql, k -> new LinkedHashMap<>()).put(site, stats);
            if (stats.executions < 2) {
                continue;
            }
            int distinct = stats.parameterSignatures.size();
            RepeatedQueryFinding.Type type = distinct >= 2 ? RepeatedQueryFinding.Type.N_PLUS_ONE : RepeatedQueryFinding.Type.DUPLICATE;
            addRepeatedQueryFinding(new RepeatedQueryFinding(type, sql, stats.executions, distinct, site.repositoryMethod(), site.callerMethod(), site.sourceFile(), site.line(),
                    httpMethod, httpEndpoint, testName, phase, List.of()));
        }
        for (Map.Entry<String, Map<QueryCallSite, TemplateStats>> e : callSitesBySql.entrySet()) {
            if (e.getValue().size() >= 2) {
                addDuplicateAcrossCallSites(e.getKey(), e.getValue(), httpMethod, httpEndpoint, testName, phase);
            }
        }
    }

    /**
     * Reports the statements of one template that more than one piece of code ran with the same
     * parameters: the request loaded that data again, though no single place repeated it.
     */
    private void addDuplicateAcrossCallSites(String sql, Map<QueryCallSite, TemplateStats> callSites, String httpMethod, String httpEndpoint, String testName, String phase) {
        Map<Long, Set<QueryCallSite>> callSitesBySignature = new HashMap<>();
        Map<Long, Integer> executionsBySignature = new HashMap<>();
        callSites.forEach((site, stats) -> stats.parameterSignatures.forEach((signature, count) -> {
            callSitesBySignature.computeIfAbsent(signature, k -> new HashSet<>()).add(site);
            executionsBySignature.merge(signature, count, Integer::sum);
        }));
        int executions = 0;
        int distinct = 0;
        Set<QueryCallSite> involved = new HashSet<>();
        for (Map.Entry<Long, Set<QueryCallSite>> e : callSitesBySignature.entrySet()) {
            if (e.getValue().size() >= 2) {
                executions += executionsBySignature.get(e.getKey());
                distinct++;
                involved.addAll(e.getValue());
            }
        }
        if (distinct > 0) {
            List<QueryCallSite> sorted = involved.stream().sorted(CALL_SITE_ORDER).toList();
            addRepeatedQueryFinding(new RepeatedQueryFinding(RepeatedQueryFinding.Type.DUPLICATE, sql, executions, distinct, null, null, null, null, httpMethod, httpEndpoint,
                    testName, phase, sorted));
        }
    }

    /**
     * Resolves the call site without letting a failure reach the query: the listener runs inside
     * the application's JDBC call, so an exception here would fail the request being measured.
     */
    private static QueryCallSite resolveCallSite(Supplier<QueryCallSite> callSiteResolver) {
        try {
            QueryCallSite site = callSiteResolver.get();
            return site != null ? site : QueryCallSite.UNKNOWN;
        }
        catch (RuntimeException e) {
            log.debug("[RepeatedQuery] Could not resolve the call site of a query", e);
            return QueryCallSite.UNKNOWN;
        }
    }

    private void addRepeatedQueryFinding(RepeatedQueryFinding finding) {
        FindingKey key = new FindingKey(finding.type(), finding.normalizedSql(), finding.repositoryMethod(), finding.callerMethod(), finding.httpMethod(), finding.httpEndpoint(),
                finding.testName(), finding.phase(), finding.callSites());
        synchronized (repeatedQueries) {
            RepeatedQueryFinding existing = repeatedQueries.get(key);
            if (existing != null) {
                if (avoidableExecutions(finding) > avoidableExecutions(existing)) {
                    repeatedQueries.put(key, finding);
                }
            }
            else if (repeatedQueries.size() < properties.getMaxRecordedQueries()) {
                repeatedQueries.put(key, finding);
            }
            else {
                truncated = true;
                log.warn("[RepeatedQuery] Circuit-breaker: max {} entries reached, ignoring further repeated-query findings", properties.getMaxRecordedQueries());
            }
        }
    }

    /** Executions the request could have saved: a duplicate needs one per distinct parameter set, anything else one in total. */
    private static int avoidableExecutions(RepeatedQueryFinding finding) {
        if (finding.type() == RepeatedQueryFinding.Type.DUPLICATE) {
            return Math.max(finding.executions() - Math.max(finding.distinctParameterSets(), 1), 0);
        }
        return Math.max(finding.executions() - 1, 0);
    }

    /** What makes two repeated-query findings the same finding: the same repetition, of the same query, by the same code, in the same test. */
    private record FindingKey(RepeatedQueryFinding.Type type, String sql, String repositoryMethod, String callerMethod, String httpMethod, String httpEndpoint, String testName,
            String phase, List<QueryCallSite> callSites) {
    }

    /** One SQL template as issued by one piece of code. */
    private record TemplateKey(String sql, QueryCallSite callSite) {
    }

    /** Mutable per-request accumulator for one {@link TemplateKey}; only ever touched by the request's own thread. */
    private static final class TemplateStats {

        private int executions;

        /** parameter signature -> how many times exactly that statement ran. */
        private final Map<Long, Integer> parameterSignatures = new HashMap<>();
    }
}
