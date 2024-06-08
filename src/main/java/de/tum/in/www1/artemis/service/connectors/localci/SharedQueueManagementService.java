package de.tum.in.www1.artemis.service.connectors.localci;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALCI;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.lock.FencedLock;
import com.hazelcast.map.IMap;
import com.hazelcast.topic.ITopic;

import de.tum.in.www1.artemis.repository.BuildJobRepository;
import de.tum.in.www1.artemis.service.ProfileService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.BuildAgentInformation;
import de.tum.in.www1.artemis.service.connectors.localci.dto.BuildJobQueueItem;
import de.tum.in.www1.artemis.service.connectors.localci.dto.DockerImageBuild;

/**
 * Includes methods for managing and retrieving the shared build job queue and build agent information. Also contains methods for cancelling build jobs.
 */
@Service
@Profile(PROFILE_LOCALCI)
public class SharedQueueManagementService {

    private static final Logger log = LoggerFactory.getLogger(SharedQueueManagementService.class);

    private final BuildJobRepository buildJobRepository;

    private final HazelcastInstance hazelcastInstance;

    private final ProfileService profileService;

    private IQueue<BuildJobQueueItem> queue;

    /**
     * Map of build jobs currently being processed across all nodes
     */
    private IMap<String, BuildJobQueueItem> processingJobs;

    private IMap<String, BuildAgentInformation> buildAgentInformation;

    private IMap<String, ZonedDateTime> dockerImageCleanupInfo;

    /**
     * Lock to prevent multiple nodes from processing the same build job.
     */
    private FencedLock sharedLock;

    private ITopic<String> canceledBuildJobsTopic;

    public SharedQueueManagementService(BuildJobRepository buildJobRepository, @Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, ProfileService profileService) {
        this.buildJobRepository = buildJobRepository;
        this.hazelcastInstance = hazelcastInstance;
        this.profileService = profileService;
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
        this.canceledBuildJobsTopic = hazelcastInstance.getTopic("canceledBuildJobsTopic");
        this.dockerImageCleanupInfo = this.hazelcastInstance.getMap("dockerImageCleanupInfo");
    }

    /**
     * Pushes the last build dates for all docker images to the hazelcast map dockerImageCleanupInfo, only executed on the main node (with active scheduling)
     * This method is scheduled to run every 5 minutes with an initial delay of 30 seconds.
     */
    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 30 * 1000)
    public void pushDockerImageCleanupInfo() {
        if (profileService.isSchedulingActive()) {
            var startDate = System.currentTimeMillis();
            dockerImageCleanupInfo.clear();
            Set<DockerImageBuild> lastBuildDatesForDockerImages = buildJobRepository.findAllLastBuildDatesForDockerImages();
            for (DockerImageBuild dockerImageBuild : lastBuildDatesForDockerImages) {
                dockerImageCleanupInfo.put(dockerImageBuild.dockerImage(), dockerImageBuild.lastBuildCompletionDate());
            }
            log.info("pushDockerImageCleanupInfo took {}ms", System.currentTimeMillis() - startDate);
        }
    }

    public List<BuildJobQueueItem> getQueuedJobs() {
        return queue.stream().toList();
    }

    public List<BuildJobQueueItem> getProcessingJobs() {
        return processingJobs.values().stream().toList();
    }

    public List<BuildJobQueueItem> getQueuedJobsForCourse(long courseId) {
        return queue.stream().filter(job -> job.courseId() == courseId).toList();
    }

    public List<BuildJobQueueItem> getProcessingJobsForCourse(long courseId) {
        return processingJobs.values().stream().filter(job -> job.courseId() == courseId).toList();
    }

    public List<BuildJobQueueItem> getQueuedJobsForParticipation(long participationId) {
        return queue.stream().filter(job -> job.participationId() == participationId).toList();
    }

    public List<BuildJobQueueItem> getProcessingJobsForParticipation(long participationId) {
        return processingJobs.values().stream().filter(job -> job.participationId() == participationId).toList();
    }

    public List<BuildAgentInformation> getBuildAgentInformation() {
        return buildAgentInformation.values().stream().toList();
    }

    public List<BuildAgentInformation> getBuildAgentInformationWithoutRecentBuildJobs() {
        return buildAgentInformation.values().stream().map(agent -> new BuildAgentInformation(agent.name(), agent.maxNumberOfConcurrentBuildJobs(),
                agent.numberOfCurrentBuildJobs(), agent.runningBuildJobs(), agent.status(), null)).toList();
    }

    /**
     * Cancel a build job by removing it from the queue or stopping the build process.
     *
     * @param buildJobId id of the build job to cancel
     */
    public void cancelBuildJob(String buildJobId) {
        sharedLock.lock();
        try {
            // Remove build job if it is queued
            if (queue.stream().anyMatch(job -> Objects.equals(job.id(), buildJobId))) {
                List<BuildJobQueueItem> toRemove = new ArrayList<>();
                for (BuildJobQueueItem job : queue) {
                    if (Objects.equals(job.id(), buildJobId)) {
                        toRemove.add(job);
                    }
                }
                queue.removeAll(toRemove);
            }
            else {
                // Cancel build job if it is currently being processed
                BuildJobQueueItem buildJob = processingJobs.remove(buildJobId);
                if (buildJob != null) {
                    triggerBuildJobCancellation(buildJobId);
                }
            }
        }
        finally {
            sharedLock.unlock();
        }
    }

    /**
     * Trigger the cancellation of the build job for the given buildJobId.
     * The listener for the canceledBuildJobsTopic will then cancel the build job.
     *
     * @param buildJobId The id of the build job that should be cancelled.
     */
    private void triggerBuildJobCancellation(String buildJobId) {
        // Publish a message to the topic indicating that the specific build job should be canceled
        canceledBuildJobsTopic.publish(buildJobId);
    }

    /**
     * Cancel all queued build jobs.
     */
    public void cancelAllQueuedBuildJobs() {
        sharedLock.lock();
        try {
            log.debug("Cancelling all queued build jobs");
            queue.clear();
        }
        finally {
            sharedLock.unlock();
        }
    }

    /**
     * Cancel all running build jobs.
     */
    public void cancelAllRunningBuildJobs() {
        sharedLock.lock();
        try {
            for (BuildJobQueueItem buildJob : processingJobs.values()) {
                cancelBuildJob(buildJob.id());
            }
        }
        finally {
            sharedLock.unlock();
        }

    }

    /**
     * Cancel all running build jobs for an agent.
     *
     * @param agentName name of the agent
     */
    public void cancelAllRunningBuildJobsForAgent(String agentName) {
        sharedLock.lock();
        try {
            processingJobs.values().stream().filter(job -> Objects.equals(job.buildAgentAddress(), agentName)).forEach(job -> cancelBuildJob(job.id()));
        }
        finally {
            sharedLock.unlock();
        }
    }

    /**
     * Cancel all queued build jobs for a course.
     *
     * @param courseId id of the course
     */
    public void cancelAllQueuedBuildJobsForCourse(long courseId) {
        sharedLock.lock();
        try {
            List<BuildJobQueueItem> toRemove = new ArrayList<>();
            for (BuildJobQueueItem job : queue) {
                if (job.courseId() == courseId) {
                    toRemove.add(job);
                }
            }
            queue.removeAll(toRemove);
        }
        finally {
            sharedLock.unlock();
        }
    }

    /**
     * Cancel all running build jobs for a course.
     *
     * @param courseId id of the course
     */
    public void cancelAllRunningBuildJobsForCourse(long courseId) {
        for (BuildJobQueueItem buildJob : processingJobs.values()) {
            if (buildJob.courseId() == courseId) {
                cancelBuildJob(buildJob.id());
            }
        }
    }

    /**
     * Remove all queued build jobs for a participation from the shared build job queue.
     *
     * @param participationId id of the participation
     */
    public void cancelAllJobsForParticipation(long participationId) {
        sharedLock.lock();
        try {
            List<BuildJobQueueItem> toRemove = new ArrayList<>();
            for (BuildJobQueueItem queuedJob : queue) {
                if (queuedJob.participationId() == participationId) {
                    toRemove.add(queuedJob);
                }
            }
            queue.removeAll(toRemove);

            for (BuildJobQueueItem runningJob : processingJobs.values()) {
                if (runningJob.participationId() == participationId) {
                    cancelBuildJob(runningJob.id());
                }
            }
        }
        finally {
            sharedLock.unlock();
        }
    }

}
