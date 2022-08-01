package de.tum.in.www1.artemis.service.scheduled.cache.monitoring;

import java.util.Map;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.in.www1.artemis.domain.exam.monitoring.ExamActivity;

/**
 * Empty cache exam monitoring object representing a cache miss.
 * <p>
 * This immutable, singleton class returning only null or empty data structures on read operations allows being used as default,
 * if no real cache exists. This saves us much branching and checking in the {@link ExamMonitoringScheduleService},
 * as we can either just return the {@link EmptyExamMonitoringCache} or create a new
 * real {@link ExamMonitoringCache} for write operations, if no cache can be found.
 * <p>
 * All method that modify a {@link ExamMonitoringCache} throw an {@link UnsupportedOperationException} for the empty cache.
 */
public class EmptyExamMonitoringCache extends ExamMonitoringCache {

    private final Logger logger = LoggerFactory.getLogger(EmptyExamMonitoringCache.class);

    static final EmptyExamMonitoringCache INSTANCE = new EmptyExamMonitoringCache();

    EmptyExamMonitoringCache() {
        super(null);
    }

    @Override
    Map<Long, ExamActivity> getActivities() {
        return Map.of();
    }

    @Override
    void updateActivity(Long activityId, UnaryOperator<ExamActivity> writeOperation) {
        logger.error("EmptyExamMonitoringCache cannot be updated");
        throwModificationAttemptException();
    }

    @Override
    public void clear() {
        logger.error("EmptyExamMonitoringCache cannot be cleared");
        throwModificationAttemptException();
    }

    private void throwModificationAttemptException() {
        throw new UnsupportedOperationException("EmptyExamMonitoringCache cannot be modified");
    }
}
