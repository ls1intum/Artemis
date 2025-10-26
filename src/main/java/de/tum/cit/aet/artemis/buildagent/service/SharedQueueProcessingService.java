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
 * Includes functionality for processing build jobs from the shared build job queue.
 */
@Profile(PROFILE_BUILDAGENT)
@Lazy(false)
@Service
public class SharedQueueProcessingService {

    private static final Logger log = LoggerFactory.getLogger(SharedQueueProcessingService.class);

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
     * Lock for operations on single instance.
     */
    private final ReentrantLock instanceLock = new ReentrantLock();

    /**
     * Lock for pausing and resuming the build agent.
     */
    private final ReentrantLock pauseResumeLock = new ReentrantLock();

    private UUID listenerId;

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
     * Initialize relevant data from hazelcast
     * EventListener cannot be used here, as the bean is lazy
     * <a href="https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events-annotation">Spring Docs</a>
     */
    @PostConstruct
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

        // Remove listener if already present
        if (this.listenerId != null) {
            distributedDataAccessService.getDistributedBuildJobQueue().removeListener(this.listenerId);
        }
        log.info("Adding item listener to Hazelcast distributed build job queue for build agent with address {}", distributedDataAccessService.getLocalMemberAddress());
        this.listenerId = this.distributedDataAccessService.getDistributedBuildJobQueue().addItemListener(new QueuedBuildJobItemListener());

        /*
         * Check every 10 seconds whether the node has at least one thread available for a new build job.
         * If so, process the next build job.
         * This is a backup mechanism in case the build queue is not empty, no new build jobs are entering the queue and the
         * node otherwise stopped checking for build jobs in the queue.
         */
        scheduledFuture = taskScheduler.scheduleAtFixedRate(this::checkAvailabilityAndProcessNextBuild, Duration.ofSeconds(10));

        distributedDataAccessService.getPauseBuildAgentTopic().addMessageListener(buildAgentName -> {
            if (buildAgentShortName.equals(buildAgentName)) {
                pauseBuildAgent(false);
            }
        });

        distributedDataAccessService.getResumeBuildAgentTopic().addMessageListener(buildAgentName -> {
            if (buildAgentShortName.equals(buildAgentName)) {
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
        if (distributedDataAccessService.isInstanceRunning() && this.listenerId != null) {
            distributedDataAccessService.getDistributedBuildJobQueue().removeListener(this.listenerId);
        }
    }

    private void cancelCheckAvailabilityAndProcessNextBuildScheduledFuture() {
        if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
            scheduledFuture.cancel(false);
        }
    }

    /**
     * Wait 1 minute after startup and then every 1 minute update the build agent information of the local hazelcast member.
     * This is necessary because the build agent information is not updated automatically when a node joins the cluster.
     */
    @Scheduled(initialDelay = 60000, fixedRate = 60000) // 1 minute initial delay, 1 minute fixed rate
    public void updateBuildAgentInformation() {
        if (distributedDataAccessService.noDataMemberInClusterAvailable()) {
            log.debug("There are only lite member in the cluster. Not updating build agent information.");
            return;
        }

        removeOfflineNodes();

        // Add build agent information of local hazelcast member to map if not already present
        if (!distributedDataAccessService.getBuildAgentInformationMap().containsKey(distributedDataAccessService.getLocalMemberAddress())) {
            buildAgentInformationService.updateLocalBuildAgentInformation(isPaused.get());
        }
    }

    /**
     * Checks whether the node has at least one thread available for a new build job.
     * If so, process the next build job.
     */
    private void checkAvailabilityAndProcessNextBuild() {
        if (distributedDataAccessService.noDataMemberInClusterAvailable() || distributedDataAccessService.getDistributedBuildJobQueue() == null) {
            log.debug("There are only lite member in the cluster. Not processing build jobs.");
            return;
        }
        // Check conditions before acquiring the lock to avoid unnecessary locking
        if (!nodeIsAvailable()) {
            // Add build agent information of local hazelcast member to map if not already present
            if (!distributedDataAccessService.getBuildAgentInformationMap().containsKey(distributedDataAccessService.getLocalMemberAddress())) {
                buildAgentInformationService.updateLocalBuildAgentInformation(isPaused.get());
            }

            log.debug("Node has no available threads currently");
            return;
        }

        if (distributedDataAccessService.getDistributedBuildJobQueue().isEmpty() || isPaused.get()) {
            return;
        }
        BuildJobQueueItem buildJob = null;
        instanceLock.lock();
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
            instanceLock.unlock();
        }
    }

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
     * Removes build agent information for offline nodes and processing jobs that are assigned to these nodes.
     */
    private void removeOfflineNodes() {
        Set<String> memberAddresses = distributedDataAccessService.getClusterMemberAddresses();
        for (String key : distributedDataAccessService.getBuildAgentInformationMap().keySet()) {
            if (!memberAddresses.contains(key)) {
                removeBuildAgentInformationForNode(key);
                removeProcessingJobsForNode(key);
            }
        }
    }

    private void removeBuildAgentInformationForNode(String memberAddress) {
        log.debug("Cleaning up build agent information for offline node: {}", memberAddress);
        distributedDataAccessService.getDistributedBuildAgentInformation().remove(memberAddress);
    }

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
            enqueueBuildResult(resultQueueItem, buildJob);
            removeProcessingJob(buildJob);

            buildAgentInformationService.updateLocalBuildAgentInformationWithRecentJob(finishedJob, isPaused.get(), false, consecutiveBuildJobFailures.get());

            consecutiveBuildJobFailures.set(0);

            // process next build job if node is available
            checkAvailabilityAndProcessNextBuild();
        });

        futureResult.exceptionally(ex -> {
            log.debug("Build job completed with exception: {}", buildJob, ex);

            ZonedDateTime completionDate = ZonedDateTime.now();

            BuildJobQueueItem job;
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

            job = new BuildJobQueueItem(buildJob, completionDate, status);

            List<BuildLogDTO> buildLogs = buildLogsMap.getAndTruncateBuildLogs(buildJob.id());
            buildLogsMap.removeBuildLogs(buildJob.id());

            BuildResult failedResult = new BuildResult(buildJob.buildConfig().branch(), buildJob.buildConfig().assignmentCommitHash(), buildJob.buildConfig().testCommitHash(),
                    buildLogs, false);

            ResultQueueItem resultQueueItem = new ResultQueueItem(failedResult, job, buildLogs, ex);
            enqueueBuildResult(resultQueueItem, buildJob);
            removeProcessingJob(buildJob);

            buildAgentInformationService.updateLocalBuildAgentInformationWithRecentJob(job, isPaused.get(), false, consecutiveBuildJobFailures.get());

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
     * @param buildJob        the build job that was processed
     */
    private void enqueueBuildResult(ResultQueueItem resultQueueItem, BuildJobQueueItem buildJob) {
        if (processResults.get()) {
            distributedDataAccessService.getDistributedBuildResultQueue().add(resultQueueItem);
        }
        else {
            log.info("Build agent is paused. Not adding build result to result queue for build job: {}", buildJob);
        }
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

    private void pauseBuildAgent(boolean dueToFailures) {
        if (isPaused.get()) {
            log.info("Build agent is already paused");
            return;
        }

        pauseResumeLock.lock();
        try {
            log.info("Pausing build agent with address {}", distributedDataAccessService.getLocalMemberAddress());

            isPaused.set(true);
            removeListenerAndCancelScheduledFuture();
            buildAgentInformationService.updateLocalBuildAgentInformation(isPaused.get(), dueToFailures, consecutiveBuildJobFailures.get());

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
        List<BuildJobQueueItem> runningBuildJobsAfterGracePeriod = distributedDataAccessService.getDistributedProcessingJobs().getAll(runningBuildJobIdsAfterGracePeriod).values()
                .stream().toList();
        runningBuildJobIdsAfterGracePeriod.forEach(buildJobManagementService::cancelBuildJob);
        distributedDataAccessService.getDistributedBuildJobQueue().addAll(runningBuildJobsAfterGracePeriod);
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
            log.info("Resuming build agent with address {}", distributedDataAccessService.getLocalMemberAddress());
            isPaused.set(false);
            processResults.set(true);
            buildAgentConfiguration.openBuildAgentServices();
            consecutiveBuildJobFailures.set(0);

            // Cleanup docker containers
            buildAgentDockerService.cleanUpContainers();

            // We remove the listener and scheduledTask first to avoid having multiple listeners and scheduled tasks running
            removeListenerAndCancelScheduledFuture();
            log.info("Re-adding item listener to distributed build job queue for build agent with address {}", distributedDataAccessService.getLocalMemberAddress());
            listenerId = distributedDataAccessService.getDistributedBuildJobQueue().addItemListener(new QueuedBuildJobItemListener());
            scheduledFuture = taskScheduler.scheduleAtFixedRate(this::checkAvailabilityAndProcessNextBuild, Duration.ofSeconds(10));

            buildAgentInformationService.updateLocalBuildAgentInformation(isPaused.get());
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

    public class QueuedBuildJobItemListener implements QueueItemListener<BuildJobQueueItem> {

        @Override
        public void itemAdded(BuildJobQueueItem item) {
            log.debug("CIBuildJobQueueItem added to queue: {}", item);
            log.debug("Current queued items: {}", distributedDataAccessService.getQueuedJobsSize());
            checkAvailabilityAndProcessNextBuild();
        }

        @Override
        public void itemRemoved(BuildJobQueueItem item) {
            log.debug("CIBuildJobQueueItem removed from queue: {}", item);
        }
    }
}
