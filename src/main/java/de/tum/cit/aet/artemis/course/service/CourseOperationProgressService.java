package de.tum.cit.aet.artemis.course.service;

import static de.tum.cit.aet.artemis.core.config.Constants.COURSE_OPERATION_PROGRESS_STATUS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

    private final Map<CourseOperationClaim, ScheduledFuture<?>> claimRenewals = new ConcurrentHashMap<>();

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
     * @param startedAt     when the operation started
     * @return the unique claim that must be used for subsequent progress updates and cleanup
     */
    public CourseOperationClaim startOperation(long courseId, CourseOperationType operationType, String firstStep, int totalSteps, ZonedDateTime startedAt) {
        CourseOperationClaim operationClaim = new CourseOperationClaim(courseId, operationType, startedAt, UUID.randomUUID());
        operationClaims.lock(courseId);
        boolean claimInserted = false;
        ScheduledFuture<?> renewal = null;
        try {
            String existingClaim = operationClaims.putIfAbsent(courseId, operationClaim.ownerToken().toString());
            if (existingClaim != null) {
                throw new ConflictException("Another operation is already in progress for this course", Course.ENTITY_NAME, "courseOperationInProgress");
            }
            claimInserted = true;
            renewal = taskScheduler.scheduleAtFixedRate(() -> renewOperationClaim(operationClaim), Instant.now().plus(COURSE_OPERATION_CLAIM_RENEWAL_INTERVAL),
                    COURSE_OPERATION_CLAIM_RENEWAL_INTERVAL);
            if (renewal == null) {
                throw new IllegalStateException("Could not schedule renewal of the course operation claim");
            }
            claimRenewals.put(operationClaim, renewal);
            var status = CourseOperationProgressDTO.inProgress(operationType, firstStep, 0, totalSteps, 0, 0, 0, startedAt, 0.0);
            sendAndCacheProgress(courseId, status);
        }
        catch (RuntimeException | Error failure) {
            if (claimInserted) {
                rollbackFailedStart(operationClaim, renewal, failure);
            }
            try {
                operationClaims.unlock(courseId);
            }
            catch (RuntimeException | Error unlockFailure) {
                failure.addSuppressed(unlockFailure);
            }
            throw failure;
        }
        try {
            operationClaims.unlock(courseId);
        }
        catch (RuntimeException | Error failure) {
            rollbackFailedStart(operationClaim, renewal, failure);
            throw failure;
        }
        return operationClaim;
    }

    /**
     * Updates the progress of a course operation and broadcasts the update.
     * Use this overload for steps that process multiple items with trackable progress.
     *
     * @param operationClaim          the claim that owns the operation
     * @param currentStep             the name of the current step
     * @param stepsCompleted          the number of completed steps
     * @param totalSteps              the total number of steps
     * @param itemsProcessed          the number of items processed in the current step
     * @param totalItems              the total items to process in the current step
     * @param failed                  the number of failed items
     * @param weightedProgressPercent the weighted progress percentage (0-100)
     */
    public void updateProgress(CourseOperationClaim operationClaim, String currentStep, int stepsCompleted, int totalSteps, int itemsProcessed, int totalItems, int failed,
            double weightedProgressPercent) {
        var status = CourseOperationProgressDTO.inProgress(operationClaim.operationType(), currentStep, stepsCompleted, totalSteps, itemsProcessed, totalItems, failed,
                operationClaim.startedAt(), weightedProgressPercent);
        sendProgressIfOwned(operationClaim, status);
    }

    /**
     * Updates the progress of a course operation and broadcasts the update.
     * Use this overload for simple steps that don't have item-level progress tracking (e.g., bulk deletes).
     *
     * @param operationClaim          the claim that owns the operation
     * @param currentStep             the name of the current step
     * @param stepsCompleted          the number of completed steps
     * @param totalSteps              the total number of steps
     * @param weightedProgressPercent the weighted progress percentage (0-100)
     */
    public void updateProgress(CourseOperationClaim operationClaim, String currentStep, int stepsCompleted, int totalSteps, double weightedProgressPercent) {
        var status = CourseOperationProgressDTO.inProgress(operationClaim.operationType(), currentStep, stepsCompleted, totalSteps, 0, 0, 0, operationClaim.startedAt(),
                weightedProgressPercent);
        sendProgressIfOwned(operationClaim, status);
    }

    /**
     * Marks the operation as completed and broadcasts the final status.
     *
     * @param operationClaim the claim that owns the operation
     * @param totalSteps     the total number of steps completed
     * @param failed         the number of failed operations
     */
    public void completeOperation(CourseOperationClaim operationClaim, int totalSteps, int failed) {
        var status = CourseOperationProgressDTO.completed(operationClaim.operationType(), totalSteps, failed, operationClaim.startedAt());
        finishOperation(operationClaim, status);
    }

    /**
     * Marks the operation as failed and broadcasts the error status.
     *
     * @param operationClaim          the claim that owns the operation
     * @param currentStep             the step where the failure occurred
     * @param stepsCompleted          the number of completed steps
     * @param totalSteps              the total number of steps
     * @param failed                  the number of failed items
     * @param errorMessage            the error message describing the failure
     * @param weightedProgressPercent the weighted progress percentage at time of failure
     */
    public void failOperation(CourseOperationClaim operationClaim, String currentStep, int stepsCompleted, int totalSteps, int failed, String errorMessage,
            double weightedProgressPercent) {
        var status = CourseOperationProgressDTO.failed(operationClaim.operationType(), currentStep, stepsCompleted, totalSteps, failed, operationClaim.startedAt(), errorMessage,
                weightedProgressPercent);
        finishOperation(operationClaim, status);
    }

    /**
     * Releases an operation claim without publishing a final progress status. This is an idempotent safety net for operation bodies that exit before they can report completion or
     * failure. A stale operation cannot release a newer operation's claim.
     *
     * @param operationClaim the claim to release
     */
    public void releaseOperationClaim(CourseOperationClaim operationClaim) {
        boolean lockAcquired = false;
        try {
            operationClaims.lock(operationClaim.courseId());
            lockAcquired = true;
            operationClaims.remove(operationClaim.courseId(), operationClaim.ownerToken().toString());
        }
        catch (RuntimeException e) {
            log.warn("Failed to release the operation claim for course {}; it will expire automatically", operationClaim.courseId(), e);
        }
        finally {
            try {
                if (lockAcquired) {
                    operationClaims.unlock(operationClaim.courseId());
                }
            }
            catch (RuntimeException e) {
                log.warn("Failed to unlock the operation claim for course {}", operationClaim.courseId(), e);
            }
            finally {
                stopClaimRenewal(operationClaim);
            }
        }
    }

    /**
     * Verifies that the operation still owns its distributed claim and refreshes the claim lifetime. Destructive operation steps call this before mutating course data so a stale
     * worker stops after losing its claim.
     *
     * @param operationClaim the claim whose ownership should be verified
     * @throws IllegalStateException if another operation owns the course or the claim expired
     */
    public void verifyOperationClaim(CourseOperationClaim operationClaim) {
        operationClaims.lock(operationClaim.courseId());
        try {
            if (!ownsClaim(operationClaim)) {
                throw new IllegalStateException("Course operation claim ownership was lost for course " + operationClaim.courseId());
            }
            operationClaims.put(operationClaim.courseId(), operationClaim.ownerToken().toString());
        }
        finally {
            operationClaims.unlock(operationClaim.courseId());
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

    private void finishOperation(CourseOperationClaim operationClaim, CourseOperationProgressDTO status) {
        boolean lockAcquired = false;
        try {
            operationClaims.lock(operationClaim.courseId());
            lockAcquired = true;
            if (ownsClaim(operationClaim)) {
                sendAndCacheProgress(operationClaim.courseId(), status);
                operationClaims.remove(operationClaim.courseId(), operationClaim.ownerToken().toString());
            }
        }
        finally {
            try {
                if (lockAcquired) {
                    operationClaims.unlock(operationClaim.courseId());
                }
            }
            finally {
                stopClaimRenewal(operationClaim);
            }
        }
    }

    private void sendProgressIfOwned(CourseOperationClaim operationClaim, CourseOperationProgressDTO status) {
        verifyOperationClaim(operationClaim);
        sendAndCacheProgress(operationClaim.courseId(), status);
    }

    private void renewOperationClaim(CourseOperationClaim operationClaim) {
        boolean ownershipLost = false;
        try {
            operationClaims.lock(operationClaim.courseId());
            try {
                if (ownsClaim(operationClaim)) {
                    operationClaims.put(operationClaim.courseId(), operationClaim.ownerToken().toString());
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

    private void stopClaimRenewal(CourseOperationClaim operationClaim) {
        ScheduledFuture<?> renewal = claimRenewals.remove(operationClaim);
        if (renewal != null) {
            renewal.cancel(false);
        }
    }

    private boolean ownsClaim(CourseOperationClaim operationClaim) {
        return operationClaim.ownerToken().toString().equals(operationClaims.get(operationClaim.courseId()));
    }

    private void rollbackFailedStart(CourseOperationClaim operationClaim, ScheduledFuture<?> renewal, Throwable failure) {
        try {
            claimRenewals.remove(operationClaim);
            if (renewal != null) {
                renewal.cancel(false);
            }
        }
        catch (RuntimeException | Error cancellationFailure) {
            failure.addSuppressed(cancellationFailure);
        }
        try {
            operationClaims.remove(operationClaim.courseId(), operationClaim.ownerToken().toString());
        }
        catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
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
