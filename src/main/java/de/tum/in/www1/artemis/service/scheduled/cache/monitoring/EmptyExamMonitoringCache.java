package de.tum.in.www1.artemis.service.scheduled.cache.monitoring;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.scheduledexecutor.ScheduledTaskHandler;

import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamActivity;

public class EmptyExamMonitoringCache extends ExamMonitoringCache {

    private static final Logger log = LoggerFactory.getLogger(EmptyExamMonitoringCache.class);

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
        log.error("EmptyExamMonitoringCache cannot have an exam");
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
        log.error("EmptyExamMonitoringCache cannot have tasks");
        throwModificationAttemptException();
    }

    @Override
    public void clear() {
        log.error("EmptyExamMonitoringCache cannot be cleared");
        throwModificationAttemptException();
    }

    private void throwModificationAttemptException() {
        throw new UnsupportedOperationException("EmptyExamMonitoringCache cannot be modified");
    }
}
