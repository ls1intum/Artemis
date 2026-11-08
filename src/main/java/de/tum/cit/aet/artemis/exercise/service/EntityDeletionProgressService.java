package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketDestination;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.exercise.domain.EntityDeletionStatus;
import de.tum.cit.aet.artemis.exercise.domain.EntityDeletionType;
import de.tum.cit.aet.artemis.exercise.dto.EntityDeletionProgressDTO;

/**
 * Runs the deletion of an exercise, an exam or an exercise group in the background and reports its progress.
 * <p>
 * At most one deletion holds a scope at a time (see {@link #exerciseScopeKey(long)} and {@link #examScopeKey(long)}), tracked by a distributed claim that a second
 * request cannot take. A deletion may hold further scopes, so an exam that is deleted also holds the scope of every one of its exercises and none of them can be deleted on
 * its own meanwhile. The claim expires a few minutes after the last renewal, so a deletion whose node stopped does not block the scope for long. The latest status of every
 * deletion stays available for an hour, also after it ended, so a client that opens the page later or reloads it can still read the progress or the outcome. Live updates go
 * to the websocket destination of the deletion.
 */
@Service
@Profile(PROFILE_CORE)
@Lazy
public class EntityDeletionProgressService {

    private static final Logger log = LoggerFactory.getLogger(EntityDeletionProgressService.class);

    private static final String ENTITY_DELETION_CLAIMS = "entity-deletion-claims";

    private static final String ENTITY_DELETION_STATUSES = "entity-deletion-statuses";

    private static final String ENTITY_DELETION_COURSES = "entity-deletion-courses";

    private static final Duration STATUS_TIME_TO_LIVE = Duration.ofHours(1);

    // The claim is renewed by the running deletion. A deletion that stopped without releasing its claim (for example because its node was shut down) frees the scope after this
    // time.
    private static final Duration CLAIM_TIME_TO_LIVE = Duration.ofMinutes(10);

    private static final Duration CLAIM_RENEWAL_INTERVAL = Duration.ofMinutes(2);

    private static final int MAX_ERROR_MESSAGE_LENGTH = 200;

    static final String GENERIC_ERROR_MESSAGE = "The deletion failed. See the server log for details.";

    static final String INTERRUPTED_ERROR_MESSAGE = "The deletion was interrupted, for example because the server restarted. Delete the entity again to finish it.";

    private final WebsocketMessagingService websocketMessagingService;

    private final DistributedMap<String, String> claims;

    private final DistributedMap<String, EntityDeletionProgressDTO> statuses;

    private final DistributedMap<String, Long> courseIds;

    private final TaskScheduler taskScheduler;

    private final Executor deletionExecutor;

    private final Map<EntityDeletionClaim, ScheduledFuture<?>> claimRenewals = new ConcurrentHashMap<>();

    // Scopes that a running deletion claimed after it started, for example the exercise of an exercise group that was added after the request, by the token of the owner.
    private final Map<UUID, Set<String>> lateScopeKeys = new ConcurrentHashMap<>();

    /**
     * Orders the writes of the stored status on this node: the progress writes of the worker and the heartbeat refresh of the renewal task.
     */
    private final ReentrantLock statusWriteLock = new ReentrantLock();

    public EntityDeletionProgressService(WebsocketMessagingService websocketMessagingService, DistributedDataProvider distributedDataProvider,
            @Qualifier("taskScheduler") TaskScheduler taskScheduler, @Qualifier("entityDeletionExecutor") Executor deletionExecutor) {
        this.websocketMessagingService = websocketMessagingService;
        this.claims = distributedDataProvider.getExpiringMap(ENTITY_DELETION_CLAIMS, CLAIM_TIME_TO_LIVE);
        this.statuses = distributedDataProvider.getExpiringMap(ENTITY_DELETION_STATUSES, STATUS_TIME_TO_LIVE);
        this.courseIds = distributedDataProvider.getExpiringMap(ENTITY_DELETION_COURSES, STATUS_TIME_TO_LIVE);
        this.taskScheduler = taskScheduler;
        this.deletionExecutor = deletionExecutor;
    }

    /**
     * The scope of the deletion of an exercise.
     *
     * @param exerciseId the id of the exercise
     * @return the key under which the deletion is claimed and its status is kept
     */
    public static String exerciseScopeKey(long exerciseId) {
        return "exercise:" + exerciseId;
    }

    /**
     * The scope of the deletion of an exam or of one of its exercise groups. They share a scope, so an exercise group cannot be deleted while its exam is deleted, and two
     * exercise groups of one exam are deleted one after the other.
     *
     * @param examId the id of the exam
     * @return the key under which the deletion is claimed and its status is kept
     */
    public static String examScopeKey(long examId) {
        return "exam:" + examId;
    }

    /**
     * Takes the claims for the scopes and runs the job in the background. Returns as soon as the job is scheduled.
     *
     * @param scopeKey            the scope of the deletion, under which its status is kept, see {@link #exerciseScopeKey(long)} and {@link #examScopeKey(long)}
     * @param additionalScopeKeys further scopes the deletion holds, for example the scopes of the exercises of an exam
     * @param entityType          the kind of entity that is deleted
     * @param entityId            the id of the entity that is deleted
     * @param destination         the websocket destination the progress is sent to
     * @param totalSteps          the number of steps the job reports
     * @param totalWeight         the sum of the weights of all steps, which the job reports to the tracker
     * @param onAccepted          runs in the calling thread once the claims are taken and before the job starts, for example to audit the request or to cancel the schedule of a
     *                                quiz. It does not run if another deletion holds one of the scopes. If it throws, the deletion does not start.
     * @param job                 the work to do in the background
     * @throws ConflictException if another deletion holds one of the scopes
     */
    public void startAsync(String scopeKey, List<String> additionalScopeKeys, EntityDeletionType entityType, long entityId, WebsocketDestination destination, int totalSteps,
            double totalWeight, Runnable onAccepted, DeletionJob job) {
        EntityDeletionClaim claim = acquireClaims(scopeKey, additionalScopeKeys, entityType, entityId, destination, totalSteps);
        boolean statusPublished = false;
        try {
            onAccepted.run();
            sendAndStore(claim, newStatus(claim, EntityDeletionStatus.IN_PROGRESS, null, 0, 0, 0, null, 0.0, null));
            statusPublished = true;
            deletionExecutor.execute(() -> runJob(claim, totalWeight, job));
        }
        catch (RuntimeException | Error failure) {
            if (statusPublished) {
                // Clients already saw the deletion start, so they have to learn that it did not.
                finish(claim, newStatus(claim, EntityDeletionStatus.FAILED, null, 0, 0, 0, null, 0.0, GENERIC_ERROR_MESSAGE));
            }
            else {
                releaseClaims(claim);
            }
            throw failure;
        }
    }

    /**
     * Remembers the course of the entity that is deleted, for as long as its status is kept. Once the entity is gone, the instructors of the course are still allowed to read
     * the status of its deletion, which cannot be derived from the deleted entity any more.
     *
     * @param scopeKey the scope of the deletion
     * @param courseId the id of the course the entity belongs to
     */
    public void rememberCourse(String scopeKey, long courseId) {
        courseIds.put(scopeKey, courseId);
    }

    /**
     * Gets the course that was remembered for the deletion of the scope.
     *
     * @param scopeKey the scope of the deletion
     * @return the id of the course, or empty if no deletion of the scope ran in the last hour
     */
    public Optional<Long> findCourseId(String scopeKey) {
        return Optional.ofNullable(courseIds.get(scopeKey));
    }

    /**
     * Gets the latest known status of the deletion that holds or last held the scope.
     *
     * @param scopeKey the scope of the deletion
     * @return the status, or empty if no deletion ran in the last hour. A deletion that is reported as running although nobody holds its claim any more, for example because
     *         its node stopped, is reported as failed.
     */
    public Optional<EntityDeletionProgressDTO> getProgress(String scopeKey) {
        EntityDeletionProgressDTO status;
        String holder;
        // finish() publishes the final status and releases the claim under this lock, so a poll that overlaps a successful completion cannot see the old running status
        // without its claim and report it as interrupted.
        claims.lock(scopeKey);
        try {
            status = statuses.get(scopeKey);
            holder = claims.get(scopeKey);
        }
        finally {
            claims.unlock(scopeKey);
        }
        if (status != null && status.status() == EntityDeletionStatus.IN_PROGRESS && holder == null) {
            return Optional.of(new EntityDeletionProgressDTO(status.entityType(), status.entityId(), EntityDeletionStatus.FAILED, status.currentStep(), status.stepsCompleted(),
                    status.totalSteps(), 0, 0, null, status.weightedProgressPercent(), status.startedAt(), status.updatedAt(), INTERRUPTED_ERROR_MESSAGE));
        }
        return Optional.ofNullable(status);
    }

    /**
     * Describes why a deletion failed in a single short line for the clients, who are all instructors of the course. Messages that may contain SQL, paths or other internals
     * are replaced by a generic text, the details are in the server log.
     *
     * @param failure what went wrong
     * @return a message without line breaks that is at most 200 characters long
     */
    static String describeFailure(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return GENERIC_ERROR_MESSAGE;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        boolean internal = root != failure || lower.contains("sql") || lower.contains("hibernate") || lower.contains("exception") || message.contains("/") || message.contains("\\")
                || message.contains("\n") || message.contains("\r");
        if (internal) {
            return GENERIC_ERROR_MESSAGE;
        }
        String singleLine = message.strip();
        return singleLine.length() <= MAX_ERROR_MESSAGE_LENGTH ? singleLine : singleLine.substring(0, MAX_ERROR_MESSAGE_LENGTH - 3) + "...";
    }

    private EntityDeletionClaim acquireClaims(String scopeKey, List<String> additionalScopeKeys, EntityDeletionType entityType, long entityId, WebsocketDestination destination,
            int totalSteps) {
        EntityDeletionClaim claim = new EntityDeletionClaim(scopeKey, List.copyOf(additionalScopeKeys), entityType, entityId, destination, totalSteps, ZonedDateTime.now(),
                UUID.randomUUID());
        String token = claim.ownerToken().toString();
        List<String> taken = new ArrayList<>();
        try {
            for (String key : claim.allScopeKeys()) {
                claims.lock(key);
                try {
                    if (claims.putIfAbsent(key, token) != null) {
                        throw new ConflictException("Another deletion is already in progress", entityType.name(), "deletionInProgress");
                    }
                    taken.add(key);
                }
                finally {
                    claims.unlock(key);
                }
            }
            ScheduledFuture<?> renewal = taskScheduler.scheduleAtFixedRate(() -> renewClaims(claim), Instant.now().plus(CLAIM_RENEWAL_INTERVAL), CLAIM_RENEWAL_INTERVAL);
            claimRenewals.put(claim, renewal);
        }
        catch (RuntimeException | Error failure) {
            removeClaims(claim, taken);
            throw failure;
        }
        return claim;
    }

    private void runJob(EntityDeletionClaim claim, double totalWeight, DeletionJob job) {
        // The worker thread has no caller to inherit an authentication from, but the repositories behind the deletion expect one. The instructor who requested the deletion was
        // checked and audited in the request.
        SecurityUtils.runAsSystem(() -> {
            DeletionProgressTracker tracker = new DeletionProgressTracker(this, claim, totalWeight);
            boolean deleted = false;
            try {
                job.run(tracker);
                deleted = true;
            }
            catch (Throwable failure) {
                log.error("Failed to delete {} {}", claim.entityType(), claim.entityId(), failure);
                try {
                    finish(claim, newStatus(claim, EntityDeletionStatus.FAILED, tracker.currentStep(), tracker.stepsCompleted(), claim.totalSteps(), 0, null,
                            tracker.percentOfCompleted(), describeFailure(failure)));
                }
                catch (RuntimeException | Error reportingFailure) {
                    failure.addSuppressed(reportingFailure);
                }
                if (failure instanceof Error error) {
                    throw error;
                }
            }
            finally {
                try {
                    if (deleted) {
                        reportCompleted(claim);
                    }
                }
                finally {
                    releaseClaims(claim);
                }
            }
        });
    }

    /**
     * Publishes the successful outcome. The entity is gone at this point, so a failure of the status store or the messaging must never be turned into a failed deletion. The
     * claims are released by the caller, and clients that miss the message find the entity gone.
     */
    private void reportCompleted(EntityDeletionClaim claim) {
        try {
            finish(claim, newStatus(claim, EntityDeletionStatus.COMPLETED, null, claim.totalSteps(), claim.totalSteps(), 0, null, 100.0, null));
        }
        catch (RuntimeException | Error reportingFailure) {
            log.error("Deleted {} {}, but its completion could not be reported", claim.entityType(), claim.entityId(), reportingFailure);
        }
    }

    void updateProgress(EntityDeletionClaim claim, String currentStep, int stepsCompleted, int itemsProcessed, int totalItems, String currentItemTitle, double percent,
            boolean verifyAllClaims) {
        // Renews the claims as well, and stops a worker that lost one of them, so a stale worker cannot go on deleting while another deletion runs.
        verifyClaims(claim, verifyAllClaims);
        sendAndStore(claim, newStatus(claim, EntityDeletionStatus.IN_PROGRESS, currentStep, stepsCompleted, itemsProcessed, totalItems, currentItemTitle, percent, null));
    }

    private EntityDeletionProgressDTO newStatus(EntityDeletionClaim claim, EntityDeletionStatus status, String currentStep, int stepsCompleted, int itemsProcessed, int totalItems,
            String currentItemTitle, double percent, String errorMessage) {
        int steps = status == EntityDeletionStatus.COMPLETED ? claim.totalSteps() : stepsCompleted;
        return new EntityDeletionProgressDTO(claim.entityType(), claim.entityId(), status, currentStep, steps, claim.totalSteps(), itemsProcessed, totalItems, currentItemTitle,
                percent, claim.startedAt(), ZonedDateTime.now(), errorMessage);
    }

    /**
     * Publishes the final status and releases the claims in one step, so a client that sees the final status can start a new deletion at once.
     */
    private void finish(EntityDeletionClaim claim, EntityDeletionProgressDTO status) {
        claims.lock(claim.scopeKey());
        try {
            // Somebody else may hold the scope after the claim expired. If nobody does, the outcome is still worth reporting.
            if (ownsClaim(claim) || claims.get(claim.scopeKey()) == null) {
                sendAndStore(claim, status);
            }
        }
        finally {
            try {
                releaseClaims(claim);
            }
            finally {
                claims.unlock(claim.scopeKey());
            }
        }
    }

    /**
     * Checks that the worker still owns its claims and renews them. The claim of the scope of the status is always checked. The claims of the further scopes, for example of
     * the exercises of an exam, are only checked if requested, which keeps frequent progress reports cheap.
     */
    private void verifyClaims(EntityDeletionClaim claim, boolean all) {
        List<String> keys = all ? heldKeys(claim) : List.of(claim.scopeKey());
        String token = claim.ownerToken().toString();
        for (String key : keys) {
            claims.lock(key);
            try {
                if (!token.equals(claims.get(key))) {
                    throw new IllegalStateException("The claim " + key + " for the deletion of " + claim.entityType() + " " + claim.entityId() + " was lost");
                }
                claims.put(key, token);
            }
            finally {
                claims.unlock(key);
            }
        }
    }

    /**
     * Claims the scope of one more entity for a running deletion, at the moment the deletion is about to delete it. The scopes known when the deletion was accepted are held
     * already. An entity that joined the deleted exam or exercise group since then may be deleted on its own at the same time, in which case the deletion fails instead of
     * deleting the same data twice.
     *
     * @param claim    the claim of the running deletion
     * @param scopeKey the scope of the entity that is deleted next
     * @throws ConflictException if another deletion holds the scope
     */
    void claimAdditionalScope(EntityDeletionClaim claim, String scopeKey) {
        if (heldKeys(claim).contains(scopeKey)) {
            return;
        }
        claims.lock(scopeKey);
        try {
            if (claims.putIfAbsent(scopeKey, claim.ownerToken().toString()) != null) {
                throw new ConflictException("An exercise of this deletion is already being deleted by another request", claim.entityType().name(), "deletionInProgress");
            }
            lateScopeKeys.computeIfAbsent(claim.ownerToken(), token -> ConcurrentHashMap.newKeySet()).add(scopeKey);
        }
        finally {
            claims.unlock(scopeKey);
        }
    }

    private List<String> heldKeys(EntityDeletionClaim claim) {
        List<String> keys = claim.allScopeKeys();
        Set<String> late = lateScopeKeys.get(claim.ownerToken());
        if (late != null) {
            keys.addAll(late);
        }
        return keys;
    }

    private void renewClaims(EntityDeletionClaim claim) {
        String token = claim.ownerToken().toString();
        try {
            boolean ownershipLost = false;
            for (String key : heldKeys(claim)) {
                claims.lock(key);
                try {
                    if (token.equals(claims.get(key))) {
                        claims.put(key, token);
                        if (key.equals(claim.scopeKey())) {
                            refreshStatus(claim);
                        }
                    }
                    else {
                        ownershipLost = true;
                    }
                }
                finally {
                    claims.unlock(key);
                }
            }
            if (ownershipLost) {
                stopRenewal(claim);
            }
        }
        catch (RuntimeException e) {
            log.warn("Failed to renew the claims for the deletion of {} {}", claim.entityType(), claim.entityId(), e);
        }
    }

    /**
     * Keeps the status of a running deletion alive and fresh while its claim is renewed, also if a step takes very long without a progress report. The caller holds the lock
     * of the scope. Nothing is sent to the clients, they only see the newer time of the update when they read the status.
     */
    private void refreshStatus(EntityDeletionClaim claim) {
        // The read-modify-write must not interleave with a progress write of the worker, or the heartbeat would bring back the previous step.
        statusWriteLock.lock();
        try {
            EntityDeletionProgressDTO status = statuses.get(claim.scopeKey());
            if (status != null && status.status() == EntityDeletionStatus.IN_PROGRESS) {
                statuses.put(claim.scopeKey(),
                        new EntityDeletionProgressDTO(status.entityType(), status.entityId(), status.status(), status.currentStep(), status.stepsCompleted(), status.totalSteps(),
                                status.itemsProcessed(), status.totalItems(), status.currentItemTitle(), status.weightedProgressPercent(), status.startedAt(), ZonedDateTime.now(),
                                status.errorMessage()));
            }
        }
        finally {
            statusWriteLock.unlock();
        }
    }

    /**
     * Releases all claims of the deletion without publishing a status. A stale deletion cannot release the claims of a newer one.
     */
    private void releaseClaims(EntityDeletionClaim claim) {
        List<String> keys = heldKeys(claim);
        lateScopeKeys.remove(claim.ownerToken());
        removeClaims(claim, keys);
    }

    private void removeClaims(EntityDeletionClaim claim, List<String> keys) {
        try {
            String token = claim.ownerToken().toString();
            for (String key : keys) {
                try {
                    claims.remove(key, token);
                }
                catch (RuntimeException e) {
                    log.warn("Failed to release the claim {} for the deletion of {} {}, it expires automatically", key, claim.entityType(), claim.entityId(), e);
                }
            }
        }
        finally {
            stopRenewal(claim);
        }
    }

    private void stopRenewal(EntityDeletionClaim claim) {
        ScheduledFuture<?> renewal = claimRenewals.remove(claim);
        if (renewal != null) {
            renewal.cancel(false);
        }
    }

    private boolean ownsClaim(EntityDeletionClaim claim) {
        return claim.ownerToken().toString().equals(claims.get(claim.scopeKey()));
    }

    private void sendAndStore(EntityDeletionClaim claim, EntityDeletionProgressDTO status) {
        // Hand the message over first: whoever reads the stored status afterwards (the status endpoint, or a test that waits for it) can rely on it having been submitted.
        // The sending itself is asynchronous and never throws, a failure only completes the returned future.
        // A lost progress message must not fail a deletion, clients can read the stored status.
        websocketMessagingService.sendMessage(claim.destination(), status).whenComplete((ignored, failure) -> {
            if (failure != null) {
                log.warn("Failed to send the progress of the deletion of {} {}: {}", claim.entityType(), claim.entityId(), failure.toString());
            }
        });
        statusWriteLock.lock();
        try {
            statuses.put(claim.scopeKey(), status);
        }
        finally {
            statusWriteLock.unlock();
        }
    }
}
