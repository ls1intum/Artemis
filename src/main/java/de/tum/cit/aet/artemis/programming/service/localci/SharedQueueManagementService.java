package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.topic.ITopic;

import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.programming.domain.BuildJob;
import de.tum.cit.aet.artemis.programming.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.programming.service.localci.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.programming.service.localci.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.programming.service.localci.dto.DockerImageBuild;
import de.tum.cit.aet.artemis.service.ProfileService;
import de.tum.cit.aet.artemis.web.rest.dto.pageablesearch.FinishedBuildJobPageableSearchDTO;

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
                agent.numberOfCurrentBuildJobs(), agent.runningBuildJobs(), agent.status(), null, null)).toList();
    }

    /**
     * Cancel a build job by removing it from the queue or stopping the build process.
     *
     * @param buildJobId id of the build job to cancel
     */
    public void cancelBuildJob(String buildJobId) {
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
        log.debug("Cancelling all queued build jobs");
        queue.clear();
    }

    /**
     * Cancel all running build jobs.
     */
    public void cancelAllRunningBuildJobs() {
        for (BuildJobQueueItem buildJob : processingJobs.values()) {
            cancelBuildJob(buildJob.id());
        }
    }

    /**
     * Cancel all running build jobs for an agent.
     *
     * @param agentName name of the agent
     */
    public void cancelAllRunningBuildJobsForAgent(String agentName) {
        processingJobs.values().stream().filter(job -> Objects.equals(job.buildAgentAddress(), agentName)).forEach(job -> cancelBuildJob(job.id()));
    }

    /**
     * Cancel all queued build jobs for a course.
     *
     * @param courseId id of the course
     */
    public void cancelAllQueuedBuildJobsForCourse(long courseId) {
        List<BuildJobQueueItem> toRemove = new ArrayList<>();
        for (BuildJobQueueItem job : queue) {
            if (job.courseId() == courseId) {
                toRemove.add(job);
            }
        }
        queue.removeAll(toRemove);
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

    /**
     * Get all finished build jobs that match the search criteria.
     *
     * @param search   the search criteria
     * @param courseId the id of the course
     * @return the page of build jobs
     */
    public Page<BuildJob> getFilteredFinishedBuildJobs(FinishedBuildJobPageableSearchDTO search, Long courseId) {
        Duration buildDurationLower = search.buildDurationLower() == null ? null : Duration.ofSeconds(search.buildDurationLower());
        Duration buildDurationUpper = search.buildDurationUpper() == null ? null : Duration.ofSeconds(search.buildDurationUpper());

        var sortOptions = Sort.by(search.pageable().getSortedColumn());
        sortOptions = search.pageable().getSortingOrder() == SortingOrder.ASCENDING ? sortOptions.ascending() : sortOptions.descending();
        var pageRequest = PageRequest.of(search.pageable().getPage() - 1, search.pageable().getPageSize(), sortOptions);

        Page<Long> buildJobIdsPage = buildJobRepository.findIdsByFilterCriteria(search.buildStatus(), search.buildAgentAddress(), search.startDate(), search.endDate(),
                search.pageable().getSearchTerm(), courseId, buildDurationLower, buildDurationUpper, pageRequest);

        List<Long> buildJobIds = buildJobIdsPage.toList();
        // Fetch the build jobs with results. Since this query used "IN" clause, the order of the results is not guaranteed. We need to order them by the order of the ids.
        List<BuildJob> unorderedBuildJobs = buildJobRepository.findWithDataByIdIn(buildJobIds);

        Map<Long, BuildJob> buildJobMap = unorderedBuildJobs.stream().collect(Collectors.toMap(BuildJob::getId, Function.identity()));

        List<BuildJob> orderedBuildJobs = buildJobIds.stream().map(buildJobMap::get).toList();

        return new PageImpl<>(orderedBuildJobs, buildJobIdsPage.getPageable(), buildJobIdsPage.getTotalElements());
    }

}
