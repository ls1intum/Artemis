package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.hazelcast.cluster.Member;
import com.hazelcast.collection.IQueue;
import com.hazelcast.collection.ItemEvent;
import com.hazelcast.collection.ItemListener;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.topic.ITopic;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

/**
 * Includes functionality for processing build jobs from the shared build job queue.
 */
@Profile(PROFILE_BUILDAGENT)
@Service
public class SharedQueueProcessingService {

    private static final Logger log = LoggerFactory.getLogger(SharedQueueProcessingService.class);

    private final HazelcastInstance hazelcastInstance;

    private final ThreadPoolExecutor localCIBuildExecutorService;

    private final BuildJobManagementService buildJobManagementService;

    private final BuildLogsMap buildLogsMap;

    private final AtomicInteger localProcessingJobs = new AtomicInteger(0);

    private final BuildAgentSshKeyService buildAgentSSHKeyService;

    private final TaskScheduler taskScheduler;

    private IQueue<BuildJobQueueItem> queue;

    private IQueue<ResultQueueItem> resultQueue;

    /**
     * Map of build jobs currently being processed across all nodes
     */
    private IMap<String, BuildJobQueueItem> processingJobs;

    private IMap<String, BuildAgentInformation> buildAgentInformation;

    /**
     * Lock for operations on single instance.
     */
    private final ReentrantLock instanceLock = new ReentrantLock();

    private UUID listenerId;

    /**
     * Scheduled future for checking availability and processing next build job.
     */
    private ScheduledFuture<?> scheduledFuture;

    private boolean isPaused = false;

    public SharedQueueProcessingService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, ExecutorService localCIBuildExecutorService,
            BuildJobManagementService buildJobManagementService, BuildLogsMap buildLogsMap, BuildAgentSshKeyService buildAgentSSHKeyService, TaskScheduler taskScheduler) {
        this.hazelcastInstance = hazelcastInstance;
        this.localCIBuildExecutorService = (ThreadPoolExecutor) localCIBuildExecutorService;
        this.buildJobManagementService = buildJobManagementService;
        this.buildLogsMap = buildLogsMap;
        this.buildAgentSSHKeyService = buildAgentSSHKeyService;
        this.taskScheduler = taskScheduler;
    }

    /**
     * Initialize relevant data from hazelcast
     */
    @PostConstruct
    public void init() {
        this.buildAgentInformation = this.hazelcastInstance.getMap("buildAgentInformation");
        this.processingJobs = this.hazelcastInstance.getMap("processingJobs");
        this.queue = this.hazelcastInstance.getQueue("buildJobQueue");
        this.resultQueue = this.hazelcastInstance.getQueue("buildResultQueue");
        this.listenerId = this.queue.addItemListener(new QueuedBuildJobItemListener(), true);

        /*
         * Check every 10 seconds whether the node has at least one thread available for a new build job.
         * If so, process the next build job.
         * This is a backup mechanism in case the build queue is not empty, no new build jobs are entering the queue and the
         * node otherwise stopped checking for build jobs in the queue.
         */
        scheduledFuture = taskScheduler.scheduleAtFixedRate(this::checkAvailabilityAndProcessNextBuild, Duration.ofSeconds(10));

        ITopic<String> pauseBuildAgentTopic = hazelcastInstance.getTopic("pauseBuildAgentTopic");
        pauseBuildAgentTopic.addMessageListener(message -> {
            if (message.getMessageObject().equals(hazelcastInstance.getCluster().getLocalMember().getAddress().toString())) {
                pauseBuildAgent();
            }
        });

        ITopic<String> resumeBuildAgentTopic = hazelcastInstance.getTopic("resumeBuildAgentTopic");
        resumeBuildAgentTopic.addMessageListener(message -> {
            if (message.getMessageObject().equals(hazelcastInstance.getCluster().getLocalMember().getAddress().toString())) {
                resumeBuildAgent();
            }
        });
    }

    @PreDestroy
    public void removeListener() {
        this.queue.removeItemListener(this.listenerId);
    }

    /**
     * Wait 1 minute after startup and then every 1 minute update the build agent information of the local hazelcast member.
     * This is necessary because the build agent information is not updated automatically when a node joins the cluster.
     */
    @Scheduled(initialDelay = 60000, fixedRate = 60000) // 1 minute initial delay, 1 minute fixed rate
    public void updateBuildAgentInformation() {
        if (noDataMemberInClusterAvailable(hazelcastInstance)) {
            log.debug("There are only lite member in the cluster. Not updating build agent information.");
            return;
        }
        // Remove build agent information of offline nodes
        removeOfflineNodes();

        // Add build agent information of local hazelcast member to map if not already present
        if (!buildAgentInformation.containsKey(hazelcastInstance.getCluster().getLocalMember().getAddress().toString())) {
            updateLocalBuildAgentInformation();
        }
    }

    /**
     * Checks whether the node has at least one thread available for a new build job.
     * If so, process the next build job.
     */
    private void checkAvailabilityAndProcessNextBuild() {
        if (noDataMemberInClusterAvailable(hazelcastInstance)) {
            log.debug("There are only lite member in the cluster. Not processing build jobs.");
            return;
        }
        // Check conditions before acquiring the lock to avoid unnecessary locking
        if (!nodeIsAvailable()) {
            // Add build agent information of local hazelcast member to map if not already present
            if (!buildAgentInformation.containsKey(hazelcastInstance.getCluster().getLocalMember().getAddress().toString())) {
                updateLocalBuildAgentInformation();
            }

            log.debug("Node has no available threads currently");
            return;
        }

        if (queue.isEmpty()) {
            return;
        }
        BuildJobQueueItem buildJob = null;
        instanceLock.lock();
        try {
            // Recheck conditions after acquiring the lock to ensure they are still valid
            if (!nodeIsAvailable() || queue.isEmpty() || isPaused) {
                return;
            }

            buildJob = addToProcessingJobs();

            processBuild(buildJob);
        }
        catch (RejectedExecutionException e) {
            log.error("Couldn't add build job to threadpool: {}\n Concurrent Build Jobs Count: {} Active tasks in pool: {}, Concurrent Build Jobs Size: {}", buildJob,
                    localProcessingJobs.get(), localCIBuildExecutorService.getActiveCount(), localCIBuildExecutorService.getMaximumPoolSize(), e);

            // Add the build job back to the queue
            if (buildJob != null) {
                processingJobs.remove(buildJob.id());

                buildJob = new BuildJobQueueItem(buildJob, "");
                log.info("Adding build job back to the queue: {}", buildJob);
                queue.add(buildJob);
                localProcessingJobs.decrementAndGet();
            }

            updateLocalBuildAgentInformation();
        }
        finally {
            instanceLock.unlock();
        }
    }

    private static boolean noDataMemberInClusterAvailable(HazelcastInstance hazelcastInstance) {
        return hazelcastInstance.getCluster().getMembers().stream().allMatch(Member::isLiteMember);
    }

    private BuildJobQueueItem addToProcessingJobs() {
        BuildJobQueueItem buildJob = queue.poll();
        if (buildJob != null) {
            String hazelcastMemberAddress = hazelcastInstance.getCluster().getLocalMember().getAddress().toString();

            BuildJobQueueItem processingJob = new BuildJobQueueItem(buildJob, hazelcastMemberAddress);

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
        String memberAddress = hazelcastInstance.getCluster().getLocalMember().getAddress().toString();
        try {
            buildAgentInformation.lock(memberAddress);
            // Add/update
            BuildAgentInformation info = getUpdatedLocalBuildAgentInformation(recentBuildJob);
            try {
                buildAgentInformation.put(info.name(), info);
            }
            catch (Exception e) {
                log.error("Error while updating build agent information for agent {}", info.name(), e);
            }
        }
        finally {
            buildAgentInformation.unlock(memberAddress);
        }
    }

    private BuildAgentInformation getUpdatedLocalBuildAgentInformation(BuildJobQueueItem recentBuildJob) {
        String memberAddress = hazelcastInstance.getCluster().getLocalMember().getAddress().toString();
        List<BuildJobQueueItem> processingJobsOfMember = getProcessingJobsOfNode(memberAddress);
        int numberOfCurrentBuildJobs = processingJobsOfMember.size();
        int maxNumberOfConcurrentBuilds = localCIBuildExecutorService.getMaximumPoolSize();
        boolean active = numberOfCurrentBuildJobs > 0;
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

        return new BuildAgentInformation(memberAddress, maxNumberOfConcurrentBuilds, numberOfCurrentBuildJobs, processingJobsOfMember, active, recentBuildJobs, publicSshKey);
    }

    private List<BuildJobQueueItem> getProcessingJobsOfNode(String memberAddress) {
        return processingJobs.values().stream().filter(job -> Objects.equals(job.buildAgentAddress(), memberAddress)).toList();
    }

    private void removeOfflineNodes() {
        Set<String> memberAddresses = hazelcastInstance.getCluster().getMembers().stream().map(member -> member.getAddress().toString()).collect(Collectors.toSet());
        for (String key : buildAgentInformation.keySet()) {
            if (!memberAddresses.contains(key)) {
                buildAgentInformation.remove(key);
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

            JobTimingInfo jobTimingInfo = new JobTimingInfo(buildJob.jobTimingInfo().submissionDate(), buildJob.jobTimingInfo().buildStartDate(), ZonedDateTime.now());

            BuildJobQueueItem finishedJob = new BuildJobQueueItem(buildJob.id(), buildJob.name(), buildJob.buildAgentAddress(), buildJob.participationId(), buildJob.courseId(),
                    buildJob.exerciseId(), buildJob.retryCount(), buildJob.priority(), BuildStatus.SUCCESSFUL, buildJob.repositoryInfo(), jobTimingInfo, buildJob.buildConfig(),
                    null);

            List<BuildLogEntry> buildLogs = buildLogsMap.getBuildLogs(buildJob.id());
            buildLogsMap.removeBuildLogs(buildJob.id());

            ResultQueueItem resultQueueItem = new ResultQueueItem(buildResult, finishedJob, buildLogs, null);
            resultQueue.add(resultQueueItem);

            // after processing a build job, remove it from the processing jobs
            processingJobs.remove(buildJob.id());
            localProcessingJobs.decrementAndGet();
            updateLocalBuildAgentInformationWithRecentJob(finishedJob);

            // process next build job if node is available
            checkAvailabilityAndProcessNextBuild();
        });

        futureResult.exceptionally(ex -> {
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

            List<BuildLogEntry> buildLogs = buildLogsMap.getBuildLogs(buildJob.id());
            buildLogsMap.removeBuildLogs(buildJob.id());

            BuildResult failedResult = new BuildResult(buildJob.buildConfig().branch(), buildJob.buildConfig().assignmentCommitHash(), buildJob.buildConfig().testCommitHash(),
                    false);
            failedResult.setBuildLogEntries(buildLogs);

            ResultQueueItem resultQueueItem = new ResultQueueItem(failedResult, job, buildLogs, ex);
            resultQueue.add(resultQueueItem);

            processingJobs.remove(buildJob.id());
            localProcessingJobs.decrementAndGet();
            updateLocalBuildAgentInformationWithRecentJob(job);

            checkAvailabilityAndProcessNextBuild();
            return null;
        });
    }

    private void pauseBuildAgent() {
        log.info("Pausing build agent with address {}", hazelcastInstance.getCluster().getLocalMember().getAddress().toString());

        this.isPaused = true;
        this.removeListener();
        if (this.scheduledFuture != null && !this.scheduledFuture.isCancelled()) {
            this.scheduledFuture.cancel(false);
        }

        log.info("Gracefully cancelling running build jobs");

        if (buildJobManagementService.getRunningBuildJobIds().isEmpty()) {
            log.info("No running build jobs to cancel");
        }
        else {
            // Sleep for 10 seconds to allow the build jobs to be finished. If they are not finished, they will be cancelled.
            try {
                Thread.sleep(10000);
            }
            catch (InterruptedException e) {
                log.error("Error while pausing build agent", e);
            }

            if (!this.isPaused) {
                log.info("Build agent was resumed before the build jobs could be cancelled");
                return;
            }

            Set<String> runningBuildJobIds = buildJobManagementService.getRunningBuildJobIds();
            List<BuildJobQueueItem> runningBuildJobs = processingJobs.getAll(runningBuildJobIds).values().stream().toList();
            runningBuildJobIds.forEach(buildJobManagementService::cancelBuildJob);
            this.queue.addAll(runningBuildJobs);
            log.info("Cancelled running build jobs and added them back to the queue with Ids {}", runningBuildJobIds);
            log.debug("Cancelled running build jobs: {}", runningBuildJobs);

        }
    }

    private void resumeBuildAgent() {
        log.info("Resuming build agent with address {}", hazelcastInstance.getCluster().getLocalMember().getAddress().toString());
        this.isPaused = false;
        this.listenerId = this.queue.addItemListener(new QueuedBuildJobItemListener(), true);
        this.scheduledFuture = taskScheduler.scheduleAtFixedRate(this::checkAvailabilityAndProcessNextBuild, Duration.ofSeconds(10));
    }

    /**
     * Checks whether the node has at least one thread available for a new build job.
     */
    private boolean nodeIsAvailable() {
        log.debug("Currently processing jobs on this node: {}, active threads in Pool: {}, maximum pool size of thread executor : {}", localProcessingJobs.get(),
                localCIBuildExecutorService.getActiveCount(), localCIBuildExecutorService.getMaximumPoolSize());
        return localProcessingJobs.get() < localCIBuildExecutorService.getMaximumPoolSize();
    }

    public class QueuedBuildJobItemListener implements ItemListener<BuildJobQueueItem> {

        @Override
        public void itemAdded(ItemEvent<BuildJobQueueItem> event) {
            log.debug("CIBuildJobQueueItem added to queue: {}", event.getItem());
            checkAvailabilityAndProcessNextBuild();
        }

        @Override
        public void itemRemoved(ItemEvent<BuildJobQueueItem> event) {
            log.debug("CIBuildJobQueueItem removed from queue: {}", event.getItem());
        }
    }
}
