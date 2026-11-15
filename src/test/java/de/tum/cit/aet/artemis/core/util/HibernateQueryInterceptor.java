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

    private final transient ThreadLocal<List<String>> threadStatements = new ThreadLocal<>();

    /**
     * Start or reset the query count to 0 for the considered thread
     */
    public void startQueryCount() {
        threadQueryCount.set(0L);
        threadStatements.set(new ArrayList<>());
    }

    /**
     * Get the statements that have been executed since the count was started, so that a test can assert which tables a
     * call read and not only how often it went to the database.
     *
     * @return the statements of the considered thread, in the order they were executed
     */
    public List<String> getStatements() {
        List<String> statements = threadStatements.get();
        return statements == null ? List.of() : List.copyOf(statements);
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
            threadStatements.get().add(sql);
        }
        return sql;
    }
}
