package de.tum.cit.aet.artemis.exam.service;

import static de.tum.cit.aet.artemis.core.config.Constants.EXAM_EXERCISE_START_STATUS;
import static de.tum.cit.aet.artemis.exam.web.ExamWebsocketTopics.EXERCISE_START_STATUS;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.util.ExamExerciseStartPreparationStatus;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;

/** Owns progress publication and retention, independently of student-exam assignment and participation preparation. */
@Service
@Lazy
@Conditional(ExamEnabled.class)
public class ExamExercisePreparationStatusService {

    private static final Logger log = LoggerFactory.getLogger(ExamExercisePreparationStatusService.class);

    private final CacheManager cacheManager;

    private final WebsocketMessagingService websocketMessagingService;

    public ExamExercisePreparationStatusService(CacheManager cacheManager, WebsocketMessagingService websocketMessagingService) {
        this.cacheManager = cacheManager;
        this.websocketMessagingService = websocketMessagingService;
    }

    /**
     * Publishes monotonic progress for one preparation batch under its caller-owned lock.
     *
     * @param examId         exam being prepared
     * @param finished       completed student exams
     * @param failed         failed student exams
     * @param overall        total student exams
     * @param participations prepared participations
     * @param startTime      batch start
     * @param lock           batch serialization lock
     */
    public void update(Long examId, int finished, int failed, int overall, int participations, ZonedDateTime startTime, ReentrantLock lock) {
        // Synchronizing and comparing to avoid race conditions here
        // Otherwise it can happen that a status with less completed exams is sent after one with a higher value
        try {
            lock.lock();
            ExamExerciseStartPreparationStatus status = null;
            var cache = cacheManager.getCache(EXAM_EXERCISE_START_STATUS);
            if (cache != null) {
                var oldValue = cache.get(examId);
                if (oldValue != null) {
                    var oldStatus = (ExamExerciseStartPreparationStatus) oldValue.get();
                    if (oldStatus != null) {
                        status = new ExamExerciseStartPreparationStatus(Math.max(finished, oldStatus.finished()), Math.max(failed, oldStatus.failed()),
                                Math.max(overall, oldStatus.overall()), Math.max(participations, oldStatus.participationCount()), startTime);
                    }
                }
                if (status == null) {
                    status = new ExamExerciseStartPreparationStatus(finished, failed, overall, participations, startTime);
                }
                cache.put(examId, status);
            }
            else {
                log.warn("Unable to add exam exercise start status to distributed cache because it is null");
            }
            websocketMessagingService.sendMessage(EXERCISE_START_STATUS.at(examId), status);
        }
        catch (Exception e) {
            log.warn("Failed to send exercise preparation status", e);
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * @param examId exam to inspect
     * @return last preparation progress, when retained
     */
    public Optional<ExamExerciseStartPreparationStatus> get(Long examId) {
        return Optional.ofNullable(cacheManager.getCache(EXAM_EXERCISE_START_STATUS)).map(cache -> cache.get(examId))
                .map(wrapper -> (ExamExerciseStartPreparationStatus) wrapper.get());
    }

    /**
     * @param examId exam whose next preparation starts a fresh progress record
     */
    public void invalidate(Long examId) {
        var cache = cacheManager.getCache(EXAM_EXERCISE_START_STATUS);
        if (cache != null) {
            cache.evict(examId);
        }
    }

}
