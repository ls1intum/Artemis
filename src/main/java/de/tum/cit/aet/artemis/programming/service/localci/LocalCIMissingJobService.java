package de.tum.cit.aet.artemis.programming.service.localci;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.repository.BuildJobRepository;

/**
 * Schedule service for detecting and retrying missing build jobs in the LocalCI system
 */
@Lazy
@Service
@Profile("localci & scheduling")
public class LocalCIMissingJobService {

    private static final Logger log = LoggerFactory.getLogger(LocalCIMissingJobService.class);

    private final BuildJobRepository buildJobRepository;

    private final LocalCITriggerService localCITriggerService;

    private final ParticipationRepository participationRepository;

    private final DistributedDataAccessService distributedDataAccessService;

    @Value("${artemis.continuous-integration.max-missing-job-retries:3}")
    private int maxMissingJobRetries;

    public LocalCIMissingJobService(BuildJobRepository buildJobRepository, LocalCITriggerService localCITriggerService, ParticipationRepository participationRepository,
            DistributedDataAccessService distributedDataAccessService) {
        this.buildJobRepository = buildJobRepository;
        this.localCITriggerService = localCITriggerService;
        this.participationRepository = participationRepository;
        this.distributedDataAccessService = distributedDataAccessService;
    }

    /**
     * Periodically checks the status of pending build jobs and updates their status if they are missing.
     * <p>
     * This scheduled task ensures that build jobs which are stuck in the QUEUED or BUILDING state for too long
     * are detected and marked as MISSING if their status cannot be verified. This helps prevent indefinite
     * waiting states due to external failures or inconsistencies in the CI system.
     * </p>
     * <p>
     * This mechanism is necessary because build jobs are managed externally, and various failure scenarios
     * can lead to jobs being lost without Artemis being notified:
     * </p>
     * <ul>
     * <li>Application crashes or restarts while build job was queued</li>
     * <li>network issues leading to Hazelcast data loss</li>
     * <li>Build agent crashes or is disconnected</li>
     * </ul>
     */
    @Scheduled(fixedRateString = "${artemis.continuous-integration.check-job-status-interval-seconds:300}", initialDelayString = "${artemis.continuous-integration.check-job-status-delay-seconds:60}", timeUnit = TimeUnit.SECONDS)
    public void checkPendingBuildJobsStatus() {
        log.debug("Checking pending build jobs status");
        List<BuildJob> pendingBuildJobs = buildJobRepository.findAllByBuildStatusIn(List.of(BuildStatus.QUEUED, BuildStatus.BUILDING));
        ZonedDateTime now = ZonedDateTime.now();
        final int buildJobExpirationInMinutes = 5; // If a build job is older than 5 minutes, and it's status can't be determined, set it to missing

        var queuedJobs = distributedDataAccessService.getQueuedJobs();
        var processingJobs = distributedDataAccessService.getProcessingJobIds();

        for (BuildJob buildJob : pendingBuildJobs) {
            var submissionDate = buildJob.getBuildSubmissionDate();
            if (submissionDate == null || submissionDate.isAfter(now.minusMinutes(buildJobExpirationInMinutes))) {
                log.debug("Build job with id {} is too recent to check", buildJob.getBuildJobId());
                continue;
            }
            if (buildJob.getBuildStatus() == BuildStatus.QUEUED && checkIfBuildJobIsStillQueued(queuedJobs, buildJob.getBuildJobId())) {
                log.debug("Build job with id {} is still queued", buildJob.getBuildJobId());
                continue;
            }
            if (checkIfBuildJobIsStillBuilding(processingJobs, buildJob.getBuildJobId())) {
                log.debug("Build job with id {} is still building", buildJob.getBuildJobId());
                continue;
            }
            log.error("Build job with id {} is in an unknown state", buildJob.getBuildJobId());
            // If the build job is in an unknown state, set it to missing and update the build start date
            buildJobRepository.updateBuildJobStatus(buildJob.getBuildJobId(), BuildStatus.MISSING);
        }
    }

    /**
     * Periodically retries missing build jobs.
     * R
     * retrieves a slice of missing build jobs from the last hour and attempts to retry them.
     * If a build job has reached the maximum number of retries, it will not be retried again.
     */
    @Scheduled(fixedRateString = "${artemis.continuous-integration.retry-missing-jobs-interval-seconds:300}", initialDelayString = "${artemis.continuous-integration.retry-missing-jobs-delay-seconds:120}", timeUnit = TimeUnit.SECONDS)
    public void retryMissingJobs() {
        log.debug("Checking for missing build jobs to retry");

        Slice<BuildJob> missingJobsSlice = getMissingJobsToRetrySliceOfLastHour(50);
        List<BuildJob> missingJobs = missingJobsSlice.getContent();
        log.debug("Processing {} missing build jobs to retry", missingJobs.size());

        for (BuildJob buildJob : missingJobs) {
            if (buildJob.getRetryCount() >= maxMissingJobRetries) {
                log.warn("Build job with id {} for participation {} has reached the maximum number of {} retries and will not be retried.", buildJob.getBuildJobId(),
                        buildJob.getParticipationId(), maxMissingJobRetries);
                continue;
            }
            try {
                localCITriggerService.retryBuildJob(buildJob, (ProgrammingExerciseParticipation) participationRepository.findByIdElseThrow(buildJob.getParticipationId()));
                buildJobRepository.incrementRetryCount(buildJob.getBuildJobId());
            }
            catch (Exception e) {
                log.error("Failed to retry build job with id {} for participation {}", buildJob.getBuildJobId(), buildJob.getParticipationId(), e);
            }
        }

        if (missingJobsSlice.hasNext()) {
            log.debug("There are more missing jobs to process in the next scheduled run.");
        }
    }

    private boolean checkIfBuildJobIsStillBuilding(List<String> processingJobIds, String buildJobId) {
        return processingJobIds.contains(buildJobId);
    }

    private boolean checkIfBuildJobIsStillQueued(List<BuildJobQueueItem> queuedJobs, String buildJobId) {
        return queuedJobs.stream().anyMatch(job -> job.id().equals(buildJobId));
    }

    /**
     * Retrieves a slice of missing build jobs submitted within the last hour that do not have a newer job for the same participation.
     *
     * @param maxResults the maximum number of results to retrieve
     * @return a slice of missing build jobs
     */
    private Slice<BuildJob> getMissingJobsToRetrySliceOfLastHour(int maxResults) {
        Pageable pageable = PageRequest.of(0, maxResults);
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime oneHourAgo = now.minusHours(1);
        return buildJobRepository.findMissingJobsToRetryInTimeRange(oneHourAgo, now, pageable);
    }
}
