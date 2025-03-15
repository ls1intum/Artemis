package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryRemovedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.DockerImageBuild;
import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.FinishedBuildJobPageableSearchDTO;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.repository.BuildJobRepository;

/**
 * Includes methods for managing and retrieving the shared build job queue and build agent information. Also contains methods for cancelling build jobs.
 */
@Service
@Profile(PROFILE_LOCALCI)
public class SharedQueueManagementService {

    private static final Logger log = LoggerFactory.getLogger(SharedQueueManagementService.class);

    private final BuildJobRepository buildJobRepository;

    private final DistributedDataAccessService distributedDataAccessService;

    private final ProfileService profileService;

    private int buildAgentsCapacity;

    private int runningBuildJobCount;

    public SharedQueueManagementService(BuildJobRepository buildJobRepository, ProfileService profileService, DistributedDataAccessService distributedDataAccessService) {
        this.buildJobRepository = buildJobRepository;
        this.profileService = profileService;
        this.distributedDataAccessService = distributedDataAccessService;
    }

    /**
     * Initialize relevant data from hazelcast
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        this.distributedDataAccessService.getDistributedBuildAgentInformation().addEntryListener(new BuildAgentListener(), false);
        this.updateBuildAgentCapacity();
    }

    /**
     * Pushes the last build dates for all docker images to the hazelcast map dockerImageCleanupInfo, only executed on the main node (with active scheduling)
     * This method is scheduled to run every 5 minutes with an initial delay of 30 seconds.
     */
    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 30 * 1000)
    public void pushDockerImageCleanupInfo() {
        if (profileService.isSchedulingActive()) {
            var startDate = System.currentTimeMillis();
            distributedDataAccessService.getDistributedDockerImageCleanupInfo().clear();
            Set<DockerImageBuild> lastBuildDatesForDockerImages = buildJobRepository.findAllLastBuildDatesForDockerImages();
            for (DockerImageBuild dockerImageBuild : lastBuildDatesForDockerImages) {
                distributedDataAccessService.getDistributedDockerImageCleanupInfo().put(dockerImageBuild.dockerImage(), dockerImageBuild.lastBuildCompletionDate());
            }
            log.info("pushDockerImageCleanupInfo took {}ms", System.currentTimeMillis() - startDate);
        }
    }

    public void pauseBuildAgent(String agent) {
        distributedDataAccessService.getPauseBuildAgentTopic().publish(agent);
    }

    public void pauseAllBuildAgents() {
        distributedDataAccessService.getBuildAgentInformation().forEach(agent -> pauseBuildAgent(agent.buildAgent().name()));
    }

    public void resumeBuildAgent(String agent) {
        distributedDataAccessService.getResumeBuildAgentTopic().publish(agent);
    }

    public void resumeAllBuildAgents() {
        distributedDataAccessService.getBuildAgentInformation().forEach(agent -> resumeBuildAgent(agent.buildAgent().name()));
    }

    /**
     * Cancel a build job by removing it from the queue or stopping the build process.
     *
     * @param buildJobId id of the build job to cancel
     */
    public void cancelBuildJob(String buildJobId) {
        // Remove build job if it is queued
        List<BuildJobQueueItem> queuedJobs = distributedDataAccessService.getQueuedJobs();
        if (queuedJobs.stream().anyMatch(job -> Objects.equals(job.id(), buildJobId))) {
            List<BuildJobQueueItem> toRemove = new ArrayList<>();
            for (BuildJobQueueItem job : queuedJobs) {
                if (Objects.equals(job.id(), buildJobId)) {
                    toRemove.add(job);
                }
            }
            distributedDataAccessService.getDistributedQueuedJobs().removeAll(toRemove);
            updateCancelledQueuedBuildJobsStatus(toRemove);
        }
        else {
            // Cancel build job if it is currently being processed
            BuildJobQueueItem buildJob = distributedDataAccessService.getDistributedProcessingJobs().remove(buildJobId);
            if (buildJob != null) {
                triggerBuildJobCancellation(buildJobId);
            }
        }
    }

    private void updateCancelledQueuedBuildJobsStatus(List<BuildJobQueueItem> queuedJobs) {
        for (BuildJobQueueItem queuedJob : queuedJobs) {
            buildJobRepository.updateBuildJobStatus(queuedJob.id(), BuildStatus.CANCELLED);
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
        distributedDataAccessService.getCanceledBuildJobsTopic().publish(buildJobId);
    }

    /**
     * Cancel all queued build jobs.
     */
    public void cancelAllQueuedBuildJobs() {
        log.debug("Cancelling all queued build jobs");
        List<BuildJobQueueItem> queuedJobs = distributedDataAccessService.getQueuedJobs();
        distributedDataAccessService.getDistributedQueuedJobs().clear();
        updateCancelledQueuedBuildJobsStatus(queuedJobs);
    }

    /**
     * Cancel all running build jobs.
     */
    public void cancelAllRunningBuildJobs() {
        List<BuildJobQueueItem> runningJobs = distributedDataAccessService.getProcessingJobs();
        for (BuildJobQueueItem buildJob : runningJobs) {
            cancelBuildJob(buildJob.id());
        }
    }

    /**
     * Cancel all running build jobs for an agent.
     *
     * @param agentName name of the agent
     */
    public void cancelAllRunningBuildJobsForAgent(String agentName) {
        distributedDataAccessService.getProcessingJobs().stream().filter(job -> Objects.equals(job.buildAgent().name(), agentName)).forEach(job -> cancelBuildJob(job.id()));
    }

    /**
     * Cancel all queued build jobs for a course.
     *
     * @param courseId id of the course
     */
    public void cancelAllQueuedBuildJobsForCourse(long courseId) {
        List<BuildJobQueueItem> queuedJobs = distributedDataAccessService.getQueuedJobs();
        List<BuildJobQueueItem> toRemove = new ArrayList<>();
        for (BuildJobQueueItem job : queuedJobs) {
            if (job.courseId() == courseId) {
                toRemove.add(job);
            }
        }
        distributedDataAccessService.getDistributedQueuedJobs().removeAll(toRemove);
        updateCancelledQueuedBuildJobsStatus(toRemove);
    }

    /**
     * Cancel all running build jobs for a course.
     *
     * @param courseId id of the course
     */
    public void cancelAllRunningBuildJobsForCourse(long courseId) {
        List<BuildJobQueueItem> runningJobs = distributedDataAccessService.getProcessingJobs();
        for (BuildJobQueueItem buildJob : runningJobs) {
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
        List<BuildJobQueueItem> queuedJobs = distributedDataAccessService.getQueuedJobs();
        for (BuildJobQueueItem queuedJob : queuedJobs) {
            if (queuedJob.participationId() == participationId) {
                toRemove.add(queuedJob);
            }
        }
        distributedDataAccessService.getDistributedQueuedJobs().removeAll(toRemove);
        updateCancelledQueuedBuildJobsStatus(toRemove);

        List<BuildJobQueueItem> runningJobs = distributedDataAccessService.getProcessingJobs();
        for (BuildJobQueueItem runningJob : runningJobs) {
            if (runningJob.participationId() == participationId) {
                cancelBuildJob(runningJob.id());
            }
        }
    }

    /**
     * Clear all build related data from the distributed data structures.
     * This method should only be called by an admin user.
     */
    public void clearDistributedData() {
        distributedDataAccessService.getDistributedQueuedJobs().clear();
        distributedDataAccessService.getDistributedProcessingJobs().clear();
        distributedDataAccessService.getDistributedDockerImageCleanupInfo().clear();
        distributedDataAccessService.getDistributedResultQueue().clear();
        distributedDataAccessService.getDistributedBuildAgentInformation().clear();
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

    /**
     * Estimates how long the job will be queued for on the participation ID.
     *
     * @param participationId the ID of the participation for which the queue release date is estimated
     * @return the estimated queue release date as a {@link ZonedDateTime}
     */
    public ZonedDateTime getBuildJobEstimatedStartDate(long participationId) {
        if (distributedDataAccessService.getDistributedQueuedJobs().isEmpty()
                || this.buildAgentsCapacity > this.runningBuildJobCount + distributedDataAccessService.getQueuedJobsSize()) {
            return ZonedDateTime.now();
        }

        String buildJobId = getIdOfQueuedJobFromParticipation(participationId);

        if (buildJobId == null) {
            return ZonedDateTime.now();
        }

        // Get the jobs queued before the job for the participation
        List<BuildJobQueueItem> jobsQueuedBefore = distributedDataAccessService.getQueuedJobs().stream().sorted(new LocalCIPriorityQueueComparator())
                .takeWhile(job -> !job.id().equals(buildJobId)).toList();

        ZonedDateTime now = ZonedDateTime.now();

        // Get the remaining duration of the build jobs currently being processed
        List<Long> agentsAvailabilities = new ArrayList<>(
                distributedDataAccessService.getProcessingJobs().stream().map(job -> getBuildJobRemainingDuration(job, now)).sorted().toList());

        if (agentsAvailabilities.size() < this.buildAgentsCapacity) {
            int agentsToAdd = this.buildAgentsCapacity - agentsAvailabilities.size();
            agentsAvailabilities.addAll(Collections.nCopies(agentsToAdd, 0L));
        }
        else {
            agentsAvailabilities = agentsAvailabilities.subList(0, this.buildAgentsCapacity);
            log.warn("There are more processing jobs than the build agents' capacity. This should not happen. Processing jobs: {}, Build agents: {}",
                    distributedDataAccessService.getProcessingJobs(), distributedDataAccessService.getBuildAgentInformation());
        }

        if (jobsQueuedBefore.size() < agentsAvailabilities.size()) {
            return now.plusSeconds(agentsAvailabilities.get(jobsQueuedBefore.size()));
        }
        else {
            return now.plusSeconds(calculateNextJobQueueDuration(agentsAvailabilities, jobsQueuedBefore));
        }
    }

    private String getIdOfQueuedJobFromParticipation(long participationId) {
        var participationBuildJobIds = distributedDataAccessService.getQueuedJobs().stream().filter(job -> job.participationId() == participationId).map(BuildJobQueueItem::id)
                .toList();
        if (participationBuildJobIds.isEmpty()) {
            return null;
        }
        return participationBuildJobIds.getLast();
    }

    private Long calculateNextJobQueueDuration(List<Long> agentsAvailabilities, List<BuildJobQueueItem> jobsQueuedBefore) {
        PriorityQueue<Long> agentAvailabilitiesQueue = new PriorityQueue<>(agentsAvailabilities);
        for (BuildJobQueueItem job : jobsQueuedBefore) {
            Long agentRemainingTimeObj = agentAvailabilitiesQueue.poll();
            long agentRemainingTime = agentRemainingTimeObj == null ? 0 : agentRemainingTimeObj;
            agentRemainingTime = Math.max(0, agentRemainingTime);
            agentAvailabilitiesQueue.add(agentRemainingTime + job.jobTimingInfo().estimatedDuration());
        }
        Long agentRemainingTimeObj = agentAvailabilitiesQueue.poll();
        return agentRemainingTimeObj == null ? 0 : agentRemainingTimeObj;
    }

    private long getBuildJobRemainingDuration(BuildJobQueueItem buildJob, ZonedDateTime now) {
        ZonedDateTime estimatedCompletionDate = buildJob.jobTimingInfo().estimatedCompletionDate();
        if (estimatedCompletionDate == null) {
            return 0;
        }
        if (estimatedCompletionDate.isBefore(now)) {
            return 0;
        }
        return Duration.between(now, estimatedCompletionDate).toSeconds();
    }

    class BuildAgentListener
            implements EntryAddedListener<String, BuildAgentInformation>, EntryRemovedListener<String, BuildAgentInformation>, EntryUpdatedListener<String, BuildAgentInformation> {

        @Override
        public void entryAdded(com.hazelcast.core.EntryEvent<String, BuildAgentInformation> event) {
            log.debug("Build agent added: {}", event.getValue());
            updateBuildAgentCapacity();
        }

        @Override
        public void entryRemoved(com.hazelcast.core.EntryEvent<String, BuildAgentInformation> event) {
            log.debug("Build agent removed: {}", event.getOldValue());
            updateBuildAgentCapacity();
        }

        @Override
        public void entryUpdated(com.hazelcast.core.EntryEvent<String, BuildAgentInformation> event) {
            log.debug("Build agent updated: {}", event.getValue());
            updateBuildAgentCapacity();
        }
    }

    private void updateBuildAgentCapacity() {
        buildAgentsCapacity = distributedDataAccessService.getBuildAgentInformation().stream().mapToInt(BuildAgentInformation::maxNumberOfConcurrentBuildJobs).sum();
        runningBuildJobCount = distributedDataAccessService.getBuildAgentInformation().stream().mapToInt(BuildAgentInformation::numberOfCurrentBuildJobs).sum();
    }

    /**
     * Check if a submission is currently being processed.
     *
     * @param participationId the id of the participation
     * @param commitHash      the commit hash
     * @return the build start date and estimated completion date of the submission if it is currently being processed, null otherwise
     */
    public BuildTimingInfo isSubmissionProcessing(long participationId, String commitHash) {
        var buildJob = distributedDataAccessService.getProcessingJobs().stream()
                .filter(job -> job.participationId() == participationId && Objects.equals(commitHash, job.buildConfig().assignmentCommitHash())).findFirst();
        return buildJob
                .map(buildJobQueueItem -> new BuildTimingInfo(buildJobQueueItem.jobTimingInfo().buildStartDate(), buildJobQueueItem.jobTimingInfo().estimatedCompletionDate()))
                .orElse(null);
    }

    public record BuildTimingInfo(ZonedDateTime buildStartDate, ZonedDateTime estimatedCompletionDate) {
    }
}
