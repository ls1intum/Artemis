package de.tum.in.www1.artemis.service.exam.monitoring;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    void clear() {
        log.error("EmptyExamMonitoringCache cannot be cleared");
        throwModificationAttemptException();
    }

    private static void throwModificationAttemptException() {
        throw new UnsupportedOperationException("EmptyExamMonitoringCache cannot be modified");
    }
}
