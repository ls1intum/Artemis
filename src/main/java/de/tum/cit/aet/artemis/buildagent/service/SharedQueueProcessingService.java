package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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

import jakarta.annotation.PreDestroy;

import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RMap;
import org.redisson.api.RPriorityQueue;
import org.redisson.api.RQueue;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.ListAddListener;
import org.redisson.api.listener.ListRemoveListener;
import org.redisson.client.RedisConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildLogDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.connectors.redis.RedisClientListResolver;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

/**
 * Includes functionality for processing build jobs from the shared build job queue.
 */
@Profile(PROFILE_BUILDAGENT)
@Service
public class SharedQueueProcessingService {

    private static final Logger log = LoggerFactory.getLogger(SharedQueueProcessingService.class);

    private final RedissonClient redissonClient;

    private final BuildAgentConfiguration buildAgentConfiguration;

    private final BuildJobManagementService buildJobManagementService;

    private final BuildLogsMap buildLogsMap;

    private final AtomicInteger localProcessingJobs = new AtomicInteger(0);

    private final BuildAgentSshKeyService buildAgentSSHKeyService;

    private final TaskScheduler taskScheduler;

    private RPriorityQueue<BuildJobQueueItem> buildJobQueue;

    private final BuildAgentDockerService buildAgentDockerService;

    private RQueue<ResultQueueItem> resultQueue;

    /**
     * Map of build jobs currently being processed across all nodes
     */
    private RMap<String, BuildJobQueueItem> processingJobs;

    private RMap<String, BuildAgentInformation> buildAgentInformation;

    /**
     * Lock for operations on single instance.
     */
    private final ReentrantLock instanceLock = new ReentrantLock();

    private int listenerIdAdd;

    private int listenerIdRemove;

    /**
     * Lock for pausing and resuming the build agent.
     */
    private final ReentrantLock pauseResumeLock = new ReentrantLock();

    /**
     * Scheduled future for checking availability and processing next build job.
     */
    private ScheduledFuture<?> scheduledFuture;

    /**
     * Flag to indicate whether the build agent is paused.
     */
    private final AtomicBoolean isPaused = new AtomicBoolean(false);

    /**
     * Flag to indicate whether the build agent should process build results. This is necessary to differentiate between when the build agent is paused and grace period is not over
     * yet.
     */
    private final AtomicBoolean processResults = new AtomicBoolean(true);

    @Value("${artemis.continuous-integration.pause-grace-period-seconds:60}")
    private int pauseGracePeriodSeconds;

    @Value("${artemis.continuous-integration.build-agent.short-name}")
    private String buildAgentShortName;

    @Value("${artemis.continuous-integration.build-agent.display-name:}")
    private String buildAgentDisplayName;

    @Value("${spring.data.redis.client-name}")
    private String redisClientName;     // this is used as build agent name

    private final RedisClientListResolver redisClientListResolver;

    public SharedQueueProcessingService(RedissonClient redissonClient, BuildAgentConfiguration buildAgentConfiguration, BuildJobManagementService buildJobManagementService,
            BuildLogsMap buildLogsMap, BuildAgentSshKeyService buildAgentSSHKeyService, TaskScheduler taskScheduler, RedisClientListResolver redisClientListResolver,
            BuildAgentDockerService buildAgentDockerService) {
        this.redissonClient = redissonClient;
        this.buildAgentConfiguration = buildAgentConfiguration;
        this.buildJobManagementService = buildJobManagementService;
        this.buildLogsMap = buildLogsMap;
        this.buildAgentSSHKeyService = buildAgentSSHKeyService;
        this.taskScheduler = taskScheduler;
        this.redisClientListResolver = redisClientListResolver;
        this.buildAgentDockerService = buildAgentDockerService;
    }

    /**
     * Initialize relevant data for cluster communication.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        if (!buildAgentShortName.matches("^[a-z0-9-]+$")) {
            String errorMessage = "Build agent short name must not be empty and only contain lowercase letters, numbers and hyphens."
                    + " Build agent short name should be changed in the application properties under 'artemis.continuous-integration.build-agent.short-name'.";
            log.error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        if (StringUtils.isBlank(buildAgentDisplayName)) {
            buildAgentDisplayName = buildAgentShortName;
        }

        this.buildAgentInformation = this.redissonClient.getMap("buildAgentInformation");
        this.processingJobs = this.redissonClient.getMap("processingJobs");
        this.buildJobQueue = this.redissonClient.getPriorityQueue("buildJobQueue");
        this.resultQueue = this.redissonClient.getQueue("buildResultQueue");
        this.listenerIdAdd = this.buildJobQueue.addListener((ListAddListener) name -> {
            log.info("CIBuildJobQueueItem added to queue: {}", name);
            checkAvailabilityAndProcessNextBuild();
        });
        this.listenerIdRemove = this.buildJobQueue.addListener((ListRemoveListener) name -> log.info("CIBuildJobQueueItem removed from queue: {}", name));

        /*
         * Check every 10 seconds whether the node has at least one thread available for a new build job.
         * If so, process the next build job.
         * This is a backup mechanism in case the build queue is not empty, no new build jobs are entering the queue and the
         * node otherwise stopped checking for build jobs in the queue.
         */
        scheduledFuture = taskScheduler.scheduleAtFixedRate(this::checkAvailabilityAndProcessNextBuild, Duration.ofSeconds(10));

        RTopic pauseBuildAgentTopic = redissonClient.getTopic("pauseBuildAgentTopic");
        pauseBuildAgentTopic.addListener(String.class, (channel, name) -> {
            if (buildAgentShortName.equals(name)) {
                pauseBuildAgent();
            }
        });

        RTopic resumeBuildAgentTopic = redissonClient.getTopic("resumeBuildAgentTopic");
        resumeBuildAgentTopic.addListener(String.class, (channel, name) -> {
            if (buildAgentShortName.equals(name)) {
                resumeBuildAgent();
            }
        });
    }

    @PreDestroy
    public void removeListenerAndCancelScheduledFuture() {
        removeListener();
        cancelCheckAvailabilityAndProcessNextBuildScheduledFuture();
    }

    private void removeListener() {
        try {
            // Remove the build agent from the map to avoid issues
            this.buildAgentInformation.remove(getBuildAgentName());
            this.buildJobQueue.removeListener(this.listenerIdAdd);
            this.buildJobQueue.removeListener(this.listenerIdRemove);
        }
        catch (RedisConnectionException e) {
            log.error("Failed to remove listener from SharedQueueProcessingService due to Redis connection exception.");
        }
    }

    private void cancelCheckAvailabilityAndProcessNextBuildScheduledFuture() {
        if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
            scheduledFuture.cancel(false);
        }
    }

    /**
     * Wait 1 minute after startup and then every 1 minute update the build agent information of the local cluster member.
     * This is necessary because the build agent information is not updated automatically when a node joins the cluster.
     */
    @Scheduled(initialDelay = 600000, fixedRate = 60000) // 1 minute initial delay, 1 minute fixed rate
    public void updateBuildAgentInformation() {
        // Remove build agent information of offline nodes
        removeOfflineNodes();

        // Add build agent information of local cluster member to map if not already present
        if (!buildAgentInformation.containsKey(getBuildAgentName())) {
            updateLocalBuildAgentInformation();
        }
    }

    private String getBuildAgentName() {
        return redisClientName;
    }

    /**
     * Check every 10 seconds whether the node has at least one thread available for a new build job.
     * If so, process the next build job.
     * This is a backup mechanism in case the build queue is not empty, no new build jobs are entering the queue and the
     * node otherwise stopped checking for build jobs in the queue.
     */
    @Scheduled(fixedRate = 10000)
    public void checkForBuildJobs() {
        checkAvailabilityAndProcessNextBuild();
    }

    /**
     * Checks whether the node has at least one thread available for a new build job.
     * If so, process the next build job.
     */
    private void checkAvailabilityAndProcessNextBuild() {
        // Check conditions before acquiring the lock to avoid unnecessary locking
        if (!nodeIsAvailable()) {
            // Add build agent information of local member to map if not already present
            if (!buildAgentInformation.containsKey(getBuildAgentName())) {
                updateLocalBuildAgentInformation();
            }

            log.debug("Node has no available threads currently");
            return;
        }

        // todo remove, probably wrong order executed on setup?
        if (buildJobQueue == null) {
            log.info("BuildJobQueue is null. Skipping build job processing.");
            return;
        }

        if (buildJobQueue.isEmpty() || isPaused.get()) {
            return;
        }
        BuildJobQueueItem buildJob = null;
        instanceLock.lock();
        try {
            // Recheck conditions after acquiring the lock to ensure they are still valid
            if (!nodeIsAvailable() || buildJobQueue.isEmpty() || isPaused.get()) {
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
                processingJobs.remove(buildJob.id());

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
                    buildJobQueue.add(buildJob);
                }
                localProcessingJobs.decrementAndGet();
            }

            updateLocalBuildAgentInformation();
        }
        finally {
            instanceLock.unlock();
        }
    }

    private BuildJobQueueItem addToProcessingJobs() {
        BuildJobQueueItem buildJob = buildJobQueue.poll();
        if (buildJob != null) {
            String memberAddress = getBuildAgentName();

            long estimatedDuration = Math.max(0, buildJob.jobTimingInfo().estimatedDuration());
            ZonedDateTime estimatedCompletionDate = ZonedDateTime.now().plusSeconds(estimatedDuration);
            BuildJobQueueItem processingJob = new BuildJobQueueItem(buildJob, new BuildAgentDTO(buildAgentShortName, memberAddress, buildAgentDisplayName),
                    estimatedCompletionDate);

            processingJobs.put(processingJob.id(), processingJob);
            localProcessingJobs.incrementAndGet();

            updateLocalBuildAgentInformation();
            return processingJob;
        }
        return null;
    }

    private void updateLocalBuildAgentInformation() {
        updateLocalBuildAgentInformationWithRecentJob(null);
    }

    private void updateLocalBuildAgentInformationWithRecentJob(BuildJobQueueItem recentBuildJob) {
        // Add/update
        BuildAgentInformation info = getUpdatedLocalBuildAgentInformation(recentBuildJob);
        try {
            buildAgentInformation.put(info.buildAgent().memberAddress(), info);
        }
        catch (Exception e) {
            log.error("Error while updating build agent information for agent {} with address {}", info.buildAgent().name(), info.buildAgent().memberAddress(), e);
        }
    }

    private BuildAgentInformation getUpdatedLocalBuildAgentInformation(BuildJobQueueItem recentBuildJob) {
        String memberAddress = getBuildAgentName();
        List<BuildJobQueueItem> processingJobsOfMember = getProcessingJobsOfNode(memberAddress);
        int numberOfCurrentBuildJobs = processingJobsOfMember.size();
        int maxNumberOfConcurrentBuilds = buildAgentConfiguration.getBuildExecutor() != null ? buildAgentConfiguration.getBuildExecutor().getMaximumPoolSize()
                : buildAgentConfiguration.getThreadPoolSize();
        boolean hasJobs = numberOfCurrentBuildJobs > 0;
        BuildAgentInformation.BuildAgentStatus status = isPaused.get() ? BuildAgentInformation.BuildAgentStatus.PAUSED
                : hasJobs ? BuildAgentInformation.BuildAgentStatus.ACTIVE : BuildAgentInformation.BuildAgentStatus.IDLE;
        BuildAgentInformation agent = buildAgentInformation.get(memberAddress);
        List<BuildJobQueueItem> recentBuildJobs;
        if (agent != null) {
            recentBuildJobs = agent.recentBuildJobs();
        }
        else {
            recentBuildJobs = new ArrayList<>();
        }
        // TODO: Make this number configurable
        if (recentBuildJob != null) {
            if (recentBuildJobs.size() >= 20) {
                recentBuildJobs.removeFirst();
            }
            recentBuildJobs.add(recentBuildJob);
        }

        String publicSshKey = buildAgentSSHKeyService.getPublicKeyAsString();
        log.info("Adding public key {} to buildAgent info of{}", publicSshKey, buildAgentShortName);

        BuildAgentDTO agentInfo = new BuildAgentDTO(buildAgentShortName, memberAddress, buildAgentDisplayName);

        return new BuildAgentInformation(agentInfo, maxNumberOfConcurrentBuilds, numberOfCurrentBuildJobs, processingJobsOfMember, status, recentBuildJobs, publicSshKey);
    }

    private List<BuildJobQueueItem> getProcessingJobsOfNode(String memberAddress) {
        // NOTE: we should not use streams with IMap, because it can be unstable, when many items are added at the same time and there is a slow network condition
        List<BuildJobQueueItem> processingJobsList = new ArrayList<>(processingJobs.values());
        return processingJobsList.stream().filter(job -> Objects.equals(job.buildAgent().memberAddress(), memberAddress)).toList();
    }

    private void removeOfflineNodes() {
        Set<String> uniqueClients = redisClientListResolver.getUniqueClients();

        log.debug("Redis client list based on names: {}", uniqueClients);

        // Compare the client names with the build agent information
        for (String key : new HashSet<>(buildAgentInformation.keySet())) {
            if (!uniqueClients.contains(key)) {
                buildAgentInformation.remove(key);
                log.info("Removed offline build agent: {}", key);
            }
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
            if (processResults.get()) {
                log.info("Adding build result to result queue: {}", resultQueueItem);
                resultQueue.add(resultQueueItem);
            }
            else {
                log.info("Build agent is paused. Not adding build result to result queue for build job: {}", buildJob);
            }

            // after processing a build job, remove it from the processing jobs
            processingJobs.remove(buildJob.id());
            localProcessingJobs.decrementAndGet();
            updateLocalBuildAgentInformationWithRecentJob(finishedJob);

            // process next build job if node is available
            checkAvailabilityAndProcessNextBuild();
        });

        futureResult.exceptionally(ex -> {
            log.debug("Build job completed with exception: {}", buildJob, ex);

            ZonedDateTime completionDate = ZonedDateTime.now();

            BuildJobQueueItem job;
            BuildStatus status;

            if (!(ex.getCause() instanceof CancellationException) || !ex.getMessage().equals("Build job with id " + buildJob.id() + " was cancelled.")) {
                status = BuildStatus.FAILED;
                log.error("Error while processing build job: {}", buildJob, ex);
            }
            else {
                status = BuildStatus.CANCELLED;
            }

            job = new BuildJobQueueItem(buildJob, completionDate, status);

            List<BuildLogDTO> buildLogs = buildLogsMap.getAndTruncateBuildLogs(buildJob.id());
            buildLogsMap.removeBuildLogs(buildJob.id());

            BuildResult failedResult = new BuildResult(buildJob.buildConfig().branch(), buildJob.buildConfig().assignmentCommitHash(), buildJob.buildConfig().testCommitHash(),
                    buildLogs, false);

            ResultQueueItem resultQueueItem = new ResultQueueItem(failedResult, job, buildLogs, ex);
            if (processResults.get()) {
                log.info("Adding build result to result queue: {}", resultQueueItem);
                resultQueue.add(resultQueueItem);
            }
            else {
                log.info("Build agent is paused. Not adding build result to result queue for build job: {}", buildJob);
            }

            processingJobs.remove(buildJob.id());
            localProcessingJobs.decrementAndGet();
            updateLocalBuildAgentInformationWithRecentJob(job);

            checkAvailabilityAndProcessNextBuild();
            return null;
        });
    }

    private void pauseBuildAgent() {
        if (isPaused.get()) {
            log.info("Build agent is already paused");
            return;
        }

        pauseResumeLock.lock();
        try {
            log.info("Pausing build agent {}", getBuildAgentName());

            isPaused.set(true);
            removeListenerAndCancelScheduledFuture();
            updateLocalBuildAgentInformation();

            log.info("Gracefully cancelling running build jobs");
            Set<String> runningBuildJobIds = buildJobManagementService.getRunningBuildJobIds();
            if (runningBuildJobIds.isEmpty()) {
                log.info("No running build jobs to cancel");
            }
            else {
                List<CompletableFuture<BuildResult>> runningFuturesWrapper = runningBuildJobIds.stream().map(buildJobManagementService::getRunningBuildJobFutureWrapper)
                        .filter(Objects::nonNull).toList();

                if (!runningFuturesWrapper.isEmpty()) {
                    CompletableFuture<Void> allFuturesWrapper = CompletableFuture.allOf(runningFuturesWrapper.toArray(new CompletableFuture[0]));

                    try {
                        allFuturesWrapper.get(pauseGracePeriodSeconds, TimeUnit.SECONDS);
                        log.info("All running build jobs finished during grace period");
                    }
                    catch (TimeoutException e) {
                        handleTimeoutAndCancelRunningJobs();
                    }
                    catch (InterruptedException | ExecutionException e) {
                        log.error("Error while waiting for running build jobs to finish", e);
                    }
                }
            }
            // Close the build executor and docker client
            buildAgentConfiguration.closeBuildAgentServices();
        }
        finally {
            pauseResumeLock.unlock();
        }
    }

    private void handleTimeoutAndCancelRunningJobs() {
        if (!isPaused.get()) {
            log.info("Build agent was resumed before the build jobs could be cancelled");
            return;
        }
        log.info("Grace period exceeded. Cancelling running build jobs.");

        processResults.set(false);
        Set<String> runningBuildJobIdsAfterGracePeriod = buildJobManagementService.getRunningBuildJobIds();
        List<BuildJobQueueItem> runningBuildJobsAfterGracePeriod = processingJobs.getAll(runningBuildJobIdsAfterGracePeriod).values().stream().toList();
        runningBuildJobIdsAfterGracePeriod.forEach(buildJobManagementService::cancelBuildJob);
        buildJobQueue.addAll(runningBuildJobsAfterGracePeriod);
        log.info("Cancelled running build jobs and added them back to the queue with Ids {}", runningBuildJobIdsAfterGracePeriod);
        log.debug("Cancelled running build jobs: {}", runningBuildJobsAfterGracePeriod);
    }

    private void resumeBuildAgent() {
        if (!isPaused.get()) {
            log.info("Build agent is already running");
            return;
        }

        pauseResumeLock.lock();
        try {
            log.info("Resuming build agent {}", getBuildAgentName());
            isPaused.set(false);
            processResults.set(true);
            buildAgentConfiguration.openBuildAgentServices();

            // Cleanup docker containers
            buildAgentDockerService.cleanUpContainers();

            // We remove the listener and scheduledTask first to avoid having multiple listeners and scheduled tasks running
            removeListenerAndCancelScheduledFuture();
            listenerIdAdd = this.buildJobQueue.addListener((ListAddListener) name -> {
                log.debug("CIBuildJobQueueItem added to queue: {}", name);
                log.debug("Current queued items: {}", name);
                checkAvailabilityAndProcessNextBuild();
            });
            scheduledFuture = taskScheduler.scheduleAtFixedRate(this::checkAvailabilityAndProcessNextBuild, Duration.ofSeconds(10));

            updateLocalBuildAgentInformation();
        }
        finally {
            pauseResumeLock.unlock();
        }

        checkAvailabilityAndProcessNextBuild();
    }

    /**
     * Checks whether the node has at least one thread available for a new build job.
     */
    private boolean nodeIsAvailable() {
        var buildExecutorService = buildAgentConfiguration.getBuildExecutor();
        log.debug("Currently processing jobs on this node: {}, active threads in Pool: {}, maximum pool size of thread executor : {}", localProcessingJobs.get(),
                buildExecutorService.getActiveCount(), buildExecutorService.getMaximumPoolSize());
        return localProcessingJobs.get() < buildExecutorService.getMaximumPoolSize() && buildExecutorService.getActiveCount() < buildExecutorService.getMaximumPoolSize()
                && buildExecutorService.getQueue().isEmpty();
    }
}
