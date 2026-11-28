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
 * Thread-safe in-memory store for the query detector.
 * <p>
 * Collects the query templates that ran more than once within a single HTTP request, either with
 * different parameters (N+1) or with identical ones (duplicate); see {@link RepeatedQueryFinding}.
 * <p>
 * Only active when the {@code e2e-performance} Spring profile is enabled.
 */
@Component
@Profile(SPRING_PROFILE_E2E_PERFORMANCE)
@Lazy
public class QueryCollector {

    private static final Logger log = LoggerFactory.getLogger(QueryCollector.class);

    private static final Comparator<QueryCallSite> CALL_SITE_ORDER = Comparator.comparing(QueryCallSite::callerMethod, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(QueryCallSite::repositoryMethod, Comparator.nullsLast(Comparator.naturalOrder()));

    private final QueryProperties properties;

    /**
     * Per-request statistics, keyed by normalized SQL template and the code that issued it: one
     * template (e.g. loading a user by id) is run by many places in a request, and each of them is
     * its own finding. A {@link ThreadLocal} because one request is handled by one thread, so every
     * request gets its own counters without synchronization, and a request finishing cannot wipe the
     * counts of another one still running.
     */
    private final ThreadLocal<Map<TemplateKey, TemplateStats>> requestTemplates = ThreadLocal.withInitial(HashMap::new);

    /**
     * Repeated-query findings, see {@link #addRepeatedQueryFindings}: one per {@link FindingKey}, the request that
     * could have saved the most executions. Requests of the same test repeating the same finding therefore use no
     * further room, and the cap counts distinct findings. Guarded by its own monitor.
     */
    private final Map<FindingKey, RepeatedQueryFinding> repeatedQueries = new LinkedHashMap<>();

    /** Set once a repeated-query finding was dropped, which makes the set of findings of the run incomplete. */
    private volatile boolean truncated;

    public QueryCollector(QueryProperties properties) {
        this.properties = properties;
    }

    /**
     * Records a completed query execution of the current request, called by {@link QueryListener}.
     *
     * @param normalizedSql      SQL with literals stripped.
     * @param parameterSignature hash of the SQL and its bound values, see {@link QueryListener#parameterSignature}.
     * @param callSiteResolver   resolves where the query was issued from; invoked inline, as it needs the query's own thread.
     */
    public void record(String normalizedSql, long parameterSignature, Supplier<QueryCallSite> callSiteResolver) {
        TemplateKey key = new TemplateKey(normalizedSql, resolveCallSite(callSiteResolver));
        TemplateStats stats = requestTemplates.get().computeIfAbsent(key, k -> new TemplateStats());
        stats.executions++;
        stats.parameterSignatures.merge(parameterSignature, 1, Integer::sum);
    }

    /** Discards the calling thread's per-request statistics, also when {@link #finishRequest} was never reached, so nothing bleeds into the thread's next request. */
    public void resetRequestState() {
        requestTemplates.remove();
    }

    /**
     * Turns the calling thread's per-request statistics into the request's {@link RepeatedQueryFinding}s and clears them.
     * Called once per HTTP request, on its thread, after {@link #record} ran for every query of the request.
     *
     * @param httpMethod   HTTP verb of the request.
     * @param httpEndpoint route template of the request.
     * @param testName     Playwright test name; may be {@code null}.
     * @param phase        Playwright phase ({@code "action"} or {@code "setup"}); may be {@code null}.
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
     * Returns a snapshot of all findings.
     *
     * @param indexes the indexes of every table, passed through to the report (see {@link IndexCatalog}).
     * @return the report.
     */
    public QueryReportDTO getReport(Map<String, List<TableIndex>> indexes) {
        List<RepeatedQueryFinding> repeatedSnapshot;
        synchronized (repeatedQueries) {
            repeatedSnapshot = new ArrayList<>(repeatedQueries.values());
        }
        return new QueryReportDTO(Instant.now(), repeatedSnapshot, indexes, truncated);
    }

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
            log.debug("[QueryReport] Could not resolve the call site of a query", e);
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
            else if (!truncated) {
                // logged once: every further new finding of the run would repeat it
                truncated = true;
                log.warn("[QueryReport] Circuit-breaker: max {} entries reached, ignoring further repeated-query findings", properties.getMaxRecordedQueries());
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
