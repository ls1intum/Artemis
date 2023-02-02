package de.tum.in.www1.artemis.service.scheduled.cache.monitoring;

import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

import com.hazelcast.config.Config;

import de.tum.in.www1.artemis.domain.exam.monitoring.ExamActivity;
import de.tum.in.www1.artemis.service.scheduled.cache.Cache;

/**
 * Represents the cache for one specific exam.
 */
abstract class ExamMonitoringCache implements Cache {

    /**
     * Exam id which works as an identifier for the cache.
     */
    private final Long examId;

    ExamMonitoringCache(Long examId) {
        this.examId = examId;
    }

    /**
     * Returns the exam id.
     * @return the exam id (key)
     */
    public Long getExamId() {
        return examId;
    }

    /**
     * ExamActivity by student exam id
     */
    abstract Map<Long, ExamActivity> getActivities();

    /**
     * Updates a specific activity without locking the entire exam cache.
     * @param activityId activity to update
     * @param writeOperation performs the operation on the exam activity
     */
    abstract void updateActivity(Long activityId, UnaryOperator<ExamActivity> writeOperation);

    @Override
    public final int hashCode() {
        return Objects.hashCode(examId);
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ExamMonitoringCache)) {
            return false;

        }
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
