package de.tum.in.www1.artemis.service.connectors.localci.buildagent;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_BUILDAGENT;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.hazelcast.collection.IQueue;
import com.hazelcast.collection.ItemEvent;
import com.hazelcast.collection.ItemListener;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.lock.FencedLock;
import com.hazelcast.map.IMap;

import de.tum.in.www1.artemis.domain.enumeration.BuildStatus;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.connectors.localci.dto.*;

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

    private final AtomicInteger localProcessingJobs = new AtomicInteger(0);

    /**
     * Lock to prevent multiple nodes from processing the same build job.
     */
    private FencedLock sharedLock;

    private IQueue<LocalCIBuildJobQueueItem> queue;

    private IQueue<ResultQueueItem> resultQueue;

    /**
     * Map of build jobs currently being processed across all nodes
     */
    private IMap<String, LocalCIBuildJobQueueItem> processingJobs;

    private IMap<String, LocalCIBuildAgentInformation> buildAgentInformation;

    /**
     * Lock for operations on single instance.
     */
    private final ReentrantLock instanceLock = new ReentrantLock();

    private UUID listenerId;

    public SharedQueueProcessingService(HazelcastInstance hazelcastInstance, ExecutorService localCIBuildExecutorService, BuildJobManagementService buildJobManagementService) {
        this.hazelcastInstance = hazelcastInstance;
        this.localCIBuildExecutorService = (ThreadPoolExecutor) localCIBuildExecutorService;
        this.buildJobManagementService = buildJobManagementService;
    }

    /**
     * Initialize relevant data from hazelcast
     */
    @PostConstruct
    public void init() {
        this.buildAgentInformation = this.hazelcastInstance.getMap("buildAgentInformation");
        this.processingJobs = this.hazelcastInstance.getMap("processingJobs");
        this.sharedLock = this.hazelcastInstance.getCPSubsystem().getLock("buildJobQueueLock");
        this.queue = this.hazelcastInstance.getQueue("buildJobQueue");
        this.resultQueue = this.hazelcastInstance.getQueue("buildResultQueue");
        this.listenerId = this.queue.addItemListener(new SharedQueueProcessingService.QueuedBuildJobItemListener(), true);
    }

    public void removeListener() {
        this.queue.removeItemListener(this.listenerId);
    }

    /**
     * Wait 1 minute after startup and then every 1 minute update the build agent information of the local hazelcast member.
     * This is necessary because the build agent information is not updated automatically when a node joins the cluster.
     */
    @Scheduled(initialDelay = 60000, fixedRate = 60000) // 1 minute initial delay, 1 minute fixed rate
    public void updateBuildAgentInformation() {
        // Remove build agent information of offline nodes
        removeOfflineNodes();

        // Add build agent information of local hazelcast member to map if not already present
        if (!buildAgentInformation.containsKey(hazelcastInstance.getCluster().getLocalMember().getAddress().toString())) {
            updateLocalBuildAgentInformation();
        }
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

        instanceLock.lock();
        try {
            // Recheck conditions after acquiring the lock to ensure they are still valid
            if (!nodeIsAvailable() || queue.isEmpty()) {
                return;
            }

            LocalCIBuildJobQueueItem buildJob;

            // Lock the queue to prevent multiple nodes from processing the same build job
            sharedLock.lock();
            try {
                buildJob = addToProcessingJobs();
            }
            finally {
                sharedLock.unlock();
            }
            processBuild(buildJob);
        }
        finally {
            instanceLock.unlock();
        }
    }

    private LocalCIBuildJobQueueItem addToProcessingJobs() {
        LocalCIBuildJobQueueItem buildJob = queue.poll();
        if (buildJob != null) {
            String hazelcastMemberAddress = hazelcastInstance.getCluster().getLocalMember().getAddress().toString();

            LocalCIBuildJobQueueItem processingJob = new LocalCIBuildJobQueueItem(buildJob, hazelcastMemberAddress);

            processingJobs.put(processingJob.id(), processingJob);
            localProcessingJobs.incrementAndGet();

            updateLocalBuildAgentInformation();
            return processingJob;
        }
        return null;
    }

    private void updateLocalBuildAgentInformation() {
        // Add/update
        String memberAddress = hazelcastInstance.getCluster().getLocalMember().getAddress().toString();
        List<LocalCIBuildJobQueueItem> processingJobsOfMember = getProcessingJobsOfNode(memberAddress);
        int numberOfCurrentBuildJobs = processingJobsOfMember.size();
        int maxNumberOfConcurrentBuilds = localCIBuildExecutorService.getMaximumPoolSize();
        boolean active = numberOfCurrentBuildJobs > 0;
        LocalCIBuildAgentInformation agent = buildAgentInformation.get(memberAddress);
        List<LocalCIBuildJobQueueItem> recentBuildJobs;
        if (agent != null) {
            recentBuildJobs = agent.recentBuildJobs();
        }
        else {
            recentBuildJobs = new ArrayList<>();
        }
        LocalCIBuildAgentInformation info = new LocalCIBuildAgentInformation(memberAddress, maxNumberOfConcurrentBuilds, numberOfCurrentBuildJobs, processingJobsOfMember, active,
                recentBuildJobs);
        try {
            buildAgentInformation.lock(memberAddress);
            buildAgentInformation.put(memberAddress, info);
        }
        catch (Exception e) {
            log.error("Error while updating build agent information for agent {}", memberAddress, e);
        }
        finally {
            buildAgentInformation.unlock(memberAddress);
        }
    }

    private List<LocalCIBuildJobQueueItem> getProcessingJobsOfNode(String memberAddress) {
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
    private void processBuild(LocalCIBuildJobQueueItem buildJob) {
        // The 'user' is not properly logged into Artemis, this leads to an issue when accessing custom repository methods.
        // Therefore, a mock auth object has to be created.
        SecurityUtils.setAuthorizationObject();

        if (buildJob == null) {
            return;
        }

        log.info("Processing build job: {}", buildJob);

        CompletableFuture<LocalCIBuildResult> futureResult = buildJobManagementService.executeBuildJob(buildJob);
        futureResult.thenAccept(buildResult -> {

            JobTimingInfo jobTimingInfo = new JobTimingInfo(buildJob.jobTimingInfo().submissionDate(), buildJob.jobTimingInfo().buildStartDate(), ZonedDateTime.now());

            LocalCIBuildJobQueueItem finishedJob = new LocalCIBuildJobQueueItem(buildJob.id(), buildJob.name(), buildJob.buildAgentAddress(), buildJob.participationId(),
                    buildJob.courseId(), buildJob.exerciseId(), buildJob.retryCount(), buildJob.priority(), BuildStatus.SUCCESSFUL, buildJob.repositoryInfo(), jobTimingInfo,
                    buildJob.buildConfig(), null);

            ResultQueueItem resultQueueItem = new ResultQueueItem(buildResult, finishedJob, null);
            resultQueue.add(resultQueueItem);

            // after processing a build job, remove it from the processing jobs
            processingJobs.remove(buildJob.id());
            localProcessingJobs.decrementAndGet();
            addToRecentBuildJobs(finishedJob);

            // process next build job if node is available
            checkAvailabilityAndProcessNextBuild();
        });

        futureResult.exceptionally(ex -> {
            ZonedDateTime completionDate = ZonedDateTime.now();

            LocalCIBuildJobQueueItem job;
            BuildStatus status;

            if (!(ex.getCause() instanceof CancellationException) || !ex.getMessage().equals("Build job with id " + buildJob.id() + " was cancelled.")) {
                status = BuildStatus.FAILED;
                log.error("Error while processing build job: {}", buildJob, ex);
            }
            else {
                status = BuildStatus.CANCELLED;
            }

            job = new LocalCIBuildJobQueueItem(buildJob, completionDate, status);

            ResultQueueItem resultQueueItem = new ResultQueueItem(null, job, ex);
            resultQueue.add(resultQueueItem);

            processingJobs.remove(buildJob.id());
            localProcessingJobs.decrementAndGet();
            addToRecentBuildJobs(job);

            checkAvailabilityAndProcessNextBuild();
            return null;
        });
    }

    /**
     * Add a build job to the list of recent build jobs. Only the last 20 build jobs are needed.
     * TODO: make the number configurable
     *
     * @param buildJob The build job to add to the list of recent build jobs
     */
    private void addToRecentBuildJobs(LocalCIBuildJobQueueItem buildJob) {
        String memberAddress = hazelcastInstance.getCluster().getLocalMember().getAddress().toString();
        LocalCIBuildAgentInformation agent = buildAgentInformation.get(memberAddress);
        if (agent != null) {
            List<LocalCIBuildJobQueueItem> recentBuildJobs = agent.recentBuildJobs();
            if (recentBuildJobs.size() >= 20) {
                recentBuildJobs.remove(0);
            }
            recentBuildJobs.add(buildJob);
            buildAgentInformation.put(memberAddress, new LocalCIBuildAgentInformation(agent, recentBuildJobs));
        }
        updateLocalBuildAgentInformation();
    }

    /**
     * Checks whether the node has at least one thread available for a new build job.
     */
    private boolean nodeIsAvailable() {
        log.debug("Currently processing jobs on this node: {}, maximum pool size of thread executor : {}", localProcessingJobs.get(),
                localCIBuildExecutorService.getMaximumPoolSize());
        return localProcessingJobs.get() < localCIBuildExecutorService.getMaximumPoolSize();
    }

    public class QueuedBuildJobItemListener implements ItemListener<LocalCIBuildJobQueueItem> {

        @Override
        public void itemAdded(ItemEvent<LocalCIBuildJobQueueItem> event) {
            log.debug("CIBuildJobQueueItem added to queue: {}", event.getItem());
            checkAvailabilityAndProcessNextBuild();
        }

        @Override
        public void itemRemoved(ItemEvent<LocalCIBuildJobQueueItem> event) {
            log.debug("CIBuildJobQueueItem removed from queue: {}", event.getItem());
        }
    }
}
