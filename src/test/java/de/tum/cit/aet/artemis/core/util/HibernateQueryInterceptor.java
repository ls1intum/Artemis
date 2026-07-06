package de.tum.cit.aet.artemis.core.util;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class HibernateQueryInterceptor implements StatementInspector {

    private final transient ThreadLocal<Long> threadQueryCount = new ThreadLocal<>();

    private final transient ThreadLocal<List<String>> threadQueries = new ThreadLocal<>();

    /**
     * Start or reset the query count to 0 for the considered thread
     */
    public void startQueryCount() {
        threadQueryCount.set(0L);
    }

    /**
     * Start capturing query count and SQL statements for the considered thread.
     */
    public void startQueryCapture() {
        threadQueryCount.set(0L);
        threadQueries.set(new ArrayList<>());
    }

    /**
     * Get the query count for the considered thread
     *
     * @return Long the amount of queries that have been perofrmed since the count was started
     */
    public Long getQueryCount() {
        return threadQueryCount.get();
    }

    /**
     * Stop capturing query count and SQL statements for the considered thread.
     *
     * @return captured query count and SQL statements
     */
    public CapturedQueries stopQueryCapture() {
        Long count = threadQueryCount.get();
        List<String> queries = threadQueries.get();
        threadQueryCount.remove();
        threadQueries.remove();
        return new CapturedQueries(count != null ? count : 0L, queries != null ? List.copyOf(queries) : List.of());
    }

    /**
     * Increment the query count for the considered thread for each new statement if the count has been initialized.
     *
     * @param sql Query to be executed
     * @return Query to be executed
     */
    @Override
    public String inspect(String sql) {
        Long count = threadQueryCount.get();
        if (count != null) {
            threadQueryCount.set(count + 1);
        }
        List<String> queries = threadQueries.get();
        if (queries != null) {
            queries.add(sql);
        }
        return sql;
    }

    public record CapturedQueries(long count, List<String> queries) {
    }
}
