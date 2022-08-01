package de.tum.in.www1.artemis.service.scheduled.cache.statistics;

import java.util.Map;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.in.www1.artemis.domain.exam.statistics.ExamActivity;

/**
 * Empty cache exam live statistics object representing a cache miss.
 * <p>
 * This immutable, singleton class returning only null or empty data structures on read operations allows being used as default,
 * if no real cache exists. This saves us much branching and checking in the {@link ExamLiveStatisticsScheduleService},
 * as we can either just return the {@link EmptyExamLiveStatisticsCache} or create a new
 * real {@link ExamLiveStatisticsCache} for write operations, if no cache can be found.
 * <p>
 * All method that modify a {@link ExamLiveStatisticsCache} throw an {@link UnsupportedOperationException} for the empty cache.
 */
public class EmptyExamLiveStatisticsCache extends ExamLiveStatisticsCache {

    private final Logger logger = LoggerFactory.getLogger(EmptyExamLiveStatisticsCache.class);

    static final EmptyExamLiveStatisticsCache INSTANCE = new EmptyExamLiveStatisticsCache();

    EmptyExamLiveStatisticsCache() {
        super(null);
    }

    @Override
    Map<Long, ExamActivity> getActivities() {
        return Map.of();
    }

    @Override
    void updateActivity(Long activityId, UnaryOperator<ExamActivity> writeOperation) {
        logger.error("EmptyExamLiveStatisticsCache cannot be updated");
        throwModificationAttemptException();
    }

    @Override
    public void clear() {
        logger.error("EmptyExamLiveStatisticsCache cannot be cleared");
        throwModificationAttemptException();
    }

    private void throwModificationAttemptException() {
        throw new UnsupportedOperationException("EmptyExamLiveStatisticsCache cannot be modified");
    }
}
