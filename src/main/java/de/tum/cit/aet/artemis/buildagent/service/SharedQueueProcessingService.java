package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildLogDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.distributed.api.queue.listener.QueueItemListener;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

/**
 * Orchestrates consumption of build jobs from the shared (Hazelcast) queue on this node.
 *
 * <p>
 * <strong>Responsibilities</strong>
 * </p>
 * <ul>
 * <li>Observe the distributed build queue and dequeue when local capacity allows.</li>
 * <li>Submit jobs to the local executor via {@link BuildJobManagementService} and track completion.</li>
 * <li>Publish {@link ResultQueueItem}s to the distributed result queue (unless paused).</li>
 * <li>Maintain per-node liveness/telemetry and clean up state for offline nodes.</li>
 * <li>Provide controlled pause/resume with a grace period and safe cancellation/requeue.</li>
 * </ul>
 *
 * <p>
 * <strong>Concurrency model</strong>
 * </p>
 * <ul>
 * <li>{@link #availabilityAndDequeueLock} serializes the decision to dequeue + register a processing job
 * to avoid races with concurrent availability checks and listener-driven triggers.</li>
 * <li>{@link #agentStateTransitionLock} serializes pause/resume transitions across:
 * listener lifecycle, scheduler lifecycle, result publication gating, and cancellation/requeue.</li>
 * <li>Operational counters/flags use {@link AtomicInteger}/{@link AtomicBoolean} for non-blocking reads.</li>
 * </ul>
 *
 * <p>
 * <strong>Failure handling</strong>
 * </p>
 * <ul>
 * <li>Rejected submissions: requeue with bounded retries, update agent info.</li>
 * <li>Job failures/timeouts/cancellations: map to {@link BuildStatus}, collect logs, publish result, update telemetry.</li>
 * <li>Repeated failures: auto-pause after configurable threshold.</li>
 * </ul>
 */
@Profile(PROFILE_BUILDAGENT)
@Lazy(false)
@Service
public class SharedQueueProcessingService {

    private static final Logger log = LoggerFactory.getLogger(SharedQueueProcessingService.class);

    private static final Duration BUILD_CHECK_AVAILABILITY_INTERVAL = Duration.ofSeconds(5);

    /**
     * Interval between retries when waiting for cluster connection during startup.
     * Uses the same interval as the availability check for consistency.
     */
    private static final Duration CLUSTER_CONNECTION_RETRY_INTERVAL = Duration.ofSeconds(5);

    /**
     * Maximum number of consecutive stale detections before force-cleaning a job.
     * With a 5-second detection interval, 6 detections = 30 seconds grace period.
     * This only applies AFTER the minimum job age has been reached.
     */
    private static final int MAX_CONSECUTIVE_STALE_DETECTIONS = 6;

    /**
     * Minimum job age in seconds before stale detection applies.
     * This grace period allows for slow Docker image pulls and repository cloning.
     * Jobs younger than this won't be considered stale even without a container.
     */
    private static final int STALE_DETECTION_MIN_JOB_AGE_SECONDS = 120;

    /**
     * Tracks consecutive stale detection counts per job ID.
     * Used to identify truly stuck jobs vs jobs that are still starting up.
     * Jobs are removed from this map when they complete or are force-cleaned.
     */
    private final Map<String, Integer> staleJobDetectionCounts = new ConcurrentHashMap<>();

    private final BuildAgentConfiguration buildAgentConfiguration;

    private final BuildJobManagementService buildJobManagementService;

    private final BuildLogsMap buildLogsMap;

    private final AtomicInteger consecutiveBuildJobFailures = new AtomicInteger(0);

    private final AtomicInteger localProcessingJobs = new AtomicInteger(0);

    private final BuildAgentInformationService buildAgentInformationService;

    private final TaskScheduler taskScheduler;

    private final BuildAgentDockerService buildAgentDockerService;

    private final BuildJobContainerService buildJobContainerService;

    private final DistributedDataAccessService distributedDataAccessService;

    /**
     * Serializes availability checks with dequeue+registration of a processing job.
     * <p>
     * Prevents races among timer-driven checks and queue event callbacks that could:
     * (a) over-dequeue beyond local capacity or (b) register inconsistent processing state.
     * </p>
     */
    private final ReentrantLock availabilityAndDequeueLock = new ReentrantLock();

    /**
     * Serializes agent state transitions (pause/resume) and their side effects:
     * <ul>
     * <li>Listener attach/detach</li>
     * <li>Scheduler start/stop</li>
     * <li>Graceful wait for jobs, then cancellation + requeue</li>
     * </ul>
     */
    private final ReentrantLock agentStateTransitionLock = new ReentrantLock();

    private UUID listenerId;

    /** UUID of the pause build agent message listener. Stored to allow removal on reconnection. */
    private UUID pauseListenerId;

    /** UUID of the resume build agent message listener. Stored to allow removal on reconnection. */
    private UUID resumeListenerId;

    /** Scheduled future for checking availability and processing next build job. */
    private ScheduledFuture<?> scheduledFuture;

    /** Flag to indicate whether the build agent is paused. */
    private final AtomicBoolean isPaused = new AtomicBoolean(false);

    /** Flag to track whether initialization has completed. Uses AtomicBoolean for thread-safe access. */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /** Scheduled future for retrying cluster connection when build agent starts before core nodes. */
    private ScheduledFuture<?> connectionRetryFuture;

    @Value("${artemis.continuous-integration.pause-grace-period-seconds:60}")
    private int pauseGracePeriodSeconds;

    @Value("${artemis.continuous-integration.build-agent.short-name}")
    private String buildAgentShortName;

    @Value("${artemis.continuous-integration.build-agent.display-name:}")
    private String buildAgentDisplayName;

    @Value("${artemis.continuous-integration.build-container-prefix:local-ci-}")
    private String buildContainerPrefix;

    /** @return true if the build agent is paused, false otherwise */
    public boolean isPaused() {
        return isPaused.get();
    }

    /**
     * Sets the pause state (for tests only).
     *
     * @param paused true to pause the build agent, false to resume
     */
    public void setPauseState(boolean paused) {
        isPaused.set(paused);
    }

    /**
     * Resets the initialized state so that init() will re-register all listeners.
     * This is useful for tests that need to re-initialize the service after calling
     * removeListenerAndCancelScheduledFuture().
     * (for tests only)
     */
    public void resetInitializedState() {
        initialized.set(false);
    }

    public SharedQueueProcessingService(BuildAgentConfiguration buildAgentConfiguration, BuildJobManagementService buildJobManagementService, BuildLogsMap buildLogsMap,
            TaskScheduler taskScheduler, BuildAgentDockerService buildAgentDockerService, BuildJobContainerService buildJobContainerService,
            BuildAgentInformationService buildAgentInformationService, DistributedDataAccessService distributedDataAccessService) {
        this.buildAgentConfiguration = buildAgentConfiguration;
        this.buildJobManagementService = buildJobManagementService;
        this.buildLogsMap = buildLogsMap;
        this.buildAgentInformationService = buildAgentInformationService;
        this.taskScheduler = taskScheduler;
        this.buildAgentDockerService = buildAgentDockerService;
        this.buildJobContainerService = buildJobContainerService;
        this.distributedDataAccessService = distributedDataAccessService;
    }

    /**
     * Initialize the service by validating configuration and setting up distributed listeners.
     * <p>
     * When running as a Hazelcast client with asyncStart=true, the client may not yet be
     * connected to the cluster when this method is called. In that case, we schedule
     * periodic retries until the connection is established and initialization completes.
     * <p>
     * Additionally, a connection state listener is registered to handle reconnection after
     * a connection loss. When the client reconnects to the cluster, the listener re-initializes
     * the distributed listeners (queue, topics) which may have been lost during the disconnection.
     * <p>
     * EventListener cannot be used here, as the bean is lazy
     * <a href="https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events-annotation">Spring Docs</a>
     */
    @PostConstruct
    public void init() {
        // Validate build agent short name - this doesn't require cluster connection
        if (!buildAgentShortName.matches("^[a-z0-9-]+$")) {
            String errorMessage = "Build agent short name must not be empty and only contain lowercase letters, numbers and hyphens."
                    + " Build agent short name should be changed in the application properties under 'artemis.continuous-integration.build-agent.short-name'.";
            log.error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        if (StringUtils.isBlank(buildAgentDisplayName)) {
            buildAgentDisplayName = buildAgentShortName;
        }

        // Register a connection state listener to handle both initial connection and reconnection.
        // On reconnection (isInitialConnection=false), the distributed listeners need to be re-registered
        // because they may have been lost when the connection was interrupted.
        distributedDataAccessService.addConnectionStateListener(isInitialConnection -> {
            if (!isInitialConnection) {
                // This is a reconnection - reset the initialized flag so listeners are re-registered
                log.info("Reconnected to the distributed data provider. Re-initializing SharedQueueProcessingService listeners.");
                initialized.set(false);
                // Also cancel existing scheduled future if it's still running, as a new one will be created
                cancelCheckAvailabilityAndProcessNextBuildScheduledFuture();
            }
            boolean initSucceeded = tryInitialize();
            // If initialization failed after reconnection, schedule retries
            if (!initSucceeded && !distributedDataAccessService.isConnectedToCluster()) {
                if (connectionRetryFuture == null || connectionRetryFuture.isDone()) {
                    connectionRetryFuture = taskScheduler.scheduleAtFixedRate(() -> {
                        if (tryInitialize()) {
                            if (connectionRetryFuture != null) {
                                connectionRetryFuture.cancel(false);
                            }
                        }
                    }, CLUSTER_CONNECTION_RETRY_INTERVAL);
                }
            }
        });

        // If already connected, tryInitialize was called by the listener above.
        // If not connected yet, schedule periodic retries as a fallback.
        if (!initialized.get() && !distributedDataAccessService.isConnectedToCluster()) {
            log.info("Not connected to the distributed data provider yet. Scheduling periodic initialization retries every {} seconds.",
                    CLUSTER_CONNECTION_RETRY_INTERVAL.toSeconds());

            connectionRetryFuture = taskScheduler.scheduleAtFixedRate(() -> {
                if (tryInitialize()) {
                    // Initialization succeeded - cancel the retry task
                    if (connectionRetryFuture != null) {
                        connectionRetryFuture.cancel(false);
                    }
                }
            }, CLUSTER_CONNECTION_RETRY_INTERVAL);
        }
    }

    /**
     * Attempts to initialize the distributed listeners and scheduled tasks.
     * <p>
     * This method checks if the Hazelcast client is connected to the cluster before
     * attempting to access distributed data structures. If not connected, it returns
     * false so the caller can retry later.
     *
     * @return true if initialization succeeded, false if not connected to cluster
     */
    private synchronized boolean tryInitialize() {
        if (initialized.get()) {
            return true;
        }

        if (!distributedDataAccessService.isConnectedToCluster()) {
            log.debug("Cannot initialize SharedQueueProcessingService: not connected to the distributed data provider yet");
            return false;
        }

        try {
            // Remove listener if already present (for idempotency)
            if (this.listenerId != null) {
                distributedDataAccessService.getDistributedBuildJobQueue().removeListener(this.listenerId);
            }
            // Cancel existing scheduled task if present (for idempotency on partial failure retry)
            cancelCheckAvailabilityAndProcessNextBuildScheduledFuture();

            log.info("Adding item listener to the distributed build job queue for build agent with address {}", distributedDataAccessService.getLocalMemberAddress());
            this.listenerId = this.distributedDataAccessService.getDistributedBuildJobQueue().addItemListener(new QueuedBuildJobItemListener());

            /*
             * Check every 5 seconds whether the node has at least one thread available for a new build job.
             * If so, process the next build job.
             * This is a backup mechanism in case the build queue is not empty, no new build jobs are entering the queue and the
             * node otherwise stopped checking for build jobs in the queue.
             */
            scheduledFuture = taskScheduler.scheduleAtFixedRate(this::checkAvailabilityAndProcessNextBuild, BUILD_CHECK_AVAILABILITY_INTERVAL);

            var pauseTopic = distributedDataAccessService.getPauseBuildAgentTopic();
            var resumeTopic = distributedDataAccessService.getResumeBuildAgentTopic();

            // Remove old listeners if they exist (prevents duplicate listeners on reconnection)
            if (pauseListenerId != null) {
                pauseTopic.removeMessageListener(pauseListenerId);
                pauseListenerId = null;
            }
            if (resumeListenerId != null) {
                resumeTopic.removeMessageListener(resumeListenerId);
                resumeListenerId = null;
            }

            pauseListenerId = pauseTopic.addMessageListener(buildAgentName -> {
                if (buildAgentShortName.equals(buildAgentName)) {
                    pauseBuildAgent(false);
                }
            });

            resumeListenerId = resumeTopic.addMessageListener(buildAgentName -> {
                if (buildAgentShortName.equals(buildAgentName)) {
                    resumeBuildAgent();
                }
            });

            initialized.set(true);
            log.info("SharedQueueProcessingService initialized successfully - listening for build jobs");
            return true;
        }
        catch (Exception e) {
            // This can happen if the connection is lost between the check and the access
            log.warn("Failed to initialize SharedQueueProcessingService: {}. Will retry.", e.getMessage());
            return false;
        }
    }

    /**
     * Cleanup method called when the service is being destroyed.
     * Removes the queue listener, cancels the scheduled availability check, and cancels
     * any pending connection retry task.
     */
    @PreDestroy
    public void removeListenerAndCancelScheduledFuture() {
        // Cancel connection retry task if it's running (for build agents that never connected)
        if (connectionRetryFuture != null && !connectionRetryFuture.isCancelled()) {
            connectionRetryFuture.cancel(false);
        }
        removeListener();
        cancelCheckAvailabilityAndProcessNextBuildScheduledFuture();
    }

    /** Removes all listeners (queue, pause, resume) if the Hazelcast instance is running. */
    private void removeListener() {
        if (distributedDataAccessService.isInstanceRunning()) {
            if (this.listenerId != null) {
                distributedDataAccessService.getDistributedBuildJobQueue().removeListener(this.listenerId);
            }
            if (this.pauseListenerId != null) {
                distributedDataAccessService.getPauseBuildAgentTopic().removeMessageListener(this.pauseListenerId);
            }
            if (this.resumeListenerId != null) {
                distributedDataAccessService.getResumeBuildAgentTopic().removeMessageListener(this.resumeListenerId);
            }
        }
    }

    /**
     * Removes the queue listener and cancels the scheduled availability check without removing
     * the pause/resume topic listeners. Used when pausing the build agent, as it should still
     * be able to receive resume commands.
     */
    private void removeQueueListenerAndCancelScheduledTask() {
        if (distributedDataAccessService.isInstanceRunning() && this.listenerId != null) {
            distributedDataAccessService.getDistributedBuildJobQueue().removeListener(this.listenerId);
            this.listenerId = null;
        }
        cancelCheckAvailabilityAndProcessNextBuildScheduledFuture();
    }

    /** Cancels the scheduled availability check, allowing current execution to complete gracefully. */
    private void cancelCheckAvailabilityAndProcessNextBuildScheduledFuture() {
        if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
            scheduledFuture.cancel(false);
        }
    }

    /** Update the build agent information every 10s (not updated automatically when node joins cluster). */
    @Scheduled(initialDelay = 10_000, fixedRate = 10_000)
    public void updateBuildAgentInformation() {
        // Skip if not connected to cluster (happens when build agent starts before core nodes)
        if (!distributedDataAccessService.isConnectedToCluster()) {
            log.debug("Not connected to the distributed data provider yet. Skipping build agent information update.");
            return;
        }

        if (distributedDataAccessService.noDataMemberInClusterAvailable()) {
            log.debug("There are only lite member in the cluster. Not updating build agent information.");
            return;
        }

        removeOfflineNodes();

        // Add build agent information to map if not already present
        // Use buildAgentShortName as the key since that's what BuildAgentInformationService uses
        if (!distributedDataAccessService.getBuildAgentInformationMap().containsKey(buildAgentShortName)) {
            buildAgentInformationService.updateLocalBuildAgentInformation(isPaused.get());
        }
    }

    /**
     * Detects stale build jobs by verifying that running builds have corresponding Docker containers.
     * This scheduled task runs every 5 seconds to identify and clean up orphaned/stuck builds.
     * <p>
     * A build job is considered stale if:
     * <ul>
     * <li>It is tracked as running in the local job management service, but has no Docker container</li>
     * <li>It exists in the distributed processing jobs map but not in the local running jobs</li>
     * </ul>
     * <p>
     * When a stale build is detected, the system:
     * <ul>
     * <li>Removes it from the distributed processing jobs map</li>
     * <li>Updates the build agent information to reflect accurate job counts</li>
     * </ul>
     */
    @Scheduled(initialDelay = 30_000, fixedRate = 5_000)
    public void detectAndCleanupStaleBuildJobs() {
        // Skip if not connected to cluster or if paused
        if (!distributedDataAccessService.isConnectedToCluster() || isPaused.get()) {
            return;
        }

        try {
            // Get locally tracked running job IDs
            Set<String> localRunningJobIds = buildJobManagementService.getRunningBuildJobIds();

            // Clean up tracking for jobs that are no longer running
            staleJobDetectionCounts.keySet().removeIf(jobId -> !localRunningJobIds.contains(jobId));

            // Check each local running job against Docker containers
            for (String jobId : localRunningJobIds) {
                String containerName = buildContainerPrefix + jobId;
                String containerId = buildJobContainerService.getIDOfRunningContainer(containerName);

                if (containerId == null) {
                    // Job is tracked as running but has no container - this could be:
                    // 1. Container was killed externally
                    // 2. Container startup failed but job wasn't cleaned up
                    // 3. Job is still starting up (image pull, repo clone in progress)

                    // A job that is pulling its Docker image has no container yet by definition, and a pull can legitimately take longer than the grace period below.
                    // The pull is bounded by its own timeout (artemis.continuous-integration.image-pull-timeout-seconds), so it does not need this watchdog as a backstop.
                    if (buildAgentDockerService.isImagePullInProgress(jobId)) {
                        log.debug("Job {} is currently pulling its Docker image, skipping stale detection", jobId);
                        staleJobDetectionCounts.remove(jobId);
                        continue;
                    }

                    // Check job age - don't consider jobs stale during startup grace period
                    // This allows time for Docker image pulls and repository cloning
                    BuildJobQueueItem job = distributedDataAccessService.getDistributedProcessingJobs().get(jobId);
                    ZonedDateTime buildStartDate = job != null && job.jobTimingInfo() != null ? job.jobTimingInfo().buildStartDate() : null;
                    if (buildStartDate == null) {
                        // We cannot tell how old the job is, so we cannot tell whether it is still starting up. Skipping is the safe choice: a genuinely stuck job without
                        // a container is still caught by the orphan cross-check below, whereas counting it as stale here cancels jobs that just started. This happens when
                        // another agent force-cancelled and requeued the job while this agent was picking it up, which removes it from the distributed processing map.
                        // Reset the counter as well, so that earlier detections cannot add up with later ones and cancel the job the moment its entry reappears.
                        log.debug("Job {} has no known build start date, skipping stale detection", jobId);
                        staleJobDetectionCounts.remove(jobId);
                        continue;
                    }
                    long jobAgeSeconds = Duration.between(buildStartDate, ZonedDateTime.now()).getSeconds();
                    if (jobAgeSeconds < STALE_DETECTION_MIN_JOB_AGE_SECONDS) {
                        // Job is still in startup grace period - skip stale detection
                        log.debug("Job {} is {} seconds old (< {} min grace period), skipping stale detection", jobId, jobAgeSeconds, STALE_DETECTION_MIN_JOB_AGE_SECONDS / 60);
                        continue;
                    }

                    int consecutiveCount = staleJobDetectionCounts.merge(jobId, 1, Integer::sum);

                    if (consecutiveCount >= MAX_CONSECUTIVE_STALE_DETECTIONS) {
                        // Job has been stale for too long - force cleanup and requeue
                        log.error("Build job {} has been stale for {} consecutive checks (~{} seconds). Force-cancelling and requeuing.", jobId, consecutiveCount,
                                consecutiveCount * 5);

                        // Cancel the build job properly so it's recognized as CANCELLED, not FAILED.
                        // This adds the job to cancelledBuildJobs set which the exceptionally handler checks.
                        // IMPORTANT: cancelBuildJob() calls future.cancel(true), which completes the future
                        // with a CancellationException. This triggers the exceptionally handler (see processBuild),
                        // which calls removeProcessingJob() to decrement localProcessingJobs.
                        // We must NOT decrement localProcessingJobs here to avoid a double decrement.
                        buildJobManagementService.cancelBuildJob(jobId);

                        // Remove from distributed processing jobs map immediately (don't wait for exceptionally handler).
                        // The exceptionally handler's removeProcessingJob() will call remove() again, but that's
                        // a no-op since the job is already removed from the map.
                        BuildJobQueueItem staleJob = distributedDataAccessService.getDistributedProcessingJobs().remove(jobId);
                        if (staleJob != null && staleJob.retryCount() < 5) {
                            BuildJobQueueItem requeuedJob = new BuildJobQueueItem(staleJob, new BuildAgentDTO("", "", ""), staleJob.retryCount() + 1);
                            log.info("Requeuing stale build job {} with retry count {}", jobId, requeuedJob.retryCount());
                            distributedDataAccessService.getDistributedBuildJobQueue().add(requeuedJob);
                        }
                        else if (staleJob != null) {
                            log.error("Stale build job {} exceeded maximum retry count ({}). Not requeuing.", jobId, staleJob.retryCount());
                        }

                        // Clean up stale detection state. Note: We do NOT decrement localProcessingJobs here
                        // because the exceptionally handler will do it when the cancelled future completes.
                        // See the documentation on removeProcessingJob() for the counter management contract.
                        staleJobDetectionCounts.remove(jobId);
                        buildAgentInformationService.updateLocalBuildAgentInformation(isPaused.get());
                    }
                    else {
                        log.warn("Stale build job detected: job {} has no running Docker container '{}' (detection count: {}/{})", jobId, containerName, consecutiveCount,
                                MAX_CONSECUTIVE_STALE_DETECTIONS);
                    }
                }
                else {
                    // Job has a container - reset stale detection count
                    staleJobDetectionCounts.remove(jobId);
                }
            }

            // Cross-check distributed processing jobs against local state
            // Get jobs in distributed map that are assigned to this agent (by agent name, not member address)
            List<BuildJobQueueItem> distributedJobs = distributedDataAccessService.getProcessingJobsForAgentByName(buildAgentShortName);

            // Find jobs in distributed map but not tracked locally (orphaned in distributed state)
            for (BuildJobQueueItem distributedJob : distributedJobs) {
                if (localRunningJobIds.contains(distributedJob.id())) {
                    continue;
                }
                // The same grace period the container check above applies, and for a stronger reason. Claiming a job
                // publishes it to the distributed map before executeBuildJob registers its future locally, so a job
                // that has just been claimed is legitimately absent from localRunningJobIds for a moment - and the
                // same holds in reverse once the future completes and before the entry is removed. Removing it in
                // that window used to skew the running-job counts; since a build agent's clone is authorized against
                // this very map it now fails the build outright, with a 401 on the repository the job was cloning.
                //
                // Bounded, not disabled: a job whose agent really did disappear mid-claim is still removed once it is
                // older than the grace period, which is the case this cross-check exists for.
                ZonedDateTime claimedAt = distributedJob.jobTimingInfo() != null ? distributedJob.jobTimingInfo().buildStartDate() : null;
                if (claimedAt == null || Duration.between(claimedAt, ZonedDateTime.now()).getSeconds() < STALE_DETECTION_MIN_JOB_AGE_SECONDS) {
                    log.debug("Job {} is assigned to agent {} but not running locally yet, which is normal while it is being claimed or finishing. Leaving it in place.",
                            distributedJob.id(), buildAgentShortName);
                    continue;
                }
                log.warn("Orphaned job in distributed map: job {} is assigned to agent {} but not running locally. Removing from distributed map.", distributedJob.id(),
                        buildAgentShortName);
                distributedDataAccessService.getDistributedProcessingJobs().remove(distributedJob.id());
                // Update agent info to reflect accurate counts
                buildAgentInformationService.updateLocalBuildAgentInformation(isPaused.get());
            }
        }
        catch (Exception e) {
            // Don't let detection failures affect other operations
            log.debug("Error during stale build detection: {}", e.getMessage());
        }
    }

    /**
     * Checks whether the node has at least one thread available for a new build job.
     * If so, process the next build job.
     */
    private void checkAvailabilityAndProcessNextBuild() {
        // Skip if not connected to cluster (happens when build agent starts before core nodes)
        if (!distributedDataAccessService.isConnectedToCluster()) {
            log.debug("Not connected to the distributed data provider yet. Skipping build job processing.");
            return;
        }

        if (distributedDataAccessService.noDataMemberInClusterAvailable() || distributedDataAccessService.getDistributedBuildJobQueue() == null) {
            log.warn("There are only lite member in the cluster. Not processing build jobs.");
            return;
        }
        // Check conditions before acquiring the lock to avoid unnecessary locking
        if (!nodeIsAvailable()) {
            // Add build agent information to map if not already present
            // Use buildAgentShortName as the key since that's what BuildAgentInformationService uses
            if (!distributedDataAccessService.getBuildAgentInformationMap().containsKey(buildAgentShortName)) {
                buildAgentInformationService.updateLocalBuildAgentInformation(isPaused.get());
            }

            log.debug("Node has no available threads currently");
            return;
        }

        if (distributedDataAccessService.getDistributedBuildJobQueue().isEmpty() || isPaused.get()) {
            return;
        }
        BuildJobQueueItem buildJob = null;
        availabilityAndDequeueLock.lock();
        try {
            // Recheck conditions after acquiring the lock to ensure they are still valid
            if (!nodeIsAvailable() || distributedDataAccessService.getDistributedBuildJobQueue().isEmpty() || isPaused.get()) {
                return;
            }

            buildJob = addToProcessingJobs();

            processBuild(buildJob);
        }
        catch (RejectedExecutionException e) {
            var buildExecutorService = buildAgentConfiguration.getBuildExecutor();
            // TODO: we should log this centrally and not on the local node
            log.error("Couldn't add build job to thread pool: {}\n Concurrent Build Jobs Count: {} Active tasks in pool: {}, Concurrent Build Jobs Size: {}", buildJob,
                    localProcessingJobs.get(), buildExecutorService.getActiveCount(), buildExecutorService.getMaximumPoolSize(), e);

            // Add the build job back to the queue
            if (buildJob != null) {
                distributedDataAccessService.getDistributedProcessingJobs().remove(buildJob.id());

                // At most try out the build job 5 times when they get rejected
                if (buildJob.retryCount() >= 5) {
                    // TODO: we should log this centrally and not on the local node
                    log.error("Build job was rejected 5 times. Not adding build job back to the queue: {}", buildJob);
                }
                else {
                    // NOTE: we increase the retry count here, because the build job was not processed successfully
                    // TODO: we should try to run this job on a different build agent to avoid getting the same error again
                    buildJob = new BuildJobQueueItem(buildJob, new BuildAgentDTO("", "", ""), buildJob.retryCount() + 1);
                    log.info("Adding build job {} back to the queue with retry count {}", buildJob, buildJob.retryCount());
                    distributedDataAccessService.getDistributedBuildJobQueue().add(buildJob);
                }
                localProcessingJobs.decrementAndGet();
            }

            buildAgentInformationService.updateLocalBuildAgentInformation(isPaused.get());
        }
        finally {
            availabilityAndDequeueLock.unlock();
        }
    }

    /**
     * Atomically dequeues a build job and registers it as a processing job on this node.
     * <p>
     * This method:
     * <ol>
     * <li>Polls the next job from the distributed queue</li>
     * <li>Assigns this build agent as the job's executor</li>
     * <li>Calculates the estimated completion time</li>
     * <li>Registers the job in the distributed processing jobs map</li>
     * <li>Increments the local processing counter</li>
     * </ol>
     * <p>
     * <b>Must be called while holding {@link #availabilityAndDequeueLock}</b> to prevent
     * race conditions with concurrent dequeue operations.
     *
     * @return the processing job item, or null if the queue was empty
     */
    private BuildJobQueueItem addToProcessingJobs() {
        BuildJobQueueItem buildJob = distributedDataAccessService.getDistributedBuildJobQueue().poll();
        if (buildJob != null) {
            String hazelcastMemberAddress = distributedDataAccessService.getLocalMemberAddress();

            long estimatedDuration = Math.max(0, buildJob.jobTimingInfo().estimatedDuration());
            ZonedDateTime estimatedCompletionDate = ZonedDateTime.now().plusSeconds(estimatedDuration);
            BuildJobQueueItem processingJob = new BuildJobQueueItem(buildJob, new BuildAgentDTO(buildAgentShortName, hazelcastMemberAddress, buildAgentDisplayName),
                    estimatedCompletionDate);

            distributedDataAccessService.getDistributedProcessingJobs().put(processingJob.id(), processingJob);
            localProcessingJobs.incrementAndGet();

            buildAgentInformationService.updateLocalBuildAgentInformation(isPaused.get());
            return processingJob;
        }
        return null;
    }

    /**
     * Removes build agent information and processing jobs for nodes that are no longer in the cluster.
     * <p>
     * This cleanup is necessary because when a node goes offline unexpectedly (e.g., crash),
     * its build agent information and any jobs it was processing remain in the distributed maps.
     * This method detects such stale entries by comparing the stored member address of each agent
     * with current cluster members.
     * <p>
     * Note: Build agents running as Hazelcast clients (not cluster members) are not cleaned up by this
     * method since their addresses are not in the cluster member list. Client-mode agents have addresses
     * on ephemeral ports (e.g., [127.0.0.1]:54321) which will never exactly match cluster member addresses
     * (which use the configured Hazelcast port like 5701). A separate mechanism (e.g., heartbeat-based
     * cleanup) should be used for client-mode agent cleanup if needed.
     */
    private void removeOfflineNodes() {
        Set<String> liveNodeIdentifiers = distributedDataAccessService.getClusterMemberAddresses();
        boolean agentsAppearInLiveList = distributedDataAccessService.buildAgentsAppearInClusterMemberList();
        var buildAgentMap = distributedDataAccessService.getBuildAgentInformationMap();

        log.debug("removeOfflineNodes: live node identifiers = {}, agents appear in live list = {}, build agent map keys = {}", liveNodeIdentifiers, agentsAppearInLiveList,
                buildAgentMap.keySet());

        // Iterate over entries to access both the key (short name) and the stored member address
        for (var entry : buildAgentMap.entrySet()) {
            String agentKey = entry.getKey();
            // Entries without agent details cannot be matched against the live node set, and other readers filter them
            // out too, so skip rather than dereference.
            if (entry.getValue() == null || entry.getValue().buildAgent() == null) {
                continue;
            }
            String storedMemberAddress = entry.getValue().buildAgent().memberAddress();

            if (OfflineBuildAgentDetector.isOffline(storedMemberAddress, liveNodeIdentifiers, agentsAppearInLiveList)) {
                log.info("removeOfflineNodes: REMOVING agent '{}' with address '{}' (node is no longer alive)", agentKey, storedMemberAddress);
                // Removing the agent entry is the whole cleanup: it raises the map removal event that
                // SharedQueueManagementService.handleOrphanedJobsForRemovedAgent listens for, and that handler takes each of
                // the node's processing jobs out of the map atomically and re-queues it, so exactly one core node retries it.
                // Deleting those jobs here as well would race that handler and, when it won, drop the job instead of
                // retrying it - the build would simply never come back.
                removeBuildAgentInformationForNode(agentKey, storedMemberAddress);
            }
        }
    }

    /**
     * Removes the build agent information entry for a specific node from the distributed map.
     *
     * @param agentKey      the map key (build agent short name) identifying the agent
     * @param memberAddress the Hazelcast member address of the offline node (for logging)
     */
    private void removeBuildAgentInformationForNode(String agentKey, String memberAddress) {
        log.debug("Cleaning up build agent information for offline node: {} (address: {})", agentKey, memberAddress);
        distributedDataAccessService.getDistributedBuildAgentInformation().remove(agentKey);
    }

    /**
     * Process a build job by submitting it to the local CI executor service.
     * On completion, check for next job.
     */
    private void processBuild(BuildJobQueueItem buildJob) {
        // The 'user' is not properly logged into Artemis, this leads to an issue when accessing custom repository methods.
        // Therefore, a mock auth object has to be created.
        SecurityUtils.setAuthorizationObject();

        if (buildJob == null) {
            return;
        }

        log.info("Processing build job: {}", buildJob);

        CompletableFuture<BuildResult> futureResult = buildJobManagementService.executeBuildJob(buildJob);
        futureResult.thenAccept(buildResult -> {
            try {
                log.debug("Build job completed: {}", buildJob);
                JobTimingInfo jobTimingInfo = new JobTimingInfo(buildJob.jobTimingInfo().submissionDate(), buildJob.jobTimingInfo().buildStartDate(), ZonedDateTime.now(),
                        buildJob.jobTimingInfo().estimatedCompletionDate(), buildJob.jobTimingInfo().estimatedDuration());

                // No clone token: the job is finished, so it leaves the processing list and the token stops being
                // accepted. This item travels on to the result queue and the finished build job records.
                BuildJobQueueItem finishedJob = new BuildJobQueueItem(buildJob.id(), buildJob.name(), buildJob.buildAgent(), buildJob.participationId(), buildJob.courseId(),
                        buildJob.exerciseId(), buildJob.retryCount(), buildJob.priority(), BuildStatus.SUCCESSFUL, buildJob.repositoryInfo(), jobTimingInfo, buildJob.buildConfig(),
                        null, null);

                List<BuildLogDTO> buildLogs = buildLogsMap.getAndTruncateBuildLogs(buildJob.id());
                buildLogsMap.removeBuildLogs(buildJob.id());

                ResultQueueItem resultQueueItem = new ResultQueueItem(buildResult, finishedJob, buildLogs, null);
                enqueueBuildResult(resultQueueItem);
                // This is the single point where localProcessingJobs is decremented for successful jobs.
                // Other code (e.g., stale job cleanup) must NOT decrement the counter directly.
                removeProcessingJob(buildJob);

                buildAgentInformationService.updateLocalBuildAgentInformationWithRecentJob(finishedJob, isPaused.get(), false, consecutiveBuildJobFailures.get());

                consecutiveBuildJobFailures.set(0);

                // process next build job if node is available
                checkAvailabilityAndProcessNextBuild();
            }
            catch (Exception e) {
                log.error("Error in build success handler for job {}: {}. Attempting to continue processing.", buildJob.id(), e.getMessage(), e);
                // Ensure we continue processing next builds even if cleanup/update fails
                try {
                    checkAvailabilityAndProcessNextBuild();
                }
                catch (Exception ignored) {
                    log.error("Failed to check for next build after error in success handler", ignored);
                }
            }
        });

        futureResult.exceptionally(ex -> {
            try {
                log.debug("Build job completed with exception: {}", buildJob, ex);

                ZonedDateTime completionDate = ZonedDateTime.now();

                BuildJobQueueItem finishedBuildJob;
                BuildStatus status;

                if (isCausedByTimeoutException(ex, buildJob.id())) {
                    status = BuildStatus.TIMEOUT;
                    log.info("Build job with id {} was timed out", buildJob.id());
                    consecutiveBuildJobFailures.incrementAndGet();
                }
                else if (isCausedByCancelledException(ex, buildJob.id())) {
                    status = BuildStatus.CANCELLED;
                    log.info("Build job with id {} was cancelled", buildJob.id());
                }
                else {
                    status = BuildStatus.FAILED;
                    if (DockerUtil.isDockerNotAvailable(ex)) {
                        log.warn("Docker is not available. Build job {} failed: {}", buildJob.id(), ex.getMessage());
                    }
                    else {
                        log.error("Error while processing build job: {}", buildJob, ex);
                    }
                    if (!isCausedByImagePullFailedException(ex)) {
                        consecutiveBuildJobFailures.incrementAndGet();
                    }
                }

                finishedBuildJob = new BuildJobQueueItem(buildJob, completionDate, status);

                List<BuildLogDTO> buildLogs = buildLogsMap.getAndTruncateBuildLogs(buildJob.id());
                buildLogsMap.removeBuildLogs(buildJob.id());

                BuildResult failedResult = new BuildResult(buildJob.buildConfig().branch(), buildJob.buildConfig().assignmentCommitHash(), buildJob.buildConfig().testCommitHash(),
                        buildLogs, false);

                ResultQueueItem resultQueueItem = new ResultQueueItem(failedResult, finishedBuildJob, buildLogs, ex);
                enqueueBuildResult(resultQueueItem);
                // This is the single point where localProcessingJobs is decremented for failed/cancelled jobs.
                // Other code (e.g., stale job cleanup) must NOT decrement the counter directly.
                removeProcessingJob(buildJob);

                buildAgentInformationService.updateLocalBuildAgentInformationWithRecentJob(finishedBuildJob, isPaused.get(), false, consecutiveBuildJobFailures.get());

                if (consecutiveBuildJobFailures.get() >= buildAgentConfiguration.getPauseAfterConsecutiveFailedJobs()) {
                    log.error("Build agent has failed to process build jobs {} times in a row. Pausing build agent.", consecutiveBuildJobFailures.get());
                    pauseBuildAgent(true);
                    return null;
                }

                checkAvailabilityAndProcessNextBuild();
            }
            catch (Exception e) {
                log.error("Error in build exception handler for job {}: {}. Attempting to continue processing.", buildJob.id(), e.getMessage(), e);
                // Ensure we continue processing next builds even if cleanup/update fails
                try {
                    checkAvailabilityAndProcessNextBuild();
                }
                catch (Exception ignored) {
                    log.error("Failed to check for next build after error in exception handler", ignored);
                }
            }
            return null;
        });
    }

    /**
     * Enqueue the build result to the distributed build result queue.
     * If the build agent is paused, the result will not be added to the queue.
     *
     * @param resultQueueItem the build result to enqueue
     */
    private void enqueueBuildResult(ResultQueueItem resultQueueItem) {
        // Log build duration for performance monitoring
        var finishedJob = resultQueueItem.buildJobQueueItem();
        var timingInfo = finishedJob.jobTimingInfo();
        if (timingInfo.buildStartDate() != null && timingInfo.buildCompletionDate() != null) {
            double durationSeconds = java.time.Duration.between(timingInfo.buildStartDate(), timingInfo.buildCompletionDate()).toMillis() / 1000.0;
            log.info("Build finished for participation {} in {} s (name: {})", finishedJob.participationId(), "%.1f".formatted(durationSeconds), finishedJob.name());
        }
        distributedDataAccessService.getDistributedBuildResultQueue().add(resultQueueItem);
    }

    /**
     * Removes a processing job from the distributed map and decrements the local job counter.
     * <p>
     * <b>Counter Management Contract:</b> This method is the single point of responsibility for
     * decrementing {@code localProcessingJobs} when a build job completes (successfully, with error,
     * or via cancellation). It is called from:
     * <ul>
     * <li>The {@code thenAccept} handler when a build completes successfully</li>
     * <li>The {@code exceptionally} handler when a build fails, times out, or is cancelled</li>
     * </ul>
     * <p>
     * <b>Important:</b> Other code paths (such as stale job cleanup) must NOT decrement
     * {@code localProcessingJobs} directly. Instead, they should trigger job cancellation via
     * {@link BuildJobManagementService#cancelBuildJob(String)}, which will eventually cause the
     * {@code exceptionally} handler to call this method.
     * <p>
     * The distributed map removal is idempotent - if the job was already removed by another code path,
     * the remove operation simply returns null.
     *
     * @param buildJob the build job to remove
     */
    private void removeProcessingJob(BuildJobQueueItem buildJob) {
        distributedDataAccessService.getDistributedProcessingJobs().remove(buildJob.id());
        localProcessingJobs.decrementAndGet();
    }

    /**
     * Pauses the local build agent and transitions it into a {@code PAUSED} state.
     * <p>
     * The method performs the following steps:
     * <ol>
     * <li>Serializes the state transition using {@link #agentStateTransitionLock} so that
     * pause and resume operations cannot interfere with each other.</li>
     * <li>Checks whether the agent is already paused and returns early if so
     * (the operation is idempotent).</li>
     * <li>Marks the agent as paused via {@link #isPaused}, removes listeners and scheduled
     * tasks that may enqueue new jobs, and updates the distributed
     * build-agent information so other components observe the {@code PAUSED} status.</li>
     * <li>Looks up all currently running build jobs and collects their associated
     * {@link java.util.concurrent.CompletableFuture}s.</li>
     * <li>After releasing the state-transition lock, waits for all running jobs to finish
     * for at most {@link #pauseGracePeriodSeconds} seconds. If they do not finish in time,
     * {@link #handleTimeoutAndCancelRunningJobs()} is invoked to enforce cancellation.</li>
     * <li>Finally, closes the local build-agent services
     * (e.g. executors, Docker client) via {@link #buildAgentConfiguration#closeBuildAgentServices()}.</li>
     * </ol>
     *
     * <h3>Concurrency and locking semantics</h3>
     * <ul>
     * <li>The {@code isPaused} flag is both read and written <strong>only while holding</strong>
     * {@link #agentStateTransitionLock}. This prevents time-of-check/time-of-use (TOCTOU)
     * races between pause and resume operations.</li>
     * <li>The method intentionally does <strong>not</strong> hold
     * {@link #agentStateTransitionLock} while waiting for running jobs to complete.
     * This avoids potential deadlocks where completion callbacks of those futures
     * might themselves try to acquire the same lock or update build-agent state.</li>
     * <li>The distributed build-agent information is updated immediately after setting
     * {@code isPaused = true}, so other nodes and services can already treat the agent
     * as paused while it is still finishing or cancelling in-flight jobs.</li>
     * </ul>
     *
     * @param dueToFailures {@code true} if the pause was triggered by repeated build failures
     *                          (e.g. to implement back-off behaviour), {@code false} if the pause
     *                          was initiated administratively or for maintenance.
     */
    private void pauseBuildAgent(boolean dueToFailures) {
        // Collect the running jobs and their futures outside the lock so we can wait on them without holding it.
        Set<String> runningBuildJobIds = Set.of();
        Set<String> awaitableBuildJobIds = Set.of();
        List<CompletableFuture<BuildResult>> runningFuturesWrapper = List.of();

        agentStateTransitionLock.lock();
        try {
            if (isPaused.get()) {
                log.info("Build agent is already paused");
                return;
            }
            log.info("Pausing build agent with address {}", distributedDataAccessService.getLocalMemberAddress());

            // Mark the agent as paused so all subsequent logic and status updates are consistent.
            isPaused.set(true);

            // Stop accepting / scheduling new work before we update the distributed state.
            // Note: We only remove the queue listener and scheduled task, NOT the pause/resume
            // topic listeners - the agent must still be able to receive resume commands.
            removeQueueListenerAndCancelScheduledTask();

            // Persist the paused state so other components in the system see the agent as PAUSED.
            buildAgentInformationService.updateLocalBuildAgentInformation(isPaused.get(), dueToFailures, consecutiveBuildJobFailures.get());

            log.info("Gracefully cancelling running build jobs");
            runningBuildJobIds = buildJobManagementService.getRunningBuildJobIds();
            if (runningBuildJobIds.isEmpty()) {
                log.info("No running build jobs to cancel");
            }
            else {
                // Keep the ids alongside the futures: a job that is still being submitted is registered as running
                // before its public future exists, and only those jobs may be cancelled once the wait has finished.
                Map<String, CompletableFuture<BuildResult>> awaitableJobs = new LinkedHashMap<>();
                for (String runningBuildJobId : runningBuildJobIds) {
                    CompletableFuture<BuildResult> wrapper = buildJobManagementService.getRunningBuildJobFutureWrapper(runningBuildJobId);
                    if (wrapper != null) {
                        awaitableJobs.put(runningBuildJobId, wrapper);
                    }
                }
                awaitableBuildJobIds = Set.copyOf(awaitableJobs.keySet());
                runningFuturesWrapper = List.copyOf(awaitableJobs.values());
            }
            // We intentionally do NOT wait for the futures while holding the lock.
        }
        finally {
            agentStateTransitionLock.unlock();
        }

        // Outside of the lock: wait for running jobs to finish up to the configured grace period.
        //
        // The decision is driven by the running job ids rather than by the futures collected for them: a job that is
        // in the middle of being submitted is registered as running before its awaitable future exists, so an empty
        // list of futures does not mean the node is idle. Skipping this block in that case used to lose the job -
        // it was neither awaited nor cancelled nor put back on the queue.
        if (!runningBuildJobIds.isEmpty()) {
            boolean everyRunningJobIsAwaitable = awaitableBuildJobIds.size() == runningBuildJobIds.size();
            CompletableFuture<Void> allFuturesWrapper = CompletableFuture.allOf(runningFuturesWrapper.toArray(new CompletableFuture[0]));

            // allOf completes once every future it was given has completed, so a job that failed still counts as
            // finished - it only makes the wait end exceptionally. What the wait can never cover is a job that was
            // still being submitted when the pause began and so had no future to await yet.
            boolean allAwaitedJobsFinished = false;
            try {
                allFuturesWrapper.get(pauseGracePeriodSeconds, TimeUnit.SECONDS);
                allAwaitedJobsFinished = true;
            }
            catch (ExecutionException e) {
                // A build that ended in failure is still a build that ended. Cancelling here would re-queue a job whose
                // result is on its way to being published, so this counts as finished like any other completion.
                allAwaitedJobsFinished = true;
                log.warn("A build job finished exceptionally during the pause grace period", e);
            }
            catch (TimeoutException e) {
                log.warn("Not all running build jobs finished within {} seconds, enforcing cancellation", pauseGracePeriodSeconds, e);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while waiting for running build jobs to finish", e);
            }

            if (allAwaitedJobsFinished && everyRunningJobIsAwaitable) {
                log.info("All running build jobs finished during grace period");
            }
            else if (allAwaitedJobsFinished) {
                // Every awaited job has finished, so only the ones that had no future to await can still be running.
                // Cancelling the awaited ones as well would risk re-queueing a job whose result is on its way to being
                // published: allOf completes with the futures it was given, while the stage that takes a finished job
                // out of the running maps runs separately and need not have run yet.
                Set<String> neverAwaited = new HashSet<>(runningBuildJobIds);
                neverAwaited.removeAll(awaitableBuildJobIds);
                log.warn("{} of {} running build jobs had no future to await, enforcing cancellation for those", neverAwaited.size(), runningBuildJobIds.size());
                cancelAndRequeueRunningBuildJobs(neverAwaited);
            }
            else {
                handleTimeoutAndCancelRunningJobs();
            }
        }

        // Close the services under the transition lock, and only while this pause is still the one in effect. The wait
        // above runs without the lock, so a resume can have completed in the meantime and reopened these services;
        // closing them afterwards would leave an agent that reports itself as active with no executor to run anything.
        agentStateTransitionLock.lock();
        try {
            if (!isPaused.get()) {
                log.info("Build agent was resumed while it was pausing, so its services stay open");
                return;
            }
            buildAgentConfiguration.closeBuildAgentServices();
        }
        finally {
            agentStateTransitionLock.unlock();
        }
    }

    private void handleTimeoutAndCancelRunningJobs() {
        log.info("Grace period exceeded. Cancelling running build jobs.");
        cancelAndRequeueRunningBuildJobs(buildJobManagementService.getRunningBuildJobIds());
    }

    /**
     * Cancels the given build jobs and puts them back on the distributed queue so another agent can pick them up.
     * <p>
     * The ids are intersected with the jobs that are still running, so a job that finished while the caller was making
     * up its mind is left alone rather than re-queued behind its own result. A job can also finish in the window
     * between that snapshot and the cancellation itself, so only the jobs that cancellation actually stopped are put
     * back on the queue. Does nothing if the agent was resumed in the meantime.
     *
     * @param buildJobIds the ids of the build jobs to cancel and re-queue
     */
    private void cancelAndRequeueRunningBuildJobs(Set<String> buildJobIds) {
        if (!isPaused.get()) {
            log.info("Build agent was resumed before the build jobs could be cancelled");
            return;
        }
        Set<String> jobsToCancel = new HashSet<>(buildJobManagementService.getRunningBuildJobIds());
        jobsToCancel.retainAll(buildJobIds);
        if (jobsToCancel.isEmpty()) {
            return;
        }
        Map<String, BuildJobQueueItem> processingJobs = distributedDataAccessService.getDistributedProcessingJobs().getAll(jobsToCancel);
        Set<String> cancelledJobIds = new HashSet<>();
        List<BuildJobQueueItem> jobsToRequeue = new ArrayList<>();
        for (String buildJobId : jobsToCancel) {
            if (!buildJobManagementService.cancelBuildJob(buildJobId)) {
                // Nothing was stopped, so the job finished on its own between the snapshot above and this call and its
                // result is already on its way. Re-queueing it here would run the same build a second time.
                log.debug("Build job {} finished before it could be cancelled, so it is not put back on the queue", buildJobId);
                continue;
            }
            cancelledJobIds.add(buildJobId);
            BuildJobQueueItem processingJob = processingJobs.get(buildJobId);
            if (processingJob != null) {
                jobsToRequeue.add(processingJob);
            }
        }
        if (jobsToRequeue.isEmpty()) {
            return;
        }
        distributedDataAccessService.getDistributedBuildJobQueue().addAll(jobsToRequeue);
        log.info("Cancelled running build jobs and added them back to the queue with Ids {}", cancelledJobIds);
        log.debug("Cancelled running build jobs: {}", jobsToRequeue);
    }

    /**
     * Resumes the local build agent from a {@code PAUSED} state and transitions it back into a
     * state where it can accept and execute new build jobs.
     * <p>
     * The method performs the following steps:
     * <ol>
     * <li>Serializes the state transition using {@link #agentStateTransitionLock} so that
     * pause and resume operations cannot interfere with each other.</li>
     * <li>Checks whether the agent is currently paused and returns early if it is already
     * running (the operation is idempotent).</li>
     * <li>Marks the agent as not paused via {@link #isPaused}, enables result processing,
     * opens the build-agent services, and resets the consecutive failure counter.</li>
     * <li>Cleans up any stale Docker containers from previous runs or aborted jobs.</li>
     * <li>Re-initializes the integration with the distributed build-job queue by
     * removing any existing listener/scheduled task and attaching a fresh listener and
     * scheduling the periodic availability check.</li>
     * <li>Updates the distributed build-agent information so other components observe the
     * agent as available again.</li>
     * <li>After releasing the state-transition lock, triggers an immediate availability
     * check to start processing queued build jobs as soon as possible.</li>
     * </ol>
     *
     * <h3>Concurrency and locking semantics</h3>
     * <ul>
     * <li>Both the check and the update of {@link #isPaused} are performed while holding
     * {@link #agentStateTransitionLock}. This mirrors {@code pauseBuildAgent(...)} and
     * avoids time-of-check/time-of-use (TOCTOU) races between pause and resume.</li>
     * <li>Listener and scheduler reconfiguration are also performed under the same lock to
     * guarantee that at most one listener and one scheduled task are active at any time,
     * even in the presence of concurrent pause/resume calls.</li>
     * <li>{@link #checkAvailabilityAndProcessNextBuild()} is invoked <strong>after</strong>
     * the lock is released to avoid re-entrancy or deadlocks if the availability check
     * itself interacts with state protected by {@link #agentStateTransitionLock} or
     * shared services.</li>
     * </ul>
     */
    private void resumeBuildAgent() {
        agentStateTransitionLock.lock();
        try {
            // Re-check paused state under the lock to avoid races with pause operations.
            if (!isPaused.get()) {
                log.info("Build agent is already running");
                return;
            }

            log.info("Resuming build agent with address {}", distributedDataAccessService.getLocalMemberAddress());

            // Mark the agent as running again and enable result processing.
            isPaused.set(false);

            // Re-open the underlying services (executors, Docker client, etc.) required to run jobs.
            buildAgentConfiguration.openBuildAgentServices();

            // Reset the consecutive failure counter so that previous failures do not penalize new runs.
            consecutiveBuildJobFailures.set(0);

            // Cleanup any stale Docker containers from previous runs or aborted jobs.
            buildAgentDockerService.cleanUpContainers();

            // To avoid multiple listeners and scheduled tasks, remove any existing ones first.
            // Note: We only remove the queue listener and scheduled task - the topic listeners
            // should remain intact as they were not removed during pause.
            removeQueueListenerAndCancelScheduledTask();

            log.info("Re-adding item listener to distributed build job queue for build agent with address {}", distributedDataAccessService.getLocalMemberAddress());

            // Attach a new listener to the distributed build job queue.
            listenerId = distributedDataAccessService.getDistributedBuildJobQueue().addItemListener(new QueuedBuildJobItemListener());

            // Restart the periodic availability check & job processing scheduler.
            scheduledFuture = taskScheduler.scheduleAtFixedRate(this::checkAvailabilityAndProcessNextBuild, BUILD_CHECK_AVAILABILITY_INTERVAL);

            // Persist the resumed state so other components see the agent as available again.
            buildAgentInformationService.updateLocalBuildAgentInformation(isPaused.get());
        }
        finally {
            agentStateTransitionLock.unlock();
        }

        // Outside of the lock: trigger an immediate availability check so queued jobs
        // do not have to wait for the next scheduled interval.
        checkAvailabilityAndProcessNextBuild();
    }

    /**
     * Returns whether at least one thread is available and the executor queue is empty.
     * <p>
     * Ensures we do not over-dequeue beyond configured pool capacity.
     * </p>
     */
    private boolean nodeIsAvailable() {
        var buildExecutorService = buildAgentConfiguration.getBuildExecutor();
        if (buildExecutorService == null) {
            log.warn("build node is not available yet because buildExecutorService is null!");
            return false;
        }
        log.debug("Currently processing jobs on this node: {}, active threads in Pool: {}, maximum pool size of thread executor : {}", localProcessingJobs.get(),
                buildExecutorService.getActiveCount(), buildExecutorService.getMaximumPoolSize());
        return localProcessingJobs.get() < buildExecutorService.getMaximumPoolSize() && buildExecutorService.getActiveCount() < buildExecutorService.getMaximumPoolSize()
                && buildExecutorService.getQueue().isEmpty();
    }

    /**
     * Check if a throwable is caused by local CI failing to pull the docker image
     *
     * @param throwable throwable to check
     * @return {@code true} if the throwable is caused by local CI failing to pull the docker image, {@code false} otherwise
     */
    private boolean isCausedByImagePullFailedException(Throwable throwable) {
        Throwable cause = throwable.getCause();
        if (!(cause instanceof ExecutionException)) {
            return false;
        }
        Throwable rootCause = cause.getCause();
        return rootCause instanceof LocalCIException && rootCause.getMessage() != null && rootCause.getMessage().contains("Could not pull Docker image");
    }

    /**
     * Check if a throwable is caused by a cancelled build job
     *
     * @param throwable  the throwable to check
     * @param buildJobId the id of the build job
     * @return {@code true} if the throwable is caused by a cancelled build job, {@code false} otherwise
     */
    private boolean isCausedByCancelledException(Throwable throwable, String buildJobId) {
        String cancelledMsg = "Build job with id " + buildJobId + " was cancelled.";
        return throwable.getCause() instanceof CancellationException && throwable.getMessage().equals(cancelledMsg);
    }

    /**
     * Check if a throwable is caused by a timeout
     *
     * @param throwable  the throwable to check
     * @param buildJobId the id of the build job
     * @return {@code true} if the throwable is caused by a timeout, {@code false} otherwise
     */
    private boolean isCausedByTimeoutException(Throwable throwable, String buildJobId) {
        String timeoutMsg = "Build job with id " + buildJobId + " was timed out";
        return throwable.getCause() instanceof TimeoutException || throwable.getMessage().equals(timeoutMsg);
    }

    /**
     * Lightweight listener that reacts to changes in the distributed build queue.
     *
     * <p>
     * <strong>Design</strong>:
     * <ul>
     * <li>Does not perform any heavy work on the Hazelcast event thread.</li>
     * <li>Simply triggers {@code checkAvailabilityAndProcessNextBuild()}, which
     * handles locking and capacity checks.</li>
     * <li>Logs compact, high-signal messages to avoid log spam at scale.</li>
     * <li>Defensive against Hazelcast lifecycle/availability issues.</li>
     * </ul>
     */
    private class QueuedBuildJobItemListener implements QueueItemListener<BuildJobQueueItem> {

        @Override
        public void itemAdded(BuildJobQueueItem item) {
            try {
                log.debug("CIBuildJobQueueItem added to queue: {}. Current queued items: {}", item, distributedDataAccessService.getQueuedJobsSize());
                checkAvailabilityAndProcessNextBuild();
            }
            catch (Exception e) {
                // Never let listener exceptions bubble up and destabilize the Hazelcast thread
                log.error("Error handling itemAdded event", e);
            }
        }

        @Override
        public void itemRemoved(BuildJobQueueItem item) {
            log.debug("CIBuildJobQueueItem removed from queue: {}", item);
        }
    }
}
