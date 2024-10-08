package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.hazelcast.collection.IQueue;
import com.hazelcast.collection.ItemEvent;
import com.hazelcast.collection.ItemListener;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.dto.ResultDTO;
import de.tum.cit.aet.artemis.programming.exception.BuildTriggerWebsocketError;
import de.tum.cit.aet.artemis.programming.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingMessagingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingTriggerService;

@Profile(PROFILE_LOCALCI)
@Service
public class LocalCIResultProcessingService {

    private static final Logger log = LoggerFactory.getLogger(LocalCIResultProcessingService.class);

    private final HazelcastInstance hazelcastInstance;

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final ProgrammingMessagingService programmingMessagingService;

    private final BuildJobRepository buildJobRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ParticipationRepository participationRepository;

    private final ProgrammingTriggerService programmingTriggerService;

    private final BuildLogEntryService buildLogEntryService;

    private IQueue<ResultQueueItem> resultQueue;

    private IMap<String, BuildAgentInformation> buildAgentInformation;

    private UUID listenerId;

    public LocalCIResultProcessingService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, ProgrammingExerciseGradingService programmingExerciseGradingService,
            ProgrammingMessagingService programmingMessagingService, BuildJobRepository buildJobRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            ParticipationRepository participationRepository, ProgrammingTriggerService programmingTriggerService, BuildLogEntryService buildLogEntryService) {
        this.hazelcastInstance = hazelcastInstance;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.participationRepository = participationRepository;
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.programmingMessagingService = programmingMessagingService;
        this.buildJobRepository = buildJobRepository;
        this.programmingTriggerService = programmingTriggerService;
        this.buildLogEntryService = buildLogEntryService;
    }

    /**
     * Initializes the result queue, build agent information map and the locks.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        this.resultQueue = this.hazelcastInstance.getQueue("buildResultQueue");
        this.buildAgentInformation = this.hazelcastInstance.getMap("buildAgentInformation");
        this.listenerId = resultQueue.addItemListener(new ResultQueueListener(), true);
    }

    @PreDestroy
    public void removeListener() {
        // check if Hazelcast is still active, before invoking this
        if (hazelcastInstance != null && hazelcastInstance.getLifecycleService().isRunning()) {
            this.resultQueue.removeItemListener(this.listenerId);
        }
    }

    /**
     * Processes the build job results published by the build agents, notifies the user about the result and saves the result to the database.
     */
    public void processResult() {

        // set lock to prevent multiple nodes from processing the same build job
        ResultQueueItem resultQueueItem = resultQueue.poll();

        if (resultQueueItem == null) {
            return;
        }
        log.info("Processing build job result with id {}", resultQueueItem.buildJobQueueItem().id());
        log.debug("Build jobs waiting in queue: {}", resultQueue.size());
        log.debug("Queued build jobs: {}", resultQueue.stream().map(i -> i.buildJobQueueItem().id()).toList());

        BuildJobQueueItem buildJob = resultQueueItem.buildJobQueueItem();
        BuildResult buildResult = resultQueueItem.buildResult();
        List<BuildLogEntry> buildLogs = resultQueueItem.buildLogs();
        Throwable ex = resultQueueItem.exception();

        BuildJob savedBuildJob;

        SecurityUtils.setAuthorizationObject();
        Optional<Participation> participationOptional = participationRepository.findWithProgrammingExerciseWithBuildConfigById(buildJob.participationId());

        if (buildResult != null) {
            Result result = null;
            try {
                if (participationOptional.isPresent()) {
                    ProgrammingExerciseParticipation participation = (ProgrammingExerciseParticipation) participationOptional.get();

                    // In case the participation does not contain the exercise, we have to load it from the database
                    if (participation.getProgrammingExercise() == null) {
                        participation.setProgrammingExercise(programmingExerciseRepository.getProgrammingExerciseWithBuildConfigFromParticipation(participation));
                    }
                    result = programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, buildResult);

                }
                else {
                    log.warn("Participation with id {} has been deleted. Cancelling the processing of the build result.", buildJob.participationId());
                }
            }
            finally {
                // save build job to database
                if (ex != null) {
                    if (ex.getCause() instanceof CancellationException && ex.getMessage().equals("Build job with id " + buildJob.id() + " was cancelled.")) {
                        savedBuildJob = saveFinishedBuildJob(buildJob, BuildStatus.CANCELLED, result);
                    }
                    else {
                        log.error("Error while processing build job: {}", buildJob, ex);
                        savedBuildJob = saveFinishedBuildJob(buildJob, BuildStatus.FAILED, result);
                    }
                }
                else {
                    savedBuildJob = saveFinishedBuildJob(buildJob, BuildStatus.SUCCESSFUL, result);
                }

                if (participationOptional.isPresent()) {
                    ProgrammingExerciseParticipation participation = (ProgrammingExerciseParticipation) participationOptional.get();
                    if (participation.getExercise() == null) {
                        participation.setExercise(programmingExerciseRepository.getProgrammingExerciseFromParticipation(participation));
                    }

                    if (result != null) {
                        programmingMessagingService.notifyUserAboutNewResult(result, participation);
                        addResultToBuildAgentsRecentBuildJobs(buildJob, result);
                    }
                    else {
                        programmingMessagingService.notifyUserAboutSubmissionError((Participation) participation,
                                new BuildTriggerWebsocketError("Result could not be processed", participation.getId()));
                    }

                    if (!buildLogs.isEmpty()) {
                        if (savedBuildJob != null) {
                            buildLogEntryService.saveBuildLogsToFile(buildLogs, savedBuildJob.getBuildJobId(), participation.getProgrammingExercise());
                        }
                        else {
                            log.warn("Couldn't save build logs as build job {} was not saved", buildJob.id());
                        }
                    }
                }
            }

            // If the build job is a solution build of a test or auxiliary push, we need to trigger the build of the corresponding template repository
            if (isSolutionBuildOfTestOrAuxPush(buildJob)) {
                log.debug("Triggering build of template repository for solution build with id {}", buildJob.id());
                try {
                    programmingTriggerService.triggerTemplateBuildAndNotifyUser(buildJob.exerciseId(), buildJob.buildConfig().testCommitHash(), SubmissionType.TEST,
                            buildJob.repositoryInfo().triggeredByPushTo());
                }
                catch (EntityNotFoundException e) {
                    // Something went wrong while retrieving the template participation.
                    // At this point, programmingMessagingService.notifyUserAboutSubmissionError() does not work, because the template participation is not available.
                    // The instructor will see in the UI that no build of the template repository was conducted and will receive an error message when triggering the build
                    // manually.
                    log.error("Something went wrong while triggering the template build for exercise {} after the solution build was finished.", buildJob.exerciseId(), e);
                }
            }
        }
    }

    /**
     * Adds the given result to the recent build jobs of the build agent that processed the build job.
     *
     * @param buildJob the build job
     * @param result   the result of the build job
     */
    private void addResultToBuildAgentsRecentBuildJobs(BuildJobQueueItem buildJob, Result result) {
        try {
            buildAgentInformation.lock(buildJob.buildAgentAddress());
            BuildAgentInformation buildAgent = buildAgentInformation.get(buildJob.buildAgentAddress());
            if (buildAgent != null) {
                List<BuildJobQueueItem> recentBuildJobs = buildAgent.recentBuildJobs();
                for (int i = 0; i < recentBuildJobs.size(); i++) {
                    if (recentBuildJobs.get(i).id().equals(buildJob.id())) {
                        recentBuildJobs.set(i, new BuildJobQueueItem(buildJob, ResultDTO.of(result)));
                        break;
                    }
                }
                buildAgentInformation.put(buildJob.buildAgentAddress(), new BuildAgentInformation(buildAgent, recentBuildJobs));
            }
        }
        finally {
            buildAgentInformation.unlock(buildJob.buildAgentAddress());
        }

    }

    /**
     * Save a finished build job to the database.
     *
     * @param queueItem   the build job object from the queue
     * @param buildStatus the status of the build job (SUCCESSFUL, FAILED, CANCELLED)
     * @param result      the submission result
     *
     * @return the saved the build job
     */
    public BuildJob saveFinishedBuildJob(BuildJobQueueItem queueItem, BuildStatus buildStatus, Result result) {
        try {
            BuildJob buildJob = new BuildJob(queueItem, buildStatus, result);
            return buildJobRepository.save(buildJob);
        }
        catch (Exception e) {
            log.error("Could not save build job to database", e);
            return null;
        }
    }

    public class ResultQueueListener implements ItemListener<ResultQueueItem> {

        @Override
        public void itemAdded(ItemEvent<ResultQueueItem> event) {
            log.debug("Result of build job with id {} added to queue", event.getItem().buildJobQueueItem().id());
            processResult();
        }

        @Override
        public void itemRemoved(ItemEvent<ResultQueueItem> event) {

        }
    }

    /**
     * Checks if the given build job is a solution build of a test or auxiliary push.
     *
     * @param buildJob the build job to check
     * @return true if the build job is a solution build of a test or auxiliary push, false otherwise
     */
    private boolean isSolutionBuildOfTestOrAuxPush(BuildJobQueueItem buildJob) {
        return buildJob.repositoryInfo().repositoryType() == RepositoryType.SOLUTION
                && (buildJob.repositoryInfo().triggeredByPushTo() == RepositoryType.TESTS || buildJob.repositoryInfo().triggeredByPushTo() == RepositoryType.AUXILIARY);
    }
}
