package de.tum.in.www1.artemis.service.scheduled.cache.monitoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.hazelcast.config.Config;
import com.hazelcast.scheduledexecutor.ScheduledTaskHandler;

import de.tum.in.www1.artemis.domain.exam.Exam;
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

    /**
     * The scheduled save tasks of the ExamActivity
     */
    abstract List<ScheduledTaskHandler> getExamActivitySaveHandler();

    /**
     * Set the scheduled save tasks of the ExamActivity
     */
    abstract void setExamActivitySaveHandler(List<ScheduledTaskHandler> examActivitySaveHandler);

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

    static List<ScheduledTaskHandler> getEmptyExamActivitySaveHandler() {
        return new ArrayList<>(0);
    }
}
