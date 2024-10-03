package de.tum.cit.aet.artemis.buildagent.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.redisson.api.RMap;
import org.redisson.api.RPriorityQueue;
import org.redisson.api.RQueue;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.ListAddListener;
import org.redisson.api.listener.ListRemoveListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.connectors.redis.RedisClientListResolver;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

/**
 * Includes functionality for processing build jobs from the shared build job queue.
 */
@Profile(PROFILE_BUILDAGENT)
@Service
public class SharedQueueProcessingService {

    private static final Logger log = LoggerFactory.getLogger(SharedQueueProcessingService.class);

    private final RedissonClient redissonClient;

    private final ThreadPoolExecutor localCIBuildExecutorService;

    private final BuildJobManagementService buildJobManagementService;

    private final BuildLogsMap buildLogsMap;

    private final AtomicInteger localProcessingJobs = new AtomicInteger(0);

    private final BuildAgentSshKeyService buildAgentSSHKeyService;

    private RPriorityQueue<BuildJobQueueItem> buildJobQueue;

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

    @Value("${spring.data.redis.client-name}")
    private String redisClientName;     // this is used as build agent name

    private final RedisClientListResolver redisClientListResolver;

    public SharedQueueProcessingService(RedissonClient redissonClient, ExecutorService localCIBuildExecutorService, BuildJobManagementService buildJobManagementService,
            BuildLogsMap buildLogsMap, BuildAgentSshKeyService buildAgentSSHKeyService, RedisClientListResolver redisClientListResolver) {
        this.redissonClient = redissonClient;
        this.localCIBuildExecutorService = (ThreadPoolExecutor) localCIBuildExecutorService;
        this.buildJobManagementService = buildJobManagementService;
        this.buildLogsMap = buildLogsMap;
        this.buildAgentSSHKeyService = buildAgentSSHKeyService;
        this.redisClientListResolver = redisClientListResolver;
    }

    /**
     * Initialize relevant data for cluster communication.
     */
    @PostConstruct
    public void init() {
        this.buildAgentInformation = this.redissonClient.getMap("buildAgentInformation");
        this.processingJobs = this.redissonClient.getMap("processingJobs");
        this.buildJobQueue = this.redissonClient.getPriorityQueue("buildJobQueue");
        this.resultQueue = this.redissonClient.getQueue("buildResultQueue");
        this.listenerIdAdd = this.buildJobQueue.addListener((ListAddListener) name -> {
            log.info("CIBuildJobQueueItem added to queue: {}", name);
            checkAvailabilityAndProcessNextBuild();
        });
        this.listenerIdRemove = this.buildJobQueue.addListener((ListRemoveListener) name -> log.info("CIBuildJobQueueItem removed from queue: {}", name));
    }

    @PreDestroy
    public void removeListener() {
        // Remove the build agent from the map to avoid issues
        this.buildAgentInformation.remove(getBuildAgentName());
        this.buildJobQueue.removeListener(this.listenerIdAdd);
        this.buildJobQueue.removeListener(this.listenerIdRemove);
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

        if (buildJobQueue.isEmpty()) {
            return;
        }
        BuildJobQueueItem buildJob = null;
        instanceLock.lock();
        try {
            // Recheck conditions after acquiring the lock to ensure they are still valid
            if (!nodeIsAvailable() || buildJobQueue.isEmpty()) {
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
                buildJobQueue.add(buildJob);
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

            BuildJobQueueItem processingJob = new BuildJobQueueItem(buildJob, memberAddress);

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
            buildAgentInformation.put(info.name(), info);
        }
        catch (Exception e) {
            log.error("Error while updating build agent information for agent {}", info.name(), e);
        }
    }

    private BuildAgentInformation getUpdatedLocalBuildAgentInformation(BuildJobQueueItem recentBuildJob) {
        String memberAddress = getBuildAgentName();
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

            JobTimingInfo jobTimingInfo = new JobTimingInfo(buildJob.jobTimingInfo().submissionDate(), buildJob.jobTimingInfo().buildStartDate(), ZonedDateTime.now());

            BuildJobQueueItem finishedJob = new BuildJobQueueItem(buildJob.id(), buildJob.name(), buildJob.buildAgentAddress(), buildJob.participationId(), buildJob.courseId(),
                    buildJob.exerciseId(), buildJob.retryCount(), buildJob.priority(), BuildStatus.SUCCESSFUL, buildJob.repositoryInfo(), jobTimingInfo, buildJob.buildConfig(),
                    null);

            List<BuildLogEntry> buildLogs = buildLogsMap.getBuildLogs(buildJob.id());
            buildLogsMap.removeBuildLogs(buildJob.id());

            ResultQueueItem resultQueueItem = new ResultQueueItem(buildResult, finishedJob, buildLogs, null);
            log.info("Adding build result to result queue: {}", resultQueueItem);
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
            log.info("Adding build result to result queue: {}", resultQueueItem);
            resultQueue.add(resultQueueItem);

            processingJobs.remove(buildJob.id());
            localProcessingJobs.decrementAndGet();
            updateLocalBuildAgentInformationWithRecentJob(job);

            checkAvailabilityAndProcessNextBuild();
            return null;
        });
    }

    /**
     * Checks whether the node has at least one thread available for a new build job.
     */
    private boolean nodeIsAvailable() {
        log.debug("Currently processing jobs on this node: {}, active threads in Pool: {}, maximum pool size of thread executor : {}", localProcessingJobs.get(),
                localCIBuildExecutorService.getActiveCount(), localCIBuildExecutorService.getMaximumPoolSize());
        return localProcessingJobs.get() < localCIBuildExecutorService.getMaximumPoolSize();
    }
}
