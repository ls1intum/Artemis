package de.tum.in.www1.artemis.util;

import org.hibernate.EmptyInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.service.exam.ExamDeletionService;

@Component
public class HibernateQueryInterceptor extends EmptyInterceptor {

    private final Logger log = LoggerFactory.getLogger(ExamDeletionService.class);

    private final transient ThreadLocal<Long> threadQueryCount = new ThreadLocal<>();

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
    public String onPrepareStatement(String sql) {
        Long count = threadQueryCount.get();
        if (count != null) {
            log.info("DEBUG QUERY: " + sql);
            threadQueryCount.set(count + 1);
        }
        return super.onPrepareStatement(sql);
    }
}
