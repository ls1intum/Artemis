package de.tum.cit.aet.artemis.core.config.performance;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_E2E_PERFORMANCE;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
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
     * A 64-bit digest of the raw SQL text and every bound parameter value: two executions get the same
     * signature when they ran the same statement with the same values. The raw SQL is included (not just
     * the parameters) because Hibernate inlines some values as literals, which normalisation strips from
     * the template but which still make two statements different. For a JDBC batch, all of its parameter
     * sets are digested together, so one batch counts as one execution -- batching is the fix for
     * one-statement-per-item, not an instance of it.
     * <p>
     * A cryptographic digest, because values such as ids lie close together, where a sum of hash codes
     * gives different parameters the same result. Cut to 64 bits to keep per-request memory flat.
     */
    static long parameterSignature(String rawSql, List<List<ParameterSetOperation>> parametersList) {
        MessageDigest digest = sha256();
        add(digest, rawSql);
        if (parametersList != null) {
            for (List<ParameterSetOperation> parameterSet : parametersList) {
                // in parameter-index order, so the result depends on which value is bound to which
                // parameter but not on the order of the calls
                List<ParameterSetOperation> ordered = new ArrayList<>(parameterSet);
                ordered.sort(Comparator.comparing(QueryListener::parameterIndex));
                add(digest, "set of " + ordered.size());
                for (ParameterSetOperation operation : ordered) {
                    // the setter tells setNull(index, sqlType) apart from setInt(index, value)
                    add(digest, operation.getMethod() == null ? null : operation.getMethod().getName());
                    Object[] arguments = operation.getArgs() == null ? new Object[0] : operation.getArgs();
                    for (Object argument : arguments) {
                        add(digest, argument == null ? null : argument.getClass().getName());
                        add(digest, describe(argument));
                    }
                }
            }
        }
        return ByteBuffer.wrap(digest.digest()).getLong();
    }

    /** Adds one piece, preceded by its length, so that different sequences of pieces never give the same bytes. */
    private static void add(MessageDigest digest, String piece) {
        byte[] bytes = String.valueOf(piece).getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String describe(Object argument) {
        // a statement takes only the time zone from a calendar, whose text also holds the moment it was created
        if (argument instanceof Calendar calendar) {
            return calendar.getTimeZone().getID();
        }
        // renders an array, such as a byte[] value, by its content
        return Arrays.deepToString(new Object[] { argument });
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform provides SHA-256", e);
        }
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
