package de.tum.cit.aet.artemis.core.util;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.stereotype.Component;

@Component
public class HibernateQueryInterceptor implements StatementInspector {

    private final transient ThreadLocal<Long> threadQueryCount = new ThreadLocal<>();

    public final transient List<String> calls = new ArrayList<>();

    /**
     * Start or reset the query count to 0 for the considered thread
     */
    public void startQueryCount() {
        threadQueryCount.set(0L);
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
            if (sql.contains("learner_profile")) {
                System.out.println("Penguin");
            }
            threadQueryCount.set(count + 1);
            calls.add(sql);
        }
        return sql;
    }
}
