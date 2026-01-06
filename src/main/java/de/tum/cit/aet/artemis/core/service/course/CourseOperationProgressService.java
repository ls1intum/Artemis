package de.tum.cit.aet.artemis.core.service.course;

import static de.tum.cit.aet.artemis.core.config.Constants.COURSE_OPERATION_PROGRESS_STATUS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.domain.CourseOperationType;
import de.tum.cit.aet.artemis.core.dto.CourseOperationProgressDTO;

/**
 * Service for managing and broadcasting course operation progress (delete, reset, archive).
 * Uses Hazelcast distributed cache for storing progress and WebSocket for real-time updates.
 * <p>
 * Progress is tracked using a weighted system where different operations have different
 * costs based on their complexity. The weighted progress provides more accurate ETA
 * calculations than simple step counting.
 */
@Service
@Profile(PROFILE_CORE)
@Lazy
public class CourseOperationProgressService {

    private static final Logger log = LoggerFactory.getLogger(CourseOperationProgressService.class);

    private static final String COURSE_OPERATION_PROGRESS_TOPIC = "/topic/courses/%d/operation-progress";

    private final CacheManager cacheManager;

    private final WebsocketMessagingService websocketMessagingService;

    private final ReentrantLock progressLock = new ReentrantLock();

    public CourseOperationProgressService(CacheManager cacheManager, WebsocketMessagingService websocketMessagingService) {
        this.cacheManager = cacheManager;
        this.websocketMessagingService = websocketMessagingService;
    }

    /**
     * Starts tracking a new course operation and broadcasts the initial status.
     *
     * @param courseId      the ID of the course being operated on
     * @param operationType the type of operation (DELETE, RESET, ARCHIVE)
     * @param firstStep     the name of the first step
     * @param totalSteps    the total number of steps in the operation
     */
    public void startOperation(long courseId, CourseOperationType operationType, String firstStep, int totalSteps) {
        var status = CourseOperationProgressDTO.inProgress(operationType, firstStep, 0, totalSteps, 0, 0, 0, ZonedDateTime.now(), 0.0);
        sendAndCacheProgress(courseId, status);
    }

    /**
     * Updates the progress of a course operation and broadcasts the update.
     * Use this overload for steps that process multiple items with trackable progress.
     *
     * @param courseId                the ID of the course being operated on
     * @param operationType           the type of operation
     * @param currentStep             the name of the current step
     * @param stepsCompleted          the number of completed steps
     * @param totalSteps              the total number of steps
     * @param itemsProcessed          the number of items processed in the current step
     * @param totalItems              the total items to process in the current step
     * @param failed                  the number of failed items
     * @param startedAt               when the operation started
     * @param weightedProgressPercent the weighted progress percentage (0-100)
     */
    public void updateProgress(long courseId, CourseOperationType operationType, String currentStep, int stepsCompleted, int totalSteps, int itemsProcessed, int totalItems,
            int failed, ZonedDateTime startedAt, double weightedProgressPercent) {
        var status = CourseOperationProgressDTO.inProgress(operationType, currentStep, stepsCompleted, totalSteps, itemsProcessed, totalItems, failed, startedAt,
                weightedProgressPercent);
        sendAndCacheProgress(courseId, status);
    }

    /**
     * Updates the progress of a course operation and broadcasts the update.
     * Use this overload for simple steps that don't have item-level progress tracking (e.g., bulk deletes).
     *
     * @param courseId                the ID of the course being operated on
     * @param operationType           the type of operation
     * @param currentStep             the name of the current step
     * @param stepsCompleted          the number of completed steps
     * @param totalSteps              the total number of steps
     * @param startedAt               when the operation started
     * @param weightedProgressPercent the weighted progress percentage (0-100)
     */
    public void updateProgress(long courseId, CourseOperationType operationType, String currentStep, int stepsCompleted, int totalSteps, ZonedDateTime startedAt,
            double weightedProgressPercent) {
        var status = CourseOperationProgressDTO.inProgress(operationType, currentStep, stepsCompleted, totalSteps, 0, 0, 0, startedAt, weightedProgressPercent);
        sendAndCacheProgress(courseId, status);
    }

    /**
     * Marks the operation as completed and broadcasts the final status.
     *
     * @param courseId      the ID of the course
     * @param operationType the type of operation
     * @param totalSteps    the total number of steps completed
     * @param failed        the number of failed operations
     * @param startedAt     when the operation started
     */
    public void completeOperation(long courseId, CourseOperationType operationType, int totalSteps, int failed, ZonedDateTime startedAt) {
        var status = CourseOperationProgressDTO.completed(operationType, totalSteps, failed, startedAt);
        sendAndCacheProgress(courseId, status);
    }

    /**
     * Marks the operation as failed and broadcasts the error status.
     *
     * @param courseId                the ID of the course
     * @param operationType           the type of operation
     * @param currentStep             the step where the failure occurred
     * @param stepsCompleted          the number of completed steps
     * @param totalSteps              the total number of steps
     * @param failed                  the number of failed items
     * @param startedAt               when the operation started
     * @param errorMessage            the error message describing the failure
     * @param weightedProgressPercent the weighted progress percentage at time of failure
     */
    public void failOperation(long courseId, CourseOperationType operationType, String currentStep, int stepsCompleted, int totalSteps, int failed, ZonedDateTime startedAt,
            String errorMessage, double weightedProgressPercent) {
        var status = CourseOperationProgressDTO.failed(operationType, currentStep, stepsCompleted, totalSteps, failed, startedAt, errorMessage, weightedProgressPercent);
        sendAndCacheProgress(courseId, status);
    }

    private void sendAndCacheProgress(long courseId, CourseOperationProgressDTO status) {
        try {
            progressLock.lock();
            var cache = cacheManager.getCache(COURSE_OPERATION_PROGRESS_STATUS);
            if (cache != null) {
                cache.put(courseId, status);
            }
            else {
                log.warn("Unable to cache course operation progress because cache is null");
            }
            websocketMessagingService.sendMessage(COURSE_OPERATION_PROGRESS_TOPIC.formatted(courseId), status);
        }
        catch (Exception e) {
            log.warn("Failed to send course operation progress", e);
        }
        finally {
            progressLock.unlock();
        }
    }
}
