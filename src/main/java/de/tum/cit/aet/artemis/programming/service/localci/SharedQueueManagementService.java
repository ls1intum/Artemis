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

import jakarta.annotation.PostConstruct;

import org.redisson.api.RMap;
import org.redisson.api.RPriorityQueue;
import org.redisson.api.RQueue;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MapPutListener;
import org.redisson.api.listener.MapRemoveListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.DockerImageBuild;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;
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

    private final RedissonClient redissonClient;

    private final ProfileService profileService;

    private RPriorityQueue<BuildJobQueueItem> buildJobQueue;

    private RQueue<ResultQueueItem> resultQueue;

    /**
     * Map of build jobs currently being processed across all nodes
     */
    private RMap<String, BuildJobQueueItem> processingJobs;

    private RMap<String, BuildAgentInformation> buildAgentInformation;

    private RMap<String, ZonedDateTime> dockerImageCleanupInfo;

    private RTopic canceledBuildJobsTopic;

    private RTopic pauseBuildAgentTopic;

    private RTopic resumeBuildAgentTopic;

    private int buildAgentsCapacity;

    private int runningBuildJobCount;

    public SharedQueueManagementService(BuildJobRepository buildJobRepository, RedissonClient redissonClient, ProfileService profileService) {
        this.buildJobRepository = buildJobRepository;
        this.redissonClient = redissonClient;
        this.profileService = profileService;
    }

    /**
     * Initialize relevant data from hazelcast
     */
    @PostConstruct
    public void init() {
        this.buildAgentInformation = this.redissonClient.getMap("buildAgentInformation");
        // TODO check if can simplify adding those listeners in general
        this.buildAgentInformation.addListener((MapPutListener) item -> {
            updateBuildAgentCapacity();
        });

        this.buildAgentInformation.addListener((MapRemoveListener) item -> {
            updateBuildAgentCapacity();
        });

        this.processingJobs = this.redissonClient.getMap("processingJobs");
        this.buildJobQueue = this.redissonClient.getPriorityQueue("buildJobQueue");
        this.buildJobQueue.trySetComparator(new LocalCIPriorityQueueComparator());
        this.canceledBuildJobsTopic = this.redissonClient.getTopic("canceledBuildJobsTopic");
        this.pauseBuildAgentTopic = this.redissonClient.getTopic("pauseBuildAgentTopic");
        this.resumeBuildAgentTopic = this.redissonClient.getTopic("resumeBuildAgentTopic");
        this.dockerImageCleanupInfo = this.redissonClient.getMap("dockerImageCleanupInfo");
        this.updateBuildAgentCapacity();
        this.resultQueue = this.redissonClient.getQueue("buildResultQueue");

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
        return new ArrayList<>(buildJobQueue);
    }

    public int getQueuedJobsSize() {
        return buildJobQueue.size();
    }

    public List<BuildJobQueueItem> getProcessingJobs() {
        return processingJobs.values().stream().toList();
    }

    /**
     * @return a list of processing job ids
     */
    public List<String> getProcessingJobIds() {
        // NOTE: we should not use streams with IMap, because it can be unstable, when many items are added at the same time and there is a slow network condition
        return new ArrayList<>(processingJobs.keySet());
    }

    public int getProcessingJobsSize() {
        return processingJobs.size();
    }

    public List<BuildJobQueueItem> getQueuedJobsForCourse(long courseId) {
        return buildJobQueue.stream().filter(job -> job.courseId() == courseId).toList();
    }

    public List<BuildJobQueueItem> getProcessingJobsForCourse(long courseId) {
        return getProcessingJobs().stream().filter(job -> job.courseId() == courseId).toList();
    }

    public List<BuildJobQueueItem> getQueuedJobsForParticipation(long participationId) {
        return getQueuedJobs().stream().filter(job -> job.participationId() == participationId).toList();
    }

    public List<BuildJobQueueItem> getProcessingJobsForParticipation(long participationId) {
        return getProcessingJobs().stream().filter(job -> job.participationId() == participationId).toList();
    }

    public List<BuildAgentInformation> getBuildAgentInformation() {
        // NOTE: we should not use streams with IMap, because it can be unstable, when many items are added at the same time and there is a slow network condition
        return new ArrayList<>(buildAgentInformation.values());
    }

    public int getBuildAgentInformationSize() {
        return buildAgentInformation.size();
    }

    public List<BuildAgentInformation> getBuildAgentInformationWithoutRecentBuildJobs() {
        return getBuildAgentInformation().stream().map(agent -> new BuildAgentInformation(agent.buildAgent(), agent.maxNumberOfConcurrentBuildJobs(),
                agent.numberOfCurrentBuildJobs(), agent.runningBuildJobs(), agent.status(), null, null)).toList();
    }

    public void pauseBuildAgent(String agent) {
        pauseBuildAgentTopic.publish(agent);
    }

    public void pauseAllBuildAgents() {
        getBuildAgentInformation().forEach(agent -> pauseBuildAgent(agent.buildAgent().name()));
    }

    public void resumeBuildAgent(String agent) {
        resumeBuildAgentTopic.publish(agent);
    }

    public void resumeAllBuildAgents() {
        getBuildAgentInformation().forEach(agent -> resumeBuildAgent(agent.buildAgent().name()));
    }

    /**
     * Cancel a build job by removing it from the queue or stopping the build process.
     *
     * @param buildJobId id of the build job to cancel
     */
    public void cancelBuildJob(String buildJobId) {
        // Remove build job if it is queued
        List<BuildJobQueueItem> queuedJobs = getQueuedJobs();
        if (queuedJobs.stream().anyMatch(job -> Objects.equals(job.id(), buildJobId))) {
            List<BuildJobQueueItem> toRemove = new ArrayList<>();
            for (BuildJobQueueItem job : queuedJobs) {
                if (Objects.equals(job.id(), buildJobId)) {
                    toRemove.add(job);
                }
            }
            buildJobQueue.removeAll(toRemove);
            updateCancelledQueuedBuildJobsStatus(toRemove);
        }
        else {
            // Cancel build job if it is currently being processed
            BuildJobQueueItem buildJob = processingJobs.remove(buildJobId);
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
        canceledBuildJobsTopic.publish(buildJobId);
    }

    /**
     * Cancel all queued build jobs.
     */
    public void cancelAllQueuedBuildJobs() {
        log.debug("Cancelling all queued build jobs");
        List<BuildJobQueueItem> queuedJobs = getQueuedJobs();
        buildJobQueue.clear();
        updateCancelledQueuedBuildJobsStatus(queuedJobs);
    }

    /**
     * Cancel all running build jobs.
     */
    public void cancelAllRunningBuildJobs() {
        List<BuildJobQueueItem> runningJobs = getProcessingJobs();
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
        // TODO: implement better filtering based on predicates to avoid retrieving all values
        getProcessingJobs().stream().filter(job -> Objects.equals(job.buildAgent().name(), agentName)).forEach(job -> cancelBuildJob(job.id()));
    }

    /**
     * Cancel all queued build jobs for a course.
     *
     * @param courseId id of the course
     */
    public void cancelAllQueuedBuildJobsForCourse(long courseId) {
        // TODO: implement better searching based on predicates to avoid retrieving all values
        List<BuildJobQueueItem> queuedJobs = getQueuedJobs();
        List<BuildJobQueueItem> toRemove = new ArrayList<>();
        for (BuildJobQueueItem job : queuedJobs) {
            if (job.courseId() == courseId) {
                toRemove.add(job);
            }
        }
        buildJobQueue.removeAll(toRemove);
        updateCancelledQueuedBuildJobsStatus(toRemove);
    }

    /**
     * Cancel all running build jobs for a course.
     *
     * @param courseId id of the course
     */
    public void cancelAllRunningBuildJobsForCourse(long courseId) {
        List<BuildJobQueueItem> runningJobs = getProcessingJobs();
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
        // TODO: implement better searching based on predicates to avoid retrieving all values
        List<BuildJobQueueItem> toRemove = new ArrayList<>();
        List<BuildJobQueueItem> queuedJobs = getQueuedJobs();
        for (BuildJobQueueItem queuedJob : queuedJobs) {
            if (queuedJob.participationId() == participationId) {
                toRemove.add(queuedJob);
            }
        }
        buildJobQueue.removeAll(toRemove);
        updateCancelledQueuedBuildJobsStatus(toRemove);

        List<BuildJobQueueItem> runningJobs = getProcessingJobs();
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
        buildJobQueue.clear();
        processingJobs.clear();
        dockerImageCleanupInfo.clear();
        resultQueue.clear();
        buildAgentInformation.clear();
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
        if (buildJobQueue.isEmpty() || this.buildAgentsCapacity > this.runningBuildJobCount + buildJobQueue.size()) {
            return ZonedDateTime.now();
        }

        String buildJobId = getIdOfQueuedJobFromParticipation(participationId);

        if (buildJobId == null) {
            return ZonedDateTime.now();
        }

        // Get the jobs queued before the job for the participation
        List<BuildJobQueueItem> jobsQueuedBefore = getQueuedJobs().stream().sorted(new LocalCIPriorityQueueComparator()).takeWhile(job -> !job.id().equals(buildJobId)).toList();

        ZonedDateTime now = ZonedDateTime.now();

        // Get the remaining duration of the build jobs currently being processed
        List<Long> agentsAvailabilities = new ArrayList<>(getProcessingJobs().stream().map(job -> getBuildJobRemainingDuration(job, now)).sorted().toList());

        if (agentsAvailabilities.size() < this.buildAgentsCapacity) {
            int agentsToAdd = this.buildAgentsCapacity - agentsAvailabilities.size();
            agentsAvailabilities.addAll(Collections.nCopies(agentsToAdd, 0L));
        }
        else {
            agentsAvailabilities = agentsAvailabilities.subList(0, this.buildAgentsCapacity);
            log.warn("There are more processing jobs than the build agents' capacity. This should not happen. Processing jobs: {}, Build agents: {}", processingJobs,
                    buildAgentInformation);
        }

        if (jobsQueuedBefore.size() < agentsAvailabilities.size()) {
            return now.plusSeconds(agentsAvailabilities.get(jobsQueuedBefore.size()));
        }
        else {
            return now.plusSeconds(calculateNextJobQueueDuration(agentsAvailabilities, jobsQueuedBefore));
        }
    }

    private String getIdOfQueuedJobFromParticipation(long participationId) {
        var participationBuildJobIds = getQueuedJobs().stream().filter(job -> job.participationId() == participationId).map(BuildJobQueueItem::id).toList();
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

    private void updateBuildAgentCapacity() {
        buildAgentsCapacity = getBuildAgentInformation().stream().mapToInt(BuildAgentInformation::maxNumberOfConcurrentBuildJobs).sum();
        runningBuildJobCount = getBuildAgentInformation().stream().mapToInt(BuildAgentInformation::numberOfCurrentBuildJobs).sum();
    }

    /**
     * Check if a submission is currently being processed.
     *
     * @param participationId the id of the participation
     * @param commitHash      the commit hash
     * @return the build start date and estimated completion date of the submission if it is currently being processed, null otherwise
     */
    public BuildTimingInfo isSubmissionProcessing(long participationId, String commitHash) {
        var buildJob = getProcessingJobs().stream().filter(job -> job.participationId() == participationId && Objects.equals(commitHash, job.buildConfig().assignmentCommitHash()))
                .findFirst();
        return buildJob
                .map(buildJobQueueItem -> new BuildTimingInfo(buildJobQueueItem.jobTimingInfo().buildStartDate(), buildJobQueueItem.jobTimingInfo().estimatedCompletionDate()))
                .orElse(null);
    }

    public record BuildTimingInfo(ZonedDateTime buildStartDate, ZonedDateTime estimatedCompletionDate) {
    }
}
