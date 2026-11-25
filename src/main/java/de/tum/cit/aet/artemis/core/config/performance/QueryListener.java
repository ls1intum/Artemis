package de.tum.cit.aet.artemis.core.config.performance;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_E2E_PERFORMANCE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.proxy.ParameterSetOperation;

/**
 * JDBC-level query execution listener, plugged into the datasource-proxy wrapper. It hands every
 * statement that runs within an HTTP request to the {@link QueryCollector}, as normalised SQL
 * plus a signature of its bound values, which tells an N+1 (same template, different values) apart
 * from a duplicate (same template, same values).
 * <p>
 * Only active when the {@code e2e-performance} Spring profile is enabled.
 */
@Component
@Profile(SPRING_PROFILE_E2E_PERFORMANCE)
@Lazy
public class QueryListener implements QueryExecutionListener {

    private static final Pattern LITERAL_STRINGS = Pattern.compile("'[^']*'");

    private static final Pattern LITERAL_NUMBERS = Pattern.compile("\\b\\d+(\\.\\d+)?\\b");

    // Only a list of values or placeholders collapses: an IN (select ...) subquery keeps its
    // structure, so two different subqueries stay two different templates. [^()] never crosses a
    // nested parenthesis, so no partial match can leave a stray ')' behind. \b before IN is required
    // -- without it this also matches the "IN" inside "JOIN".
    private static final Pattern IN_LIST_VALUES = Pattern.compile("\\bIN\\s*\\((?!\\s*select\\b)[^()]+\\)", Pattern.CASE_INSENSITIVE);

    private static final Pattern WHITESPACE_RUNS = Pattern.compile("\\s+");

    private final QueryCollector collector;

    public QueryListener(QueryCollector collector) {
        this.collector = collector;
    }

    @Override
    public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        // Nothing to do before execution
    }

    @Override
    public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        // queries outside a request (startup, scheduled tasks) have no request to repeat a query in
        if (queryInfoList.isEmpty() || RequestContextHolder.getRequestAttributes() == null) {
            return;
        }
        QueryInfo queryInfo = queryInfoList.get(0);
        String rawSql = queryInfo.getQuery();
        collector.record(normalizeSql(rawSql), parameterSignature(rawSql, queryInfo.getParametersList()), QueryCallSite::resolve);
    }

    /**
     * A hash of the raw SQL text and every bound parameter value: two executions get the same
     * signature exactly when they ran the same statement with the same values. The raw SQL is
     * included (not just the parameters) because Hibernate inlines some values as literals, which
     * normalisation strips from the template but which still make two statements different. For
     * a JDBC batch, all of its parameter sets are hashed together, so one batch counts as one
     * execution -- batching is the fix for one-statement-per-item, not an instance of it.
     * A 64-bit hash keeps per-request memory flat; a collision is negligible at that size.
     */
    static long parameterSignature(String rawSql, List<List<ParameterSetOperation>> parametersList) {
        long hash = rawSql != null ? rawSql.hashCode() : 0;
        if (parametersList != null) {
            for (List<ParameterSetOperation> parameterSet : parametersList) {
                // hashed in parameter-index order, so the result depends on which value is bound to
                // which parameter but not on the order of the calls; deepHashCode also covers byte[]
                List<ParameterSetOperation> ordered = new ArrayList<>(parameterSet);
                ordered.sort(Comparator.comparing(QueryListener::parameterIndex));
                long setHash = 0;
                for (ParameterSetOperation operation : ordered) {
                    setHash = setHash * 31 + Arrays.deepHashCode(operation.getArgs());
                }
                hash = hash * 1_000_003L + setHash;
            }
        }
        return hash;
    }

    /** The JDBC parameter index (or name) a setXxx call binds, its first argument. */
    private static String parameterIndex(ParameterSetOperation operation) {
        Object[] args = operation.getArgs();
        return args == null || args.length == 0 ? "" : String.valueOf(args[0]);
    }

    /** A canonical, parameter-free form of a statement, so the same query shape counts together whatever ids or strings it used. */
    static String normalizeSql(String rawSql) {
        if (rawSql == null) {
            return null;
        }
        String sql = LITERAL_STRINGS.matcher(rawSql).replaceAll("?");
        sql = IN_LIST_VALUES.matcher(sql).replaceAll("IN (?)");
        sql = LITERAL_NUMBERS.matcher(sql).replaceAll("?");
        return WHITESPACE_RUNS.matcher(sql).replaceAll(" ").trim();
    }
}
