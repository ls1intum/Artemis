package de.tum.in.www1.artemis.service.scheduled.cache.monitoring;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.scheduledexecutor.ScheduledTaskHandler;

import de.tum.in.www1.artemis.domain.exam.Exam;
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
    Exam getExam() {
        return null;
    }

    @Override
    void setExam(Exam exam) {
        logger.error("EmptyExamMonitoringCache cannot have an exam");
        throwModificationAttemptException();
    }

    @Override
    Map<Long, ExamActivity> getActivities() {
        return Map.of();
    }

    @Override
    List<ScheduledTaskHandler> getExamActivitySaveHandler() {
        return List.of();
    }

    @Override
    void setExamActivitySaveHandler(List<ScheduledTaskHandler> examActivitySaveHandler) {
        logger.error("EmptyExamMonitoringCache cannot have tasks");
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
