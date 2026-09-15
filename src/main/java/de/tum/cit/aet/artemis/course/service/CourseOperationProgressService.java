package de.tum.cit.aet.artemis.course.service;

import static de.tum.cit.aet.artemis.core.config.Constants.COURSE_OPERATION_PROGRESS_STATUS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseOperationType;
import de.tum.cit.aet.artemis.course.dto.CourseOperationProgressDTO;

/**
 * Service for managing and broadcasting course operation progress (delete, reset, archive).
 * Uses a distributed operation claim to prevent conflicting work and WebSocket for real-time progress updates.
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

    private static final String COURSE_OPERATION_CLAIMS = "course-operation-claims";

    private static final Duration COURSE_OPERATION_CLAIM_TIME_TO_LIVE = Duration.ofHours(1);

    private static final Duration COURSE_OPERATION_CLAIM_RENEWAL_INTERVAL = Duration.ofMinutes(5);

    private final CacheManager cacheManager;

    private final WebsocketMessagingService websocketMessagingService;

    private final DistributedMap<Long, String> operationClaims;

    private final TaskScheduler taskScheduler;

    private final Map<OperationClaim, ScheduledFuture<?>> claimRenewals = new ConcurrentHashMap<>();

    private final ReentrantLock progressLock = new ReentrantLock();

    public CourseOperationProgressService(CacheManager cacheManager, WebsocketMessagingService websocketMessagingService, DistributedDataProvider distributedDataProvider,
            @Qualifier("taskScheduler") TaskScheduler taskScheduler) {
        this.cacheManager = cacheManager;
        this.websocketMessagingService = websocketMessagingService;
        this.operationClaims = distributedDataProvider.getExpiringMap(COURSE_OPERATION_CLAIMS, COURSE_OPERATION_CLAIM_TIME_TO_LIVE);
        this.taskScheduler = taskScheduler;
    }

    /**
     * Starts tracking a new course operation and broadcasts the initial status.
     *
     * @param courseId      the ID of the course being operated on
     * @param operationType the type of operation (DELETE, RESET, ARCHIVE)
     * @param firstStep     the name of the first step
     * @param totalSteps    the total number of steps in the operation
     * @param startedAt     when the operation started; also identifies the owner of the distributed claim
     */
    public void startOperation(long courseId, CourseOperationType operationType, String firstStep, int totalSteps, ZonedDateTime startedAt) {
        OperationClaim operationClaim = operationClaim(courseId, operationType, startedAt);
        operationClaims.lock(courseId);
        try {
            String existingClaim = operationClaims.putIfAbsent(courseId, operationClaim.value());
            if (existingClaim != null) {
                throw new ConflictException("Another operation is already in progress for this course", Course.ENTITY_NAME, "courseOperationInProgress");
            }
            try {
                ScheduledFuture<?> renewal = taskScheduler.scheduleAtFixedRate(() -> renewOperationClaim(operationClaim),
                        Instant.now().plus(COURSE_OPERATION_CLAIM_RENEWAL_INTERVAL), COURSE_OPERATION_CLAIM_RENEWAL_INTERVAL);
                if (renewal == null) {
                    throw new IllegalStateException("Could not schedule renewal of the course operation claim");
                }
                claimRenewals.put(operationClaim, renewal);
            }
            catch (RuntimeException e) {
                operationClaims.remove(courseId, operationClaim.value());
                throw e;
            }
            var status = CourseOperationProgressDTO.inProgress(operationType, firstStep, 0, totalSteps, 0, 0, 0, startedAt, 0.0);
            sendAndCacheProgress(courseId, status);
        }
        finally {
            operationClaims.unlock(courseId);
        }
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
        sendProgressIfOwned(courseId, operationType, startedAt, status);
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
        sendProgressIfOwned(courseId, operationType, startedAt, status);
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
        finishOperation(courseId, operationType, startedAt, status);
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
        finishOperation(courseId, operationType, startedAt, status);
    }

    /**
     * Releases an operation claim without publishing a final progress status. This is an idempotent safety net for operation bodies that exit before they can report completion or
     * failure. A stale operation cannot release a newer operation's claim.
     *
     * @param courseId      the ID of the course
     * @param operationType the type of operation
     * @param startedAt     when the operation started
     */
    public void releaseOperationClaim(long courseId, CourseOperationType operationType, ZonedDateTime startedAt) {
        OperationClaim operationClaim = operationClaim(courseId, operationType, startedAt);
        boolean lockAcquired = false;
        try {
            operationClaims.lock(courseId);
            lockAcquired = true;
            operationClaims.remove(courseId, operationClaim.value());
        }
        catch (RuntimeException e) {
            log.warn("Failed to release the operation claim for course {}; it will expire automatically", courseId, e);
        }
        finally {
            if (lockAcquired) {
                try {
                    operationClaims.unlock(courseId);
                }
                catch (RuntimeException e) {
                    log.warn("Failed to unlock the operation claim for course {}", courseId, e);
                }
            }
            stopClaimRenewal(operationClaim);
        }
    }

    /**
     * Gets the current progress status for a course operation.
     *
     * @param courseId the ID of the course
     * @return the current progress status, or empty if no operation is in progress
     */
    public Optional<CourseOperationProgressDTO> getOperationProgress(long courseId) {
        return Optional.ofNullable(cacheManager.getCache(COURSE_OPERATION_PROGRESS_STATUS)).map(cache -> cache.get(courseId))
                .map(wrapper -> (CourseOperationProgressDTO) wrapper.get());
    }

    private void finishOperation(long courseId, CourseOperationType operationType, ZonedDateTime startedAt, CourseOperationProgressDTO status) {
        OperationClaim operationClaim = operationClaim(courseId, operationType, startedAt);
        boolean lockAcquired = false;
        try {
            operationClaims.lock(courseId);
            lockAcquired = true;
            if (operationClaim.value().equals(operationClaims.get(courseId))) {
                sendAndCacheProgress(courseId, status);
                operationClaims.remove(courseId, operationClaim.value());
            }
        }
        finally {
            try {
                if (lockAcquired) {
                    operationClaims.unlock(courseId);
                }
            }
            finally {
                stopClaimRenewal(operationClaim);
            }
        }
    }

    private void sendProgressIfOwned(long courseId, CourseOperationType operationType, ZonedDateTime startedAt, CourseOperationProgressDTO status) {
        OperationClaim operationClaim = operationClaim(courseId, operationType, startedAt);
        operationClaims.lock(courseId);
        try {
            if (operationClaim.value().equals(operationClaims.get(courseId))) {
                sendAndCacheProgress(courseId, status);
            }
        }
        finally {
            operationClaims.unlock(courseId);
        }
    }

    private void renewOperationClaim(OperationClaim operationClaim) {
        boolean ownershipLost = false;
        try {
            operationClaims.lock(operationClaim.courseId());
            try {
                if (operationClaim.value().equals(operationClaims.get(operationClaim.courseId()))) {
                    operationClaims.put(operationClaim.courseId(), operationClaim.value());
                }
                else {
                    ownershipLost = true;
                }
            }
            finally {
                operationClaims.unlock(operationClaim.courseId());
            }
        }
        catch (RuntimeException e) {
            log.warn("Failed to renew the operation claim for course {}", operationClaim.courseId(), e);
        }
        if (ownershipLost) {
            stopClaimRenewal(operationClaim);
        }
    }

    private void stopClaimRenewal(OperationClaim operationClaim) {
        ScheduledFuture<?> renewal = claimRenewals.remove(operationClaim);
        if (renewal != null) {
            renewal.cancel(false);
        }
    }

    private OperationClaim operationClaim(long courseId, CourseOperationType operationType, ZonedDateTime startedAt) {
        return new OperationClaim(courseId, operationType + ":" + startedAt.toInstant());
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

    private record OperationClaim(long courseId, String value) {
    }
}
