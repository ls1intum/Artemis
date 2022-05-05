package de.tum.in.www1.artemis.service.scheduled.cache.monitoring;

import java.util.Map;
import java.util.Objects;

import com.hazelcast.config.Config;

import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamActivity;
import de.tum.in.www1.artemis.service.scheduled.cache.Cache;

abstract class ExamMonitoringCache implements Cache {

    private final Long examId;

    ExamMonitoringCache(Long examId) {
        this.examId = examId;
    }

    public Long getExamId() {
        return examId;
    }

    /**
     * Returns the cached exam object.
     *
     * @return the actual Exam object, may be null.
     */
    abstract Exam getExam();

    /**
     * Set the cached {@link Exam} object
     */
    abstract void setExam(Exam exam);

    /**
     * ExamActivity by student exam id
     */
    abstract Map<Long, ExamActivity> getActivities();

    @Override
    public final int hashCode() {
        return Objects.hashCode(examId);
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof ExamMonitoringCache))
            return false;
        return Objects.equals(examId, ((ExamMonitoringCache) obj).examId);
    }

    @Override
    public String toString() {
        return "ExamMonitoringCache[" + examId + "]";
    }

    /**
     * Returns an empty exam monitoring exercise
     *
     * @return the {@link EmptyExamMonitoringCache} instance
     */
    static EmptyExamMonitoringCache empty() {
        return EmptyExamMonitoringCache.INSTANCE;
    }

    static void registerSerializers(Config config) {
        ExamMonitoringDistributedCache.registerSerializer(config);
    }
}
