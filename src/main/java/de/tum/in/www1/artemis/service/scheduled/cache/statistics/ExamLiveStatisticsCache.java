package de.tum.in.www1.artemis.service.scheduled.cache.statistics;

import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

import com.hazelcast.config.Config;

import de.tum.in.www1.artemis.domain.exam.statistics.ExamActivity;
import de.tum.in.www1.artemis.service.scheduled.cache.Cache;

/**
 * Represents the cache for one specific exam.
 */
abstract class ExamLiveStatisticsCache implements Cache {

    /**
     * Exam id which works as an identifier for the cache.
     */
    private final Long examId;

    ExamLiveStatisticsCache(Long examId) {
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
        if (!(obj instanceof ExamLiveStatisticsCache)) {
            return false;

        }
        return Objects.equals(examId, ((ExamLiveStatisticsCache) obj).examId);
    }

    @Override
    public String toString() {
        return "ExamLiveStatisticsCache[" + examId + "]";
    }

    /**
     * Returns an empty exam live statistics ache
     *
     * @return the {@link EmptyExamLiveStatisticsCache} instance
     */
    static EmptyExamLiveStatisticsCache empty() {
        return EmptyExamLiveStatisticsCache.INSTANCE;
    }

    static void registerSerializers(Config config) {
        ExamLiveStatisticsDistributedCache.registerSerializer(config);
    }
}
