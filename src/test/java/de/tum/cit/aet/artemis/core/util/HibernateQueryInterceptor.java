package de.tum.cit.aet.artemis.core.util;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Sees every SQL statement Hibernate executes, so that a test can assert how many queries a call makes (see
 * {@link QueryCountAssert}) and which tables they read (see {@link #getRecordedStatements()}). It is registered as the
 * statement inspector of every integration test context by {@code HibernatePropertiesConfig}. The count and the
 * recording are kept per thread, so tests running concurrently in the same context do not see each other's queries,
 * and a thread counts or records nothing until it has been told to.
 */
@Component
@Lazy
public class HibernateQueryInterceptor implements StatementInspector {

    private final transient ThreadLocal<Long> threadQueryCount = new ThreadLocal<>();

    private final transient ThreadLocal<List<String>> threadStatements = new ThreadLocal<>();

    /**
     * The statements of the thread between {@link #startRecordingStatements()} and {@link #stopRecordingStatements()}. Kept apart from the count, which runs on
     * until the next start: a count costs one long per thread, a recording grows with every statement, so it has an end.
     */
    private final transient ThreadLocal<List<String>> threadRecordedStatements = new ThreadLocal<>();

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
     * @return Long the amount of queries that have been performed since the count was started
     */
    public Long getQueryCount() {
        return threadQueryCount.get();
    }

    /**
     * Start recording the SQL statements executed on the considered thread, discarding whatever was recorded on it before.
     */
    public void startRecordingStatements() {
        threadRecordedStatements.set(new ArrayList<>());
    }

    /**
     * Stop recording on the considered thread and discard what was recorded, so that a thread which has read its statements does not go on collecting them.
     */
    public void stopRecordingStatements() {
        threadRecordedStatements.remove();
    }

    /**
     * Get the SQL statements executed on the considered thread since recording started, in execution order.
     *
     * @return an unmodifiable copy of the statements, empty when the thread is not recording
     */
    public List<String> getRecordedStatements() {
        List<String> statements = threadRecordedStatements.get();
        return statements == null ? List.of() : List.copyOf(statements);
    }

    /**
     * Increment the query count for the considered thread for each new statement if the count has been initialized, and record the statement if the thread is
     * recording.
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
        List<String> statements = threadRecordedStatements.get();
        if (statements != null) {
            statements.add(sql);
        }
        return sql;
    }
}
