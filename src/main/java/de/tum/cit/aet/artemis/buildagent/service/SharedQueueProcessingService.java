package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
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
import de.tum.cit.aet.artemis.core.exception.LocalCIException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.service.localci.DistributedDataAccessService;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.listener.QueueItemListener;

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

    private final BuildAgentConfiguration buildAgentConfiguration;

    private final BuildJobManagementService buildJobManagementService;

    private final BuildLogsMap buildLogsMap;

    private final AtomicInteger consecutiveBuildJobFailures = new AtomicInteger(0);

    private final AtomicInteger localProcessingJobs = new AtomicInteger(0);

    private final BuildAgentInformationService buildAgentInformationService;

    private final TaskScheduler taskScheduler;

    private final BuildAgentDockerService buildAgentDockerService;

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

    /** @return true if the build agent is paused, false otherwise */
    public boolean isPaused() {
        return isPaused.get();
    }

    /** Resets the pause state (for tests only). */
    public void resetPauseState() {
        isPaused.set(false);
    }

    public SharedQueueProcessingService(BuildAgentConfiguration buildAgentConfiguration, BuildJobManagementService buildJobManagementService, BuildLogsMap buildLogsMap,
            TaskScheduler taskScheduler, BuildAgentDockerService buildAgentDockerService, BuildAgentInformationService buildAgentInformationService,
            DistributedDataAccessService distributedDataAccessService) {
        this.buildAgentConfiguration = buildAgentConfiguration;
        this.buildJobManagementService = buildJobManagementService;
        this.buildLogsMap = buildLogsMap;
        this.buildAgentInformationService = buildAgentInformationService;
        this.taskScheduler = taskScheduler;
        this.buildAgentDockerService = buildAgentDockerService;
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
                log.info("Hazelcast client reconnected to cluster. Re-initializing SharedQueueProcessingService listeners.");
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
            log.info("Hazelcast client not yet connected to cluster. Scheduling periodic initialization retries every {} seconds.", CLUSTER_CONNECTION_RETRY_INTERVAL.toSeconds());

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
            log.debug("Cannot initialize SharedQueueProcessingService: not connected to Hazelcast cluster yet");
            return false;
        }

        try {
            // Remove listener if already present (for idempotency)
            if (this.listenerId != null) {
                distributedDataAccessService.getDistributedBuildJobQueue().removeListener(this.listenerId);
            }
            // Cancel existing scheduled task if present (for idempotency on partial failure retry)
            cancelCheckAvailabilityAndProcessNextBuildScheduledFuture();

            log.info("Adding item listener to Hazelcast distributed build job queue for build agent with address {}", distributedDataAccessService.getLocalMemberAddress());
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
            log.debug("Not connected to Hazelcast cluster yet. Skipping build agent information update.");
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
     * Checks whether the node has at least one thread available for a new build job.
     * If so, process the next build job.
     */
    private void checkAvailabilityAndProcessNextBuild() {
        // Skip if not connected to cluster (happens when build agent starts before core nodes)
        if (!distributedDataAccessService.isConnectedToCluster()) {
            log.debug("Not connected to Hazelcast cluster yet. Skipping build job processing.");
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
        Set<String> memberAddresses = distributedDataAccessService.getClusterMemberAddresses();
        var buildAgentMap = distributedDataAccessService.getBuildAgentInformationMap();

        log.debug("removeOfflineNodes: cluster member addresses = {}, build agent map keys = {}", memberAddresses, buildAgentMap.keySet());

        // Iterate over entries to access both the key (short name) and the stored member address
        for (var entry : buildAgentMap.entrySet()) {
            String agentKey = entry.getKey();
            String storedMemberAddress = entry.getValue().buildAgent().memberAddress();
            boolean isClusterMember = isClusterMemberAddress(storedMemberAddress, memberAddresses);
            boolean isInMemberSet = memberAddresses.contains(storedMemberAddress);

            log.debug("removeOfflineNodes: checking agent '{}' with address '{}': isClusterMemberAddress={}, isInMemberSet={}", agentKey, storedMemberAddress, isClusterMember,
                    isInMemberSet);

            // Only clean up agents whose stored address matches the exact format of current cluster members
            // AND is not in the current cluster member set (i.e., the member went offline).
            // Client-mode agents have ephemeral port addresses that won't match cluster member addresses,
            // so they are safely ignored by this cleanup logic.
            if (isClusterMember && !isInMemberSet) {
                log.info("removeOfflineNodes: REMOVING agent '{}' with address '{}' (was cluster member but is now offline)", agentKey, storedMemberAddress);
                removeBuildAgentInformationForNode(agentKey, storedMemberAddress);
                removeProcessingJobsForNode(storedMemberAddress);
            }
        }
    }

    /**
     * Checks if the given address appears to be a cluster member address.
     * <p>
     * For Hazelcast: Cluster members use configured ports (typically 5701, 5702, etc.), while clients
     * use ephemeral ports assigned by the OS. We check by comparing ports.
     * <p>
     * For Redisson: Addresses are simple client names (e.g., "artemis-node") without ports.
     * We check if the address is directly contained in the member addresses set.
     *
     * @param address         the address to check
     * @param memberAddresses the current set of cluster member addresses
     * @return true if the address appears to be a cluster member address
     */
    private boolean isClusterMemberAddress(String address, Set<String> memberAddresses) {
        if (address == null) {
            return false;
        }

        // First, check if the address is directly in the member addresses set (Redisson-style plain names)
        if (memberAddresses.contains(address)) {
            return true;
        }

        // For Hazelcast-style addresses with [host]:port format
        if (!address.contains("]:")) {
            return false;
        }
        // Extract port from the address (format: [host]:port)
        String addressPort = extractPort(address);
        if (addressPort == null) {
            return false;
        }
        // Check if any cluster member uses the same port - this indicates it's a cluster member address
        // Clients use random ephemeral ports, so they won't match cluster member ports
        return memberAddresses.stream().map(this::extractPort).filter(Objects::nonNull).anyMatch(addressPort::equals);
    }

    /**
     * Extracts the port from an address in [host]:port format.
     *
     * @param address the address string
     * @return the port string, or null if extraction fails
     */
    private String extractPort(String address) {
        if (address == null) {
            return null;
        }
        int lastColon = address.lastIndexOf(':');
        if (lastColon >= 0 && lastColon < address.length() - 1) {
            return address.substring(lastColon + 1);
        }
        return null;
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
     * Removes all processing jobs that were assigned to an offline node.
     * <p>
     * These jobs were being processed when the node went offline and need to be cleaned up.
     * Note: The jobs are not re-queued here as they may have been partially processed.
     *
     * @param memberAddress the Hazelcast member address of the offline node
     */
    private void removeProcessingJobsForNode(String memberAddress) {
        List<String> jobsToRemove = distributedDataAccessService.getProcessingJobIdsForAgent(memberAddress);
        log.debug("Removing {} processing jobs for offline node: {}", jobsToRemove.size(), memberAddress);
        for (String jobId : jobsToRemove) {
            distributedDataAccessService.getDistributedProcessingJobs().remove(jobId);
        }
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

            log.debug("Build job completed: {}", buildJob);
            JobTimingInfo jobTimingInfo = new JobTimingInfo(buildJob.jobTimingInfo().submissionDate(), buildJob.jobTimingInfo().buildStartDate(), ZonedDateTime.now(),
                    buildJob.jobTimingInfo().estimatedCompletionDate(), buildJob.jobTimingInfo().estimatedDuration());

            BuildJobQueueItem finishedJob = new BuildJobQueueItem(buildJob.id(), buildJob.name(), buildJob.buildAgent(), buildJob.participationId(), buildJob.courseId(),
                    buildJob.exerciseId(), buildJob.retryCount(), buildJob.priority(), BuildStatus.SUCCESSFUL, buildJob.repositoryInfo(), jobTimingInfo, buildJob.buildConfig(),
                    null);

            List<BuildLogDTO> buildLogs = buildLogsMap.getAndTruncateBuildLogs(buildJob.id());
            buildLogsMap.removeBuildLogs(buildJob.id());

            ResultQueueItem resultQueueItem = new ResultQueueItem(buildResult, finishedJob, buildLogs, null);
            enqueueBuildResult(resultQueueItem);
            removeProcessingJob(buildJob);

            buildAgentInformationService.updateLocalBuildAgentInformationWithRecentJob(finishedJob, isPaused.get(), false, consecutiveBuildJobFailures.get());

            consecutiveBuildJobFailures.set(0);

            // process next build job if node is available
            checkAvailabilityAndProcessNextBuild();
        });

        futureResult.exceptionally(ex -> {
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
                log.error("Error while processing build job: {}", buildJob, ex);
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
            removeProcessingJob(buildJob);

            buildAgentInformationService.updateLocalBuildAgentInformationWithRecentJob(finishedBuildJob, isPaused.get(), false, consecutiveBuildJobFailures.get());

            if (consecutiveBuildJobFailures.get() >= buildAgentConfiguration.getPauseAfterConsecutiveFailedJobs()) {
                log.error("Build agent has failed to process build jobs {} times in a row. Pausing build agent.", consecutiveBuildJobFailures.get());
                pauseBuildAgent(true);
                return null;
            }

            checkAvailabilityAndProcessNextBuild();
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
            log.info("Build finished for participation {} in {} s (name: {})", finishedJob.participationId(), String.format("%.1f", durationSeconds), finishedJob.name());
        }
        distributedDataAccessService.getDistributedBuildResultQueue().add(resultQueueItem);
    }

    /**
     * Remove the processing job from the distributed processing jobs map and decrement the local processing jobs counter.
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
        // Collect running job futures outside the lock so we can wait on them without holding it.
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
            Set<String> runningBuildJobIds = buildJobManagementService.getRunningBuildJobIds();
            if (runningBuildJobIds.isEmpty()) {
                log.info("No running build jobs to cancel");
            }
            else {
                runningFuturesWrapper = runningBuildJobIds.stream().map(buildJobManagementService::getRunningBuildJobFutureWrapper).filter(Objects::nonNull).toList();
            }
            // We intentionally do NOT wait for the futures while holding the lock.
        }
        finally {
            agentStateTransitionLock.unlock();
        }

        // Outside of the lock: wait for running jobs to finish up to the configured grace period.
        if (!runningFuturesWrapper.isEmpty()) {
            CompletableFuture<Void> allFuturesWrapper = CompletableFuture.allOf(runningFuturesWrapper.toArray(new CompletableFuture[0]));

            try {
                allFuturesWrapper.get(pauseGracePeriodSeconds, TimeUnit.SECONDS);
                log.info("All running build jobs finished during grace period");
            }
            catch (TimeoutException e) {
                log.warn("Not all running build jobs finished within {} seconds, enforcing cancellation", pauseGracePeriodSeconds, e);
                handleTimeoutAndCancelRunningJobs();
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while waiting for running build jobs to finish", e);
            }
            catch (ExecutionException e) {
                log.error("Error while waiting for running build jobs to finish", e);
            }
        }

        // After handling all running jobs, close the underlying services of the build agent.
        buildAgentConfiguration.closeBuildAgentServices();
    }

    private void handleTimeoutAndCancelRunningJobs() {
        if (!isPaused.get()) {
            log.info("Build agent was resumed before the build jobs could be cancelled");
            return;
        }
        log.info("Grace period exceeded. Cancelling running build jobs.");

        Set<String> runningBuildJobIdsAfterGracePeriod = buildJobManagementService.getRunningBuildJobIds();
        List<BuildJobQueueItem> runningBuildJobsAfterGracePeriod = distributedDataAccessService.getDistributedProcessingJobs().getAll(runningBuildJobIdsAfterGracePeriod).values()
                .stream().toList();
        runningBuildJobIdsAfterGracePeriod.forEach(buildJobManagementService::cancelBuildJob);
        distributedDataAccessService.getDistributedBuildJobQueue().addAll(runningBuildJobsAfterGracePeriod);
        log.info("Cancelled running build jobs and added them back to the queue with Ids {}", runningBuildJobIdsAfterGracePeriod);
        log.debug("Cancelled running build jobs: {}", runningBuildJobsAfterGracePeriod);
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
